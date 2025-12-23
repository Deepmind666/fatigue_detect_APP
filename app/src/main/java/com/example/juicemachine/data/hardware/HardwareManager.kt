package com.example.juicemachine.data.hardware

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.example.juicemachine.data.database.CupConfig
import com.example.juicemachine.data.database.Recipe
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.IOException
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.annotation.SuppressLint
    // 新增：USB权限与设备广播
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.example.juicemachine.util.DebugLogger
import kotlin.math.abs

// 重量异常数据类
/**
 * 模块：硬件通信管理（USB-Serial）
 * 作用：
 * - 负责 USB 权限、设备发现、串口打开/关闭与 IO 线程管理；
 * - 以 7 字节协议（FF CMD P0 P1 P2 P3 FE）与下位机通讯；
 * - 解析重量异常、订单成功/失败等上行事件并回调给上层（UI/ViewModel）。
 * 健壮性：
 * - 写入加锁（writeLock）与资源集合加锁（portLock）；
 * - 设备插拔/权限授权的系统广播监听；
 * - 异常后指数回退的自动重连（scheduleReconnect）。
 */
 data class WeightAnomalyData(
    val currentWeight: Int,
    val expectedWeight: Int,
    val severity: WeightAnomalySeverity,
    val timestamp: Long
)

/**
 * 下位机重量异常的严重程度枚举。
 * 对应协议中的 severity 字节：1=LOW，2=MEDIUM，3或4=HIGH。
 */
enum class WeightAnomalySeverity {
    LOW, MEDIUM, HIGH
}

/**
 * 硬件通信管理器（USB-Serial）。
 * 职责：
 * - 发现并连接USB串口设备，管理IO线程与串口生命周期；
 * - 以 7 字节协议（FF CMD P0 P1 P2 P3 FE）与下位机通讯；
 * - 解析上行事件（重量异常、订单完成/失败）并回调上层；
 * - 处理USB权限授权、设备插拔广播与自动重连。
 * 线程安全：
 * - 写入使用 writeLock 加锁；资源集合（openedPorts/ioManagers）使用 portLock 加锁；
 * - 状态通过 isConnected 字段对外暴露（只读）。
 */
class HardwareManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : SerialInputOutputManager.Listener {

    /** USB权限请求广播Action，用于在授权回调中继续连接流程 */
    companion object {
        const val USB_PERMISSION_ACTION: String = "com.example.juicemachine.USB_PERMISSION"
    }
    // 系统USB服务入口
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    // 当前使用的单一串口（保持与旧版一致，仅选第一个端口）
    private var serialPort: UsbSerialPort? = null
    // 与当前端口绑定的IO管理器（库负责异步读写回调）
    private var usbIoManager: SerialInputOutputManager? = null
    // 写入锁，避免多个调用同时写串口
    private val writeLock = Any()
    // 自动重连控制
    private var reconnectJob: Job? = null
    private var reconnectAttempts: Int = 0
    // 对外连接状态（只读），由连接/断开/错误路径维护
    var isConnected: Boolean = false
        private set
    // 协议模式开关：false=旧规(无边界，首字节为CMD)，true=新规(带FF/FE边界)
    private var useFramedProtocol: Boolean = true
    fun setProtocolFramed(enabled: Boolean) {
        useFramedProtocol = enabled
        DebugLogger.i("HardwareManager", "协议模式切换: framed=$useFramedProtocol")
    }
    // 上层状态提示/异常/订单完成回调
    private var onStatusListener: ((String) -> Unit)? = null
    private var onWeightAnomalyListener: ((WeightAnomalyData) -> Unit)? = null
    private var onOrderCompletionListener: ((Boolean) -> Unit)? = null // true=成功，false=失败
    // 新增：温度/重量数值上报与中性完成事件（用于广告倒计时重置）
    private var onTemperatureReportListener: ((Int) -> Unit)? = null
    private var onWeightReportListener: ((Int) -> Unit)? = null
    private var onNeutralCompletionListener: (() -> Unit)? = null

    // 采样打印串口接收日志，避免冷启动阶段日志过多影响性能
    private var lastRxLogMillis: Long = 0L

    // 连续字节流粘包/分包缓冲，确保解析到以0xFF开头、0xFE结尾的完整帧
    private val frameBuffer = ArrayList<Byte>(256)
    private val maxBufferSize = 2048

    // USB权限与设备插拔广播
    private var usbReceiver: BroadcastReceiver? = null
    private var usbReceiverRegistered: Boolean = false

    private var lastPrimedWaterTopSpeed: Int? = null

    private val adminFrame11Local = ThreadLocal.withInitial { ByteArray(11) }
    private val adminPayload9Local = ThreadLocal.withInitial { ByteArray(9) }

    private fun indexOfByte(buf: List<Byte>, value: Byte, start: Int = 0): Int {
        for (i in start until buf.size) if (buf[i] == value) return i
        return -1
    }

    /**
     * 从 frameBuffer 中尽可能解析完整帧并分发处理。
     * 优先解析 11.8 改版的 7 字节：FF CMD P0 P1 P2 P3 FE。
     * 映射（与设备侧文档一致）：
     * - 异常/温度/重量：CMD=0x09，按数据位判别
     *   - 温度：P0=温度（有符号8位），P1/P2/P3=0；
     *   - 重量：P0=lo，P1=hi，P2/P3=0；
     *   - 异常：P0/P1/P2/P3均为0；
     * - 完成：CMD=0x03，P0/P1/P2/P3均为0（中性完成）
     * 兼容保留：温度 CMD=0x0A（P0=temp），重量 CMD=0x0C（P0=lo，P1=hi）；以及旧完成状态字 P0=0xAA/0xAC。
     */
    private fun tryParseFramesFromBuffer() {
        while (true) {
            // 查找起始标记
            val startIdx = indexOfByte(frameBuffer, 0xFF.toByte())
            if (startIdx == -1) return // 没有起始标记，等待更多数据
            // 丢弃起始标记之前的无效数据
            if (startIdx > 0) {
                frameBuffer.subList(0, startIdx).clear()
            }
            // 查找结束标记
            val endIdx = indexOfByte(frameBuffer, 0xFE.toByte(), 1)
            if (endIdx == -1) return // 等待更多数据

            val frameLen = endIdx + 1
            val frame = ByteArray(frameLen)
            for (i in 0 until frameLen) frame[i] = frameBuffer[i]

            // 移除已消费字节
            frameBuffer.subList(0, frameLen).clear()

            // 仅处理 7 字节协议帧（后端→前端）
            if (frameLen >= 7 && frame[0] == 0xFF.toByte() && frame[frameLen - 1] == 0xFE.toByte()) {
                val cmd = frame[1].toInt() and 0xFF
                val p0 = frame[2].toInt() and 0xFF
                val p1 = frame[3].toInt() and 0xFF
                val p2 = frame[4].toInt() and 0xFF
                val p3 = frame[5].toInt() and 0xFF

                if (cmd == 0x09) {
                    // 11.8 改版：0x09 统一用于温度/重量/异常三类事件，按数据位判别
                    val allZero = (p0 == 0 && p1 == 0 && p2 == 0 && p3 == 0)
                    val onlyP0 = (p0 != 0 && p1 == 0 && p2 == 0 && p3 == 0)
                    val p0p1 = (p0 != 0 && p1 != 0 && p2 == 0 && p3 == 0)
                    when {
                        // 温度：FF 09 [temp] 00 00 00 FE（temp 有符号8位）
                        onlyP0 -> {
                            val raw = p0
                            val temp = if (raw >= 0x80) raw - 0x100 else raw
                            if (DebugLogger.isVerboseEnabled()) {
                                val hex = frame.joinToString(" ") { "%02X".format(it) }
                                Log.d("HardwareManager", "解析到温度上报(0x09): ${temp}°C, frame=$hex")
                            } else {
                                Log.d("HardwareManager", "解析到温度上报(0x09): ${temp}°C")
                            }
                            scope.launch(Dispatchers.Main.immediate) { onTemperatureReportListener?.invoke(temp) }
                        }
                        // 重量：FF 09 [hi] [lo] 00 00 FE（按“01 90”版本，高位在前）
                        p0p1 -> {
                            val weight = ((p0 and 0xFF) shl 8) or (p1 and 0xFF)
                            if (DebugLogger.isVerboseEnabled()) {
                                val hex = frame.joinToString(" ") { "%02X".format(it) }
                                Log.d("HardwareManager", "解析到重量上报(0x09 BE): ${weight}g, frame=$hex")
                            } else {
                                Log.d("HardwareManager", "解析到重量上报(0x09 BE): ${weight}g")
                            }
                            scope.launch(Dispatchers.Main.immediate) { onWeightReportListener?.invoke(weight) }
                        }
                        // 异常：FF 09 00 00 00 00 FE（不含期望重量与等级，按中等级处理）
                        allZero -> {
                            val data = WeightAnomalyData(
                                currentWeight = 0,
                                expectedWeight = 0,
                                severity = WeightAnomalySeverity.MEDIUM,
                                timestamp = System.currentTimeMillis()
                            )
                            Log.w(
                                "HardwareManager",
                                if (DebugLogger.isVerboseEnabled()) {
                                    "收到异常事件(0x09 all-zero): frame=" + frame.joinToString(" ") { "%02X".format(it) }
                                } else {
                                    "收到异常事件(0x09 all-zero)"
                                }
                            )
                            scope.launch(Dispatchers.Main.immediate) { onWeightAnomalyListener?.invoke(data) }
                        }
                        // 兼容：旧解析（severity + expectedWeight lo/hi）
                        else -> {
                            val severity = when (p0) {
                                1 -> WeightAnomalySeverity.LOW
                                2 -> WeightAnomalySeverity.MEDIUM
                                3 -> WeightAnomalySeverity.HIGH
                                else -> WeightAnomalySeverity.MEDIUM
                            }
                            val expected = (p2 shl 8) or p1
                            val data = WeightAnomalyData(
                                currentWeight = 0,
                                expectedWeight = expected,
                                severity = severity,
                                timestamp = System.currentTimeMillis()
                            )
                            Log.w(
                                "HardwareManager",
                                if (DebugLogger.isVerboseEnabled()) {
                                    "收到重量异常(兼容0x09): severity=$p0 expected=${expected}g frame=" + frame.joinToString(" ") { "%02X".format(it) }
                                } else {
                                    "收到重量异常(兼容0x09): severity=$p0 expected=${expected}g"
                                }
                            )
                            scope.launch(Dispatchers.Main.immediate) { onWeightAnomalyListener?.invoke(data) }
                        }
                    }
                } else if (cmd == 0x01) {
                    // 新规：温度上报 CMD=0x01，P0为温度，其余应为0
                    val raw = p0
                    val temp = if (raw >= 0x80) raw - 0x100 else raw
                    val highZerosOk = (p1 == 0 && p2 == 0 && p3 == 0)
                    if (DebugLogger.isVerboseEnabled()) {
                        val hex = frame.joinToString(" ") { "%02X".format(it) }
                        Log.d("HardwareManager", "解析到温度上报(0x01): ${temp}°C, highZerosOk=$highZerosOk, frame=$hex")
                    } else {
                        Log.d("HardwareManager", "解析到温度上报(0x01): ${temp}°C, highZerosOk=$highZerosOk")
                    }
                    scope.launch(Dispatchers.Main.immediate) { onTemperatureReportListener?.invoke(temp) }
                } else if (cmd == 0x02) {
                    // 新规：重量上报 CMD=0x02，按“01 90”版本固定为高位在前（BE）：weight=(P0<<8)|P1
                    val weight = ((p0 and 0xFF) shl 8) or (p1 and 0xFF)
                    val highZerosOk = (p2 == 0 && p3 == 0)
                    if (DebugLogger.isVerboseEnabled()) {
                        val hex = frame.joinToString(" ") { "%02X".format(it) }
                        Log.d("HardwareManager", "解析到重量上报(0x02): ${weight}g, highZerosOk=$highZerosOk, frame=$hex")
                    } else {
                        Log.d("HardwareManager", "解析到重量上报(0x02): ${weight}g, highZerosOk=$highZerosOk")
                    }
                    scope.launch(Dispatchers.Main.immediate) { onWeightReportListener?.invoke(weight) }
                } else if (cmd == 0x03) {
                    // 11.8 改版：完成帧 FF 03 00 00 00 00 FE（中性完成）
                    val allZero = (p0 == 0 && p1 == 0 && p2 == 0 && p3 == 0)
                    when {
                        allZero -> {
                            val hex = frame.joinToString(" ") { "%02X".format(it) }
                            Log.i("HardwareManager", "订单完成(7B all-zero): 完成 frame=$hex")
                            DebugLogger.i("HardwareManager", "订单完成(7B all-zero)", showToast = false)
                            scope.launch(Dispatchers.Main.immediate) {
                                onOrderCompletionListener?.invoke(true)
                                onNeutralCompletionListener?.invoke()
                            }
                        }
                        // 兼容旧版：P0=0xAA/0xAC 表示成功/失败
                        p0 == 0xAA -> {
                            Log.i("HardwareManager", "订单完成(7B cmd=0x03): 成功")
                            DebugLogger.i("HardwareManager", "订单完成(7B cmd=0x03)", showToast = false)
                            scope.launch(Dispatchers.Main.immediate) { onOrderCompletionListener?.invoke(true) }
                        }
                        p0 == 0xAC -> {
                            Log.w("HardwareManager", "订单完成(7B cmd=0x03): 失败")
                            DebugLogger.w("HardwareManager", "订单失败(7B cmd=0x03)", showToast = false)
                            scope.launch(Dispatchers.Main.immediate) { onOrderCompletionListener?.invoke(false) }
                        }
                        else -> {
                            val hex = frame.joinToString(" ") { "%02X".format(it) }
                            Log.d("HardwareManager", "解析到未识别的完成帧: $hex")
                        }
                    }
                } else {
                    // 订单完成/失败：P0=0xAA/0xAC
                    when (p0) {
                        0xAA -> {
                            Log.i("HardwareManager", "订单完成(7B cmd=0x%02X): 成功".format(cmd))
                            DebugLogger.i("HardwareManager", "订单完成(7B cmd=0x%02X)".format(cmd), showToast = false)
                            scope.launch(Dispatchers.Main.immediate) { onOrderCompletionListener?.invoke(true) }
                        }
                        0xAC -> {
                            Log.i("HardwareManager", "订单完成(7B cmd=0x%02X): 失败".format(cmd))
                            DebugLogger.w("HardwareManager", "订单失败(7B cmd=0x%02X)".format(cmd), showToast = false)
                            scope.launch(Dispatchers.Main.immediate) { onOrderCompletionListener?.invoke(false) }
                        }
                        else -> {
                            val hex = frame.joinToString(" ") { "%02X".format(it) }
                            Log.d("HardwareManager", "解析到未识别或保留的7B帧: $hex")
                        }
                    }
                }
            } else {
                // 其他帧暂不处理
                val hex = frame.joinToString(" ") { "%02X".format(it) }
                Log.d("HardwareManager", "解析到非目标帧: $hex")
            }
        }
    }

    override fun onNewData(data: ByteArray) {
        if (data.isNotEmpty()) {
            val now = System.currentTimeMillis()
            if (DebugLogger.isVerboseEnabled()) {
                if (now - lastRxLogMillis >= 1000L) {
                    val hexString = data.joinToString(separator = " ") { "%02X".format(it) }
                    Log.d("HardwareManager", "接收到数据(采样): $hexString")
                    DebugLogger.i("HardwareManager", "接收到数据(采样): $hexString")
                    lastRxLogMillis = now
                }
            }
            // 仅解析 7 字节帧（FF ... FE）；不再解析旧规 5 字节 FD/FC
            for (b in data) frameBuffer.add(b)
            if (frameBuffer.size > maxBufferSize) {
                val toDrop = frameBuffer.size - maxBufferSize
                if (toDrop > 0) frameBuffer.subList(0, toDrop).clear()
            }
            tryParseFramesFromBuffer()
        }
    }

    fun connect(onStatus: (String) -> Unit) {
        Log.d("HardwareManager", "connect called")
        DebugLogger.i("HardwareManager", "开始连接", showToast = false)
        // 冷启动优化：若当前无连接且资源集合为空，跳过一次全量断开以减少开销
        val needDisconnect = isConnected || (serialPort != null) || (usbIoManager != null)
        if (needDisconnect) {
            disconnect()
        }
        // 确保广播已注册，用于权限回调
        registerUsbReceiver()
    
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        Log.d("HardwareManager", "检测到 ${availableDrivers.size} 个驱动")
        DebugLogger.i("HardwareManager", "检测到 ${availableDrivers.size} 个驱动")
        if (availableDrivers.isEmpty()) {
            isConnected = false
            Log.e("HardwareManager", "未检测到设备")
            DebugLogger.w("HardwareManager", "未检测到设备", showToast = false)
            onStatusListener?.invoke("未检测到设备")
            onStatus("未检测到设备")
            return
        }
        onStatusListener = onStatus
    
        var permissionRequested = false
        // 仅选择并连接一个可用端口（与旧版保持一致）
        for (driver in availableDrivers) {
            // 识别设备信息并日志输出（VID/PID 映射常见芯片）
            try {
                val dev = driver.device
                val vid = dev.vendorId
                val pid = dev.productId
                val chip = when {
                    vid == 0x1A86 && pid == 0x7523 -> "WCH CH340/CH341"
                    vid == 0x10C4 && pid == 0xEA60 -> "Silicon Labs CP2102"
                    vid == 0x10C4 -> "Silicon Labs CP210x"
                    else -> "未知芯片"
                }
                Log.i(
                    "HardwareManager",
                    "发现USB设备: vid=0x${vid.toString(16).uppercase()}, pid=0x${pid.toString(16).uppercase()} ($chip), devName=${dev.deviceName}"
                )
                DebugLogger.i("HardwareManager", "发现USB设备: vid=0x${vid.toString(16).uppercase()}, pid=0x${pid.toString(16).uppercase()} ($chip)")
            } catch (e: Exception) {
                Log.w("HardwareManager", "读取设备信息失败: ${e.message}", e)
                DebugLogger.w("HardwareManager", "读取设备信息失败: ${e.message}")
            }
    
            try {
                val connection = usbManager.openDevice(driver.device)
                if (connection == null) {
                    Log.w("HardwareManager", "没有USB权限或打开失败: ${driver.device.deviceId}，尝试请求权限")
                    val flags = if (android.os.Build.VERSION.SDK_INT >= 31) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    val pi = PendingIntent.getBroadcast(context, 0, Intent(USB_PERMISSION_ACTION), flags)
                    try {
                        usbManager.requestPermission(driver.device, pi)
                        permissionRequested = true
                        // 避免立即认为未连接，等待授权回调
                        onStatusListener?.invoke("等待USB授权")
                    } catch (e: Exception) {
                        Log.e("HardwareManager", "请求USB权限失败: ${e.message}", e)
                    }
                    continue
                }
    
                // 仅选择第一个端口
                val port = try { driver.ports.first() } catch (e: Exception) { null }
                if (port == null) continue
                try {
                    port.open(connection)
                    // 与旧版一致：9600, 8N1
                    port.setParameters(
                        9600,
                        UsbSerialPort.DATABITS_8,
                        UsbSerialPort.STOPBITS_1,
                        UsbSerialPort.PARITY_NONE
                    )
                    // 尝试打开硬件握手
                    try { port.setDTR(true); port.setRTS(true) } catch (e: Exception) {
                        Log.w("HardwareManager", "设置DTR/RTS失败: ${e.message}", e)
                    }
                    // 清空硬件缓冲
                    try { port.purgeHwBuffers(true, true) } catch (e: Exception) {
                        Log.w("HardwareManager", "清空硬件缓冲失败: ${e.message}", e)
                    }
    
                    // 设置为活动端口并启动单一 IO 管理器
                    serialPort = port
                    usbIoManager = SerialInputOutputManager(port, this)
                    try { usbIoManager?.start() } catch (e: Exception) {
                        Log.w("HardwareManager", "启动IO管理器失败: ${e.message}", e)
                    }
    
                    isConnected = true
                    Log.i("HardwareManager", "连接成功，已打开端口=${port.portNumber}")
                    onStatusListener?.invoke("已连接")
                    DebugLogger.i("HardwareManager", "连接成功，端口=${port.portNumber}", showToast = false)
                    reconnectAttempts = 0
                    return
                } catch (e: Exception) {
                    Log.e("HardwareManager", "打开端口失败: ${e.message}", e)
                    try { port.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e("HardwareManager", "连接驱动失败: ${e.message}", e)
            }
        }
    
        // 如果刚刚请求了权限，则等待广播回调，不要立即判定为未连接
        if (permissionRequested) {
            Log.i("HardwareManager", "已请求USB权限，等待用户授权")
            return
        }
    
        isConnected = false
        Log.w("HardwareManager", "连接失败或无可用端口")
        onStatusListener?.invoke("未连接")
        DebugLogger.w("HardwareManager", "连接失败或无可用端口", showToast = false)
    }

    private fun scheduleReconnect() {
        // 若已有重连任务在进行，避免重复创建
        if (reconnectJob?.isActive == true) return
        val attempt = reconnectAttempts
        val delayMs = if (attempt == 0) 0L else (500L shl ((attempt - 1).coerceAtMost(5))).coerceAtMost(30_000L)
        DebugLogger.w("HardwareManager", "计划在${delayMs}ms后重连(第${attempt + 1}次)", showToast = false)
        reconnectJob = scope.launch(Dispatchers.IO) {
            try {
                delay(delayMs)
                if (isConnected) return@launch
                reconnectAttempts += 1
                onStatusListener?.invoke("正在自动重连(第${reconnectAttempts}次)...")
                try {
                    connect { status -> onStatusListener?.invoke(status) }
                } catch (e: Exception) {
                    Log.e("HardwareManager", "自动重连失败: ${e.message}", e)
                    DebugLogger.e("HardwareManager", "自动重连失败: ${e.message}", e)
                }
            } finally {
                reconnectJob = null
            }
        }
    }

    private fun buildFrame(cmd: Int, p0: Int = 0, p1: Int = 0, p2: Int = 0, p3: Int = 0): ByteArray {
        // 根据协议模式构建帧：
        // - 旧规(无边界)：[CMD P0 P1 P2 P3]
        // - 新规(带边界)：FF CMD P0 P1 P2 P3 FE
        return if (!useFramedProtocol) {
            byteArrayOf(
                (cmd and 0xFF).toByte(),
                (p0 and 0xFF).toByte(),
                (p1 and 0xFF).toByte(),
                (p2 and 0xFF).toByte(),
                (p3 and 0xFF).toByte()
            )
        } else {
            byteArrayOf(
                0xFF.toByte(),
                (cmd and 0xFF).toByte(),
                (p0 and 0xFF).toByte(),
                (p1 and 0xFF).toByte(),
                (p2 and 0xFF).toByte(),
                (p3 and 0xFF).toByte(),
                0xFE.toByte()
            )
        }
    }

    // 新增：后台管理指令的 9 数据位载荷构造（CMD + P0 P1 P2 P3 + 4个补零）
    // 要求：即便是管理/控制类指令，数据位也需填充至 9 字节。
    private fun buildAdminPayload9(cmd: Int, p0: Int = 0, p1: Int = 0, p2: Int = 0, p3: Int = 0): ByteArray {
        return byteArrayOf(
            (cmd and 0xFF).toByte(),
            (p0 and 0xFF).toByte(),
            (p1 and 0xFF).toByte(),
            (p2 and 0xFF).toByte(),
            (p3 and 0xFF).toByte(),
            0x00, 0x00, 0x00, 0x00
        )
    }

    // 固定为 BE（高位在前）：重量解析直接采用 weight=(P0<<8)|P1

    // 旧扩展（含补偿字段）的载荷构造：目前已按新要求停用，仅保留以备后续兼容。
    // 实际发送采用 9 数据位（CMD + W1 V1 W2 V2 W3 V3 W4 V4），外层 FF/FE 包裹。

    // 旧规配方包：仅 9 字节（CMD + 8 个 w/v），无补偿字段、无边界
    private fun buildLegacyRecipePacket(
        cmd: Int,
        w1: Int, v1: Int,
        w2: Int, v2: Int,
        w3: Int, v3: Int,
        w4: Int, v4: Int
    ): ByteArray {
        return byteArrayOf(
            (cmd and 0xFF).toByte(),
            (w1 and 0xFF).toByte(), (v1 and 0xFF).toByte(),
            (w2 and 0xFF).toByte(), (v2 and 0xFF).toByte(),
            (w3 and 0xFF).toByte(), (v3 and 0xFF).toByte(),
            (w4 and 0xFF).toByte(), (v4 and 0xFF).toByte()
        )
    }

    // 包裹边界：FF ... FE
    private fun wrapWithBoundaries(payload: ByteArray): ByteArray {
        val framed = ByteArray(payload.size + 2)
        framed[0] = 0xFF.toByte()
        System.arraycopy(payload, 0, framed, 1, payload.size)
        framed[framed.size - 1] = 0xFE.toByte()
        return framed
    }

    

    private val writeTimeoutMs: Int = 500
    private fun sendAdminCommand9(
        cmd: Int,
        p0: Int = 0,
        p1: Int = 0,
        p2: Int = 0,
        p3: Int = 0,
        forceFramed: Boolean? = null
    ): Boolean {
        val framed = forceFramed ?: useFramedProtocol
        if (framed) {
            val frame = adminFrame11Local.get()
            frame[0] = 0xFF.toByte()
            frame[1] = (cmd and 0xFF).toByte()
            frame[2] = (p0 and 0xFF).toByte()
            frame[3] = (p1 and 0xFF).toByte()
            frame[4] = (p2 and 0xFF).toByte()
            frame[5] = (p3 and 0xFF).toByte()
            frame[6] = 0x00
            frame[7] = 0x00
            frame[8] = 0x00
            frame[9] = 0x00
            frame[10] = 0xFE.toByte()
            return sendCommand(frame)
        }

        val payload = adminPayload9Local.get()
        payload[0] = (cmd and 0xFF).toByte()
        payload[1] = (p0 and 0xFF).toByte()
        payload[2] = (p1 and 0xFF).toByte()
        payload[3] = (p2 and 0xFF).toByte()
        payload[4] = (p3 and 0xFF).toByte()
        payload[5] = 0x00
        payload[6] = 0x00
        payload[7] = 0x00
        payload[8] = 0x00
        return sendCommand(payload)
    }

    private fun sendCommand(frame: ByteArray): Boolean {
        val port = serialPort
        if (!isConnected || port == null) {
            Log.w("HardwareManager", "未连接，无法发送指令")
            onStatusListener?.invoke("未连接")
            // 不再直接弹出Toast，避免重复提示
            DebugLogger.w("HardwareManager", "未连接，无法发送指令", showToast = false)
            return false
        }
        return try {
            synchronized(writeLock) { port.write(frame, writeTimeoutMs) }
            if (DebugLogger.isVerboseEnabled()) {
                val hex = frame.joinToString(" ") { "%02X".format(it) }
                Log.d("HardwareManager", "已发送: $hex 到端口 ${port.portNumber}")
                DebugLogger.i("HardwareManager", "已发送: $hex 到端口 ${port.portNumber}", showToast = false)
            }
            true
        } catch (e: IOException) {
            Log.e("HardwareManager", "发送失败(端口${port.portNumber}): ${e.message}", e)
            DebugLogger.e("HardwareManager", "发送失败(端口${port.portNumber}): ${e.message}", e, showToast = false)
            false
        } catch (e: Exception) {
            Log.e("HardwareManager", "发送过程中发生异常: ${e.message}", e)
            DebugLogger.e("HardwareManager", "发送过程中发生异常: ${e.message}", e, showToast = false)
            false
        }
    }

    private fun sendCommandWithRetry(frame: ByteArray): Boolean {
        val port = serialPort ?: return false
        var lastError: Exception? = null
        repeat(2) {
            try {
                synchronized(writeLock) { port.write(frame, writeTimeoutMs) }
                if (DebugLogger.isVerboseEnabled()) {
                    val hex = frame.joinToString(" ") { "%02X".format(it) }
                    Log.d("HardwareManager", "已发送: $hex 到端口 ${port.portNumber}")
                    DebugLogger.i("HardwareManager", "已发送: $hex 到端口 ${port.portNumber}", showToast = false)
                }
                return true
            } catch (e: IOException) {
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
        }
        val err = lastError
        if (err != null) {
            Log.e("HardwareManager", "发送失败(端口${port.portNumber}): ${err.message}", err)
            DebugLogger.e("HardwareManager", "发送失败(端口${port.portNumber}): ${err.message}", err, showToast = false)
        }
        return false
    }

    fun makeJuice(recipe: Recipe, cupSize: String, withIce: Boolean): Boolean {
        // 新协议：配方执行（冰度决定主命令） CMD=0x01(正常冰) / 0x02(去冰，先去皮)
        // 约定：通道映射 -> 1:水；2:果汁通道1；3:果汁通道2；4:果汁通道3
        // 注：cupSize 由调用方自行计算体积，这里直接使用 recipe 的当前数值
        val cmd = if (withIce) 0x01 else 0x02
        val waterVol = recipe.water.coerceIn(0, 255)
        val waterSp = recipe.waterSpeed.coerceIn(0, 255)
        val juiceVol = recipe.juice.coerceIn(0, 255)
        val juiceSp = recipe.juiceSpeed.coerceIn(0, 255)

        val ch = recipe.juiceChannel.coerceIn(1, 3)
        val w2 = if (ch == 1) juiceVol else 0
        val w3 = if (ch == 2) juiceVol else 0
        val w4 = if (ch == 3) juiceVol else 0
        val v2 = if (ch == 1) juiceSp else 0
        val v3 = if (ch == 2) juiceSp else 0
        val v4 = if (ch == 3) juiceSp else 0

        // 详细构建日志（下发前打印）
        DebugLogger.i(
            "HardwareManager",
            "构建配方: ${if (withIce) "正常冰(CMD=0x01)" else "去冰(CMD=0x02, 先去皮)"}; " +
                "W1=$waterVol V1=$waterSp W2=$w2 V2=$v2 W3=$w3 V3=$v3 W4=$w4 V4=$v4; ch=$ch; framed=$useFramedProtocol"
        )

        // 无果肉补偿：补偿参数仅前端使用，不下发任何额外字节
        if (!useFramedProtocol) {
            // 旧规：无边界，仅 9 字节（忽略补偿字段）
            val legacy = buildLegacyRecipePacket(
                cmd = cmd,
                w1 = waterVol, v1 = waterSp,
                w2 = w2, v2 = v2,
                w3 = w3, v3 = v3,
                w4 = w4, v4 = v4
            )
            DebugLogger.i(
                "HardwareManager",
                "发送旧规配方(无边界) ${if (withIce) "正常冰(CMD=0x01)" else "去冰(CMD=0x02)"}: ${legacy.joinToString(" ") { "%02X".format(it) }}"
            )
            return sendCommandWithRetry(legacy)
        } else {
            // 新规：仅下发 9 数据位（CMD + W1 V1 W2 V2 W3 V3 W4 V4），外层 FF/FE 包裹
            val payload9 = buildLegacyRecipePacket(
                cmd = cmd,
                w1 = waterVol, v1 = waterSp,
                w2 = w2, v2 = v2,
                w3 = w3, v3 = v3,
                w4 = w4, v4 = v4
            )
            val frame = wrapWithBoundaries(payload9)
            // 发送前进行边界与CMD校验
            val validFrame = frame.size == 11 && frame[0] == 0xFF.toByte() && frame[frame.size - 1] == 0xFE.toByte() && frame[1] == (cmd and 0xFF).toByte()
            if (!validFrame) {
                DebugLogger.e(
                    "HardwareManager",
                    "帧校验失败: 期望 FF ... FE, CMD=0x%02X, 实际=${frame.joinToString(" ") { "%02X".format(it) }}".format(cmd)
                )
            }
            DebugLogger.i(
                "HardwareManager",
                "发送新规配方(FF + 9数据位 + FE) ${if (withIce) "正常冰(CMD=0x01)" else "去冰(CMD=0x02)"}: ${frame.joinToString(" ") { "%02X".format(it) }}"
            )
            return sendCommandWithRetry(frame)
        }
    }

        // 新规：含果肉配方（冰度决定CMD；补偿仅前端自调，帧仍为9数据位）
    fun makeJuiceWithCompensation(
        recipe: Recipe,
        cupSize: String,
        withIce: Boolean
    ): Boolean {
        val cmd = if (withIce) 0x01 else 0x02
        val waterVol = recipe.water.coerceIn(0, 255)
        val waterSp = recipe.waterSpeed.coerceIn(0, 255)
        val juiceVol = recipe.juice.coerceIn(0, 255)
        val juiceSp = recipe.juiceSpeed.coerceIn(0, 255)

        val ch = recipe.juiceChannel.coerceIn(1, 3)
        val w2 = if (ch == 1) juiceVol else 0
        val w3 = if (ch == 2) juiceVol else 0
        val w4 = if (ch == 3) juiceVol else 0
        val v2 = if (ch == 1) juiceSp else 0
        val v3 = if (ch == 2) juiceSp else 0
        val v4 = if (ch == 3) juiceSp else 0

        val totalCups = recipe.pulpTotalCups.coerceIn(1, 255)
        val decInterval = recipe.pulpDecInterval.coerceIn(0, 255)
        val decAmount = recipe.pulpDecAmount.coerceIn(0, 255)
        // 详细构建日志（下发前打印），补偿参数仅用于前端自调
        DebugLogger.i(
            "HardwareManager",
            "构建配方(含果肉): ${if (withIce) "正常冰(CMD=0x01)" else "去冰(CMD=0x02, 先去皮)"}; " +
                "W1=$waterVol V1=$waterSp W2=$w2 V2=$v2 W3=$w3 V3=$v3 W4=$w4 V4=$v4; ch=$ch; framed=$useFramedProtocol; " +
                "pulp(total=$totalCups, interval=$decInterval, amount=$decAmount)"
        )
        if (!useFramedProtocol) {
            // 旧规：后端不识别补偿字段，忽略之，仅发送 9 字节
            val legacy = buildLegacyRecipePacket(
                cmd = cmd,
                w1 = waterVol, v1 = waterSp,
                w2 = w2, v2 = v2,
                w3 = w3, v3 = v3,
                w4 = w4, v4 = v4
            )
            DebugLogger.i(
                "HardwareManager",
                "发送旧规配方(无边界，忽略补偿) ${if (withIce) "正常冰(CMD=0x01)" else "去冰(CMD=0x02)"}: ${legacy.joinToString(" ") { "%02X".format(it) }}"
            )
            return sendCommandWithRetry(legacy)
        } else {
            // 新规：补偿仅用于前端自调，不下发额外字段；仍发送 9 数据位
            val payload9 = buildLegacyRecipePacket(
                cmd = cmd,
                w1 = waterVol, v1 = waterSp,
                w2 = w2, v2 = v2,
                w3 = w3, v3 = v3,
                w4 = w4, v4 = v4
            )
            val frame = wrapWithBoundaries(payload9)
            // 发送前进行边界与CMD校验
            val validFrame = frame.size == 11 && frame[0] == 0xFF.toByte() && frame[frame.size - 1] == 0xFE.toByte() && frame[1] == (cmd and 0xFF).toByte()
            if (!validFrame) {
                DebugLogger.e(
                    "HardwareManager",
                    "帧校验失败(含果肉): 期望 FF ... FE, CMD=0x%02X, 实际=${frame.joinToString(" ") { "%02X".format(it) }}".format(cmd)
                )
            }
            DebugLogger.i(
                "HardwareManager",
                "发送新规配方(FF + 9数据位 + FE，补偿不下发) ${if (withIce) "正常冰(CMD=0x01)" else "去冰(CMD=0x02)"}: ${frame.joinToString(" ") { "%02X".format(it) }}"
            )
            return sendCommandWithRetry(frame)
        }
    }

    // 新增：热饮（只加果汁，不加水），CMD=0x10，水量与水速强制为0
    fun makeHotDrink(recipe: Recipe, cupSize: String): Boolean {
        val cmd = 0x10
        val waterVol = 0
        val waterSp = 0
        val juiceVol = recipe.juice.coerceIn(0, 255)
        val juiceSp = recipe.juiceSpeed.coerceIn(0, 255)

        val ch = recipe.juiceChannel.coerceIn(1, 3)
        val w2 = if (ch == 1) juiceVol else 0
        val w3 = if (ch == 2) juiceVol else 0
        val w4 = if (ch == 3) juiceVol else 0
        val v2 = if (ch == 1) juiceSp else 0
        val v3 = if (ch == 2) juiceSp else 0
        val v4 = if (ch == 3) juiceSp else 0

        if (!useFramedProtocol) {
            val legacy = buildLegacyRecipePacket(
                cmd = cmd,
                w1 = waterVol, v1 = waterSp,
                w2 = w2, v2 = v2,
                w3 = w3, v3 = v3,
                w4 = w4, v4 = v4
            )
            DebugLogger.i(
                "HardwareManager",
                "发送旧规热饮配方(无边界，CMD=0x10，水量=0): ${legacy.joinToString(" ") { "%02X".format(it) }}"
            )
            return sendCommandWithRetry(legacy)
        } else {
            val payload9 = buildLegacyRecipePacket(
                cmd = cmd,
                w1 = waterVol, v1 = waterSp,
                w2 = w2, v2 = v2,
                w3 = w3, v3 = v3,
                w4 = w4, v4 = v4
            )
            val frame = wrapWithBoundaries(payload9)
            DebugLogger.i(
                "HardwareManager",
                "发送新规热饮配方(FF + 9数据位 + FE，CMD=0x10，水量=0): ${frame.joinToString(" ") { "%02X".format(it) }}"
            )
            return sendCommandWithRetry(frame)
        }
    }

    fun sendAdminCommand(code: Int): Boolean {
        // 统一：管理员指令采用 FF + 9数据位 + FE（数据位补零）
        val payload9 = buildAdminPayload9(code and 0xFF, 0, 0, 0, 0)
        val frame = wrapWithBoundaries(payload9)
        DebugLogger.i(
            "HardwareManager",
            "发送管理员指令(FF + 9数据位 + FE) CMD=0x${(code and 0xFF).toString(16).uppercase()}: ${frame.joinToString(" ") { "%02X".format(it) }}"
        )
        return sendCommand(frame)
    }

    // 与后端文档一致：控制类子命令（清洗/加水 开始/停止）与 HX711 子命令
    fun sendCleanStart(): Boolean {
        return sendAdminCommand9(cmd = 0x03, p0 = 0x01, forceFramed = true)
    }

    fun sendCleanStop(): Boolean {
        return sendAdminCommand9(cmd = 0x04, p0 = 0x01, forceFramed = true)
    }

    // 急停：发送 9 数据位帧（FF + 9字节 + FE），与配方下单格式一致（数据位补零）
    fun sendEmergencyStop(): Boolean {
        return sendAdminCommand9(cmd = 0x09, forceFramed = true)
    }

    fun sendWaterStart(): Boolean {
        // 文档对齐：加水开始不携带速度参数，后续速度由配方帧更新速度表决定
        return sendAdminCommand9(cmd = 0x03, p0 = 0x02, forceFramed = true)
    }

    // 新增：加水开始（携带水速，0-255 -> 16进制放入P1）
    fun sendWaterStart(speed: Int): Boolean {
        val sp = speed.coerceIn(0, 255)
        return sendAdminCommand9(cmd = 0x03, p0 = 0x02, p1 = sp, forceFramed = true)
    }

    fun sendWaterStop(): Boolean {
        return sendAdminCommand9(cmd = 0x04, p0 = 0x02, forceFramed = true)
    }

    fun sendHX711Tare(): Boolean {
        return sendAdminCommand9(cmd = 0x05, p0 = 0x01, forceFramed = true)
    }

    fun sendHX711Weigh(): Boolean {
        return sendAdminCommand9(cmd = 0x05, p0 = 0x02, forceFramed = true)
    }

    fun sendHX711Calibrate(targetGrams: Int = 1000): Boolean {
        val hi = (targetGrams shr 8) and 0xFF
        val lo = targetGrams and 0xFF
        return sendAdminCommand9(cmd = 0x05, p0 = 0x03, p2 = hi, p3 = lo, forceFramed = true)
    }

    fun sendContinueCommand(): Boolean {
        return sendAdminCommand9(cmd = 0x07, forceFramed = null)
    }

    fun sendRestartCommand(): Boolean {
        return sendAdminCommand9(cmd = 0x08, forceFramed = null)
    }

    // 只出水：发送使用 11 字节（FF + 9 数据位 + FE），接收解析 7 字节
    fun sendWaterOnlyStart(): Boolean {
        return sendAdminCommand9(cmd = 0x03, p0 = 0x02, forceFramed = null)
    }

    // 只出水（携带水速，放入P1）：FF + [03 02 SP 00 00 00 00 00 00] + FE
    fun sendWaterOnlyStart(speed: Int): Boolean {
        val sp = speed.coerceIn(0, 255)
        return sendAdminCommand9(cmd = 0x03, p0 = 0x02, p1 = sp, forceFramed = null)
    }

    fun sendWaterOnlyStop(): Boolean {
        return sendAdminCommand9(cmd = 0x04, p0 = 0x02, forceFramed = null)
    }

    // 新增：预设水通道最高速度（通过发送零配方，仅更新速度表，不出液）
    // 说明：固件侧仅在接收到制作帧(CMD=0x01/0x02)时调用 UpdateChannelTopSpeed(channel, topSpeed)。
    // 因此为了让“只出水”使用指定速度，需先下发一个“体积全为0”的制作帧来刷新速度表。
    fun primeWaterTopSpeed(topSpeed: Int): Boolean {
        val cmd = 0x01 // 使用正常冰避免去皮
        val waterSp = topSpeed.coerceIn(0, 255)
        // 四通道体积均为0，仅设置水通道速度；果汁通道速度也设为0
        val payload9 = buildLegacyRecipePacket(
            cmd = cmd,
            w1 = 0, v1 = waterSp,
            w2 = 0, v2 = 0,
            w3 = 0, v3 = 0,
            w4 = 0, v4 = 0
        )
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload9) else payload9
        DebugLogger.i(
            "HardwareManager",
            "预设水速(更新速度表，不出液): ${frame.joinToString(" ") { "%02X".format(it) }}; framed=$useFramedProtocol"
        )
        val ok = sendCommand(frame)
        if (ok) lastPrimedWaterTopSpeed = waterSp
        return ok
    }

    // 标准砝码校准（11.8 改版对齐）：走 0x05 子命令 0x03
    // 约定：Byte0=0x05(CMD)、Byte1=0x03(校准子命令)、Byte2=0(保留)、Byte3=hi、Byte4=lo、Byte5-8=0
    // 若 targetGrams=1000（默认），则发送 hi=0、lo=0，由固件采用默认值（CALIB_DEFAULT_WEIGHT）
    fun sendCalibrateStandard(targetGrams: Int = 1000): Boolean {
        val useDefault = targetGrams == 1000
        val hi = if (useDefault) 0 else (targetGrams ushr 8) and 0xFF
        val lo = if (useDefault) 0 else (targetGrams and 0xFF)
        return sendAdminCommand9(cmd = 0x05, p0 = 0x03, p2 = hi, p3 = lo, forceFramed = true)
    }

    fun disconnect() {
        try {
            try { usbIoManager?.stop() } catch (_: Exception) {}
            try { serialPort?.close() } catch (_: Exception) {}
            serialPort = null
            usbIoManager = null
            // 取消重连任务
            try { reconnectJob?.cancel() } catch (_: Exception) {}
            reconnectJob = null
            reconnectAttempts = 0
            // 新增：注销USB广播接收器
            if (usbReceiverRegistered) {
                try { context.unregisterReceiver(usbReceiver) } catch (_: Exception) {}
                usbReceiverRegistered = false
                usbReceiver = null
            }
        } finally {
            isConnected = false
            onStatusListener?.invoke("未连接")
        }
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerUsbReceiver() {
        if (usbReceiverRegistered) return
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                when (action) {
                    // 新增：处理USB权限授权回调
                    USB_PERMISSION_ACTION -> {
                        val granted = intent.getBooleanExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (granted) {
                            Log.i("HardwareManager", "USB权限已授权，重新尝试连接")
                            onStatusListener?.invoke("USB已授权，正在连接…")
                            try { connect { status -> onStatusListener?.invoke(status) } } catch (_: Exception) {}
                        } else {
                            Log.e("HardwareManager", "USB权限被拒绝")
                            onStatusListener?.invoke("USB权限被拒绝")
                        }
                    }
                    // 设备插拔广播
                    android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        Log.i("HardwareManager", "检测到设备插入，尝试连接")
                        onStatusListener?.invoke("检测到设备，正在连接")
                        try { connect { status -> onStatusListener?.invoke(status) } } catch (_: Exception) {}
                    }
                    android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        Log.w("HardwareManager", "检测到设备拔出，断开连接")
                        try { disconnect() } catch (_: Exception) {}
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(USB_PERMISSION_ACTION)
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(usbReceiver, filter)
            }
            usbReceiverRegistered = true
        } catch (e: Exception) {
            Log.w("HardwareManager", "注册USB广播失败: ${e.message}", e)
        }
    }

    override fun onRunError(e: Exception) {
        Log.e("HardwareManager", "串口IO出错或已停止: ${e.message}", e)
        DebugLogger.e("HardwareManager", "串口IO出错或已停止: ${e.message}", e, showToast = false)
        onStatusListener?.invoke("连接异常: ${e.message}")
        isConnected = false
        scheduleReconnect()
    }


    fun setOnStatusListener(listener: (String) -> Unit) {
        onStatusListener = listener
    }

    fun setOnWeightAnomalyListener(listener: (WeightAnomalyData) -> Unit) {
        onWeightAnomalyListener = listener
    }

    fun setOnOrderCompletionListener(listener: (Boolean) -> Unit) {
        onOrderCompletionListener = listener
    }

    // 新增：温度/重量/中性完成事件监听注册
    fun setOnTemperatureReportListener(listener: (Int) -> Unit) {
        onTemperatureReportListener = listener
    }

    fun setOnWeightReportListener(listener: (Int) -> Unit) {
        onWeightReportListener = listener
    }

    fun setOnNeutralCompletionListener(listener: () -> Unit) {
        onNeutralCompletionListener = listener
    }
}
