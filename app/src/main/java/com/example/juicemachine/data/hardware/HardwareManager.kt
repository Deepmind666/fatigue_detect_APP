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
    // 兼容多端口场景时的集合（当前逻辑只启用一个）
    private val openedPorts: MutableList<UsbSerialPort> = mutableListOf()
    private val ioManagers: MutableList<SerialInputOutputManager> = mutableListOf()
    // 资源集合锁，避免并发修改导致崩溃
    private val portLock = Any()
    // 写入锁，避免多个调用同时写串口
    private val writeLock = Any()
    @Volatile private var toastOnSend: Boolean = false
    // 手动读取任务（保留以兼容历史实现，当前主要依赖IO管理器回调）
    private var manualReadJob: Job? = null
    private val manualReadJobs: MutableList<Job> = mutableListOf()
    // 自动重连控制
    private var reconnectJob: Job? = null
    private var reconnectAttempts: Int = 0
    // 二进制字节流缓冲（仅用于调试/扩展；真实解析使用 frameBuffer）
    private val binaryRxBuffer: MutableList<Byte> = ArrayList(2048)
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

    // 连续字节流粘包/分包缓冲，确保解析到以0xFF开头、0xFE结尾的完整帧
    private val frameBuffer = ArrayList<Byte>(256)
    private val maxBufferSize = 2048

    // USB权限与设备插拔广播
    private var usbReceiver: BroadcastReceiver? = null
    private var usbReceiverRegistered: Boolean = false

    private fun indexOfByte(buf: List<Byte>, value: Byte, start: Int = 0): Int {
        for (i in start until buf.size) if (buf[i] == value) return i
        return -1
    }

    /**
     * 从 frameBuffer 中尽可能解析完整帧并分发处理。
     * 协议为 7 字节：FF CMD P0 P1 P2 P3 FE。
     * - CMD=0x09：重量异常，P0=severity，P1~P2=预期重量(lo,hi)；
     * - 其他CMD：使用 P0 作为状态字节（0xAA=成功，0xAC=失败）来判定订单结果；
     * - 解析到非目标帧时只做日志，避免误处理未知扩展指令。
     */
    private fun tryParseFramesFromBuffer() {
        while (true) {
            // 查找起始标记
            val startIdx = indexOfByte(frameBuffer, 0xFF.toByte())
            if (startIdx == -1) return // 没有起始标记，等待更多数据
            // 丢弃起始标记之前的无效数据
            if (startIdx > 0) {
                repeat(startIdx) { frameBuffer.removeAt(0) }
            }
            // 查找结束标记
            val endIdx = indexOfByte(frameBuffer, 0xFE.toByte(), 1)
            if (endIdx == -1) return // 等待更多数据

            val frameLen = endIdx + 1
            val frame = ByteArray(frameLen)
            for (i in 0 until frameLen) frame[i] = frameBuffer[i]

            // 移除已消费字节
            repeat(frameLen) { frameBuffer.removeAt(0) }

            // 仅处理 7 字节协议帧
            if (frameLen >= 7 && frame[0] == 0xFF.toByte() && frame[frameLen - 1] == 0xFE.toByte()) {
                val cmd = frame[1].toInt() and 0xFF
                if (cmd == 0x09) {
                    // 重量异常：FF 09 [severity] [lo] [hi] .. FE（项目已有约定）
                    val severityByte = frame[2].toInt() and 0xFF
                    val value = ((frame[4].toInt() and 0xFF) shl 8) or (frame[3].toInt() and 0xFF)
                    val severity = when (severityByte) {
                        1 -> WeightAnomalySeverity.LOW
                        2 -> WeightAnomalySeverity.MEDIUM
                        3, 4 -> WeightAnomalySeverity.HIGH
                        else -> WeightAnomalySeverity.MEDIUM
                    }
                    Log.d(
                        "HardwareManager",
                        "解析到重量异常帧: " + frame.joinToString(" ") { "%02X".format(it) } +
                            ", severity=$severityByte, value=$value"
                    )
                    CoroutineScope(Dispatchers.Main).launch {
                        onWeightAnomalyListener?.invoke(
                            WeightAnomalyData(
                                currentWeight = 0,
                                expectedWeight = value,
                                severity = severity,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        // 改为仅记录日志，不直接弹Toast，避免干扰
                        DebugLogger.w("HardwareManager", "检测到重量异常: severity=$severityByte, value=$value", showToast = false)
                    }
                } else {
                    // 订单完成/失败：FF [cmd] [status] ... FE，其中 status=0xAA(成功)/0xAC(失败)
                    val status = frame[2].toInt() and 0xFF
                    when (status) {
                        0xAA -> {
                            Log.i("HardwareManager", "收到完成状态(7B): cmd=$cmd status=0xAA 成功")
                            DebugLogger.i("HardwareManager", "订单完成(7B): cmd=$cmd status=0xAA 成功", showToast = false)
                            CoroutineScope(Dispatchers.Main).launch { onOrderCompletionListener?.invoke(true) }
                        }
                        0xAC -> {
                            Log.w("HardwareManager", "收到失败状态(7B): cmd=$cmd status=0xAC 失败")
                            DebugLogger.w("HardwareManager", "订单失败(7B): cmd=$cmd status=0xAC 失败", showToast = false)
                            CoroutineScope(Dispatchers.Main).launch { onOrderCompletionListener?.invoke(false) }
                        }
                        else -> {
                            val hex = frame.joinToString(" ") { "%02X".format(it) }
                            Log.d("HardwareManager", "解析到非目标帧: $hex")
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
            val hexString = data.joinToString(separator = " ") { "%02X".format(it) }
            Log.d("HardwareManager", "接收到数据: $hexString")
            DebugLogger.i("HardwareManager", "接收到数据: $hexString")
            // 移除对 5 字节 FD/FC 的解析；仅保留 7 字节帧解析
            for (b in data) frameBuffer.add(b)
            if (frameBuffer.size > maxBufferSize) {
                val toDrop = frameBuffer.size - maxBufferSize
                repeat(toDrop) { if (frameBuffer.isNotEmpty()) frameBuffer.removeAt(0) }
            }
            tryParseFramesFromBuffer()
        }
    }

    fun connect(onStatus: (String) -> Unit) {
        Log.d("HardwareManager", "connect called")
        DebugLogger.i("HardwareManager", "开始连接", showToast = false)
        // 先清理旧连接，避免注销广播后权限回调丢失
        disconnect()
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
        val delayMs = (1000L shl attempt.coerceAtMost(5)).coerceAtMost(30_000L)
        DebugLogger.w("HardwareManager", "计划在${delayMs / 1000}s后重连(第${attempt + 1}次)", showToast = false)
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

    // 旧扩展（含补偿字段）的载荷构造：目前已按新要求停用，仅保留以备后续兼容。
    // 实际发送采用 9 数据位（CMD + W1 V1 W2 V2 W3 V3 W4 V4），外层 FF/FE 包裹。
    private fun buildNewRecipePacket(
        cmd: Int,
        w1: Int, v1: Int,
        w2: Int, v2: Int,
        w3: Int, v3: Int,
        w4: Int, v4: Int,
        totalCups: Int,
        decInterval: Int,
        decAmount: Int
    ): ByteArray {
        return byteArrayOf(
            (cmd and 0xFF).toByte(),
            (w1 and 0xFF).toByte(), (v1 and 0xFF).toByte(),
            (w2 and 0xFF).toByte(), (v2 and 0xFF).toByte(),
            (w3 and 0xFF).toByte(), (v3 and 0xFF).toByte(),
            (w4 and 0xFF).toByte(), (v4 and 0xFF).toByte(),
            (totalCups and 0xFF).toByte(),
            (decInterval and 0xFF).toByte(),
            (decAmount and 0xFF).toByte()
        )
    }

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
            // 单端口发送，使用较短的 2000ms 超时（与旧版一致）
            synchronized(writeLock) {
                port.write(frame, 2000)
            }
            val hex = frame.joinToString(" ") { "%02X".format(it) }
            Log.d("HardwareManager", "已发送: $hex 到端口 ${port.portNumber}")
            DebugLogger.i("HardwareManager", "已发送: $hex 到端口 ${port.portNumber}", showToast = false)
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
            return sendCommand(legacy)
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
            return sendCommand(frame)
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
            return sendCommand(legacy)
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
            return sendCommand(frame)
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
            return sendCommand(legacy)
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
            return sendCommand(frame)
        }
    }

    fun sendAdminCommand(code: Int): Boolean {
        // 管理员指令按要求使用 9 数据位（CMD+P0..P3+补零4字节），并用 FF/FE 包裹（新规）
        val payload = buildAdminPayload9(code and 0xFF, 0, 0, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i(
            "HardwareManager",
            "发送管理员指令(9数据位) CMD=0x${(code and 0xFF).toString(16).uppercase()}: ${frame.joinToString(" ") { "%02X".format(it) }}"
        )
        return sendCommand(frame)
    }

    // 与后端文档一致：控制类子命令（清洗/加水 开始/停止）与 HX711 子命令
    fun sendCleanStart(): Boolean {
        val payload = buildAdminPayload9(0x03, 0x01, 0, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i("HardwareManager", "发送清洗开始(9数据位): ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    fun sendCleanStop(): Boolean {
        val payload = buildAdminPayload9(0x04, 0x01, 0, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i("HardwareManager", "发送清洗停止(9数据位): ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    // 新增：急停（独立指令，不作为控制类子命令），采用9数据位载荷并包裹边界
    fun sendEmergencyStop(): Boolean {
        val payload = buildAdminPayload9(0x09, 0, 0, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i("HardwareManager", "发送急停(9数据位): ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    fun sendWaterStart(): Boolean {
        val payload = buildAdminPayload9(0x03, 0x02, 0, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i("HardwareManager", "发送加水开始(9数据位): ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    // 新增：加水开始（携带水速，0-255 -> 16进制放入P1）
    fun sendWaterStart(speed: Int): Boolean {
        val sp = speed.coerceIn(0, 255)
        val payload = buildAdminPayload9(0x03, 0x02, sp, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i(
            "HardwareManager",
            "发送加水开始(含水速=${sp})(9数据位): ${frame.joinToString(" ") { "%02X".format(it) }}"
        )
        return sendCommand(frame)
    }

    fun sendWaterStop(): Boolean {
        val payload = buildAdminPayload9(0x04, 0x02, 0, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i("HardwareManager", "发送加水停止(9数据位): ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    fun sendHX711Tare(): Boolean {
        val payload = buildAdminPayload9(0x05, 0x01, 0, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i("HardwareManager", "发送HX711去皮(9数据位): ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    fun sendHX711Weigh(): Boolean {
        val payload = buildAdminPayload9(0x05, 0x02, 0, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i("HardwareManager", "发送HX711称重(9数据位): ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    fun sendHX711Calibrate(targetGrams: Int = 1000): Boolean {
        val hi = (targetGrams shr 8) and 0xFF
        val lo = targetGrams and 0xFF
        // 9 数据位：CMD=0x05, P0=0x03, P1=0, P2=hi, P3=lo, 其余补零
        val payload = buildAdminPayload9(0x05, 0x03, 0, hi, lo)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i("HardwareManager", "发送HX711校准(9数据位): ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    fun sendContinueCommand(): Boolean {
        // 独立指令：继续制作；按要求也采用 9 数据位并补零
        val payload = buildAdminPayload9(0x07, 0, 0, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i("HardwareManager", "发送继续制作指令(9数据位): ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    fun sendRestartCommand(): Boolean {
        // 独立指令：重新制作；按要求采用 9 数据位并补零
        val payload = buildAdminPayload9(0x08, 0, 0, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i("HardwareManager", "发送重新制作指令(9数据位): ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    // 只出水：按文档推荐使用控制类子命令开始/停止
    fun sendWaterOnlyStart(): Boolean {
        val payload = buildAdminPayload9(0x03, 0x02, 0, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i("HardwareManager", "发送只出水开始(9数据位控制子命令): ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    // 新增：只出水开始（携带水速，0-255 -> 16进制放入P1）
    fun sendWaterOnlyStart(speed: Int): Boolean {
        val sp = speed.coerceIn(0, 255)
        val payload = buildAdminPayload9(0x03, 0x02, sp, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i(
            "HardwareManager",
            "发送只出水开始(含水速=${sp})(9数据位控制子命令): ${frame.joinToString(" ") { "%02X".format(it) }}"
        )
        return sendCommand(frame)
    }

    fun sendWaterOnlyStop(): Boolean {
        val payload = buildAdminPayload9(0x04, 0x02, 0, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i("HardwareManager", "发送只出水停止(9数据位控制子命令): ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    // 新增：1000g标准校准（保留原有指令路径）
    // 约定：使用管理员扩展码 0x0B，P0/P1携带目标重量lo/hi
    fun sendCalibrateStandard(targetGrams: Int = 1000): Boolean {
        val lo = targetGrams and 0xFF
        val hi = (targetGrams shr 8) and 0xFF
        val payload = buildAdminPayload9(0x0B, lo, hi, 0, 0)
        val frame = if (useFramedProtocol) wrapWithBoundaries(payload) else payload
        DebugLogger.i("HardwareManager", "发送标准校准指令(9数据位): ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    fun disconnect() {
        try {
            // 在锁内获取当前集合快照，逐一停止/关闭，避免并发修改
            val managersSnapshot: List<SerialInputOutputManager> = synchronized(portLock) { ioManagers.toList() }
            managersSnapshot.forEach { manager ->
                try { manager.stop() } catch (_: Exception) {}
            }
            synchronized(portLock) { ioManagers.clear() }

            val portsSnapshot: List<UsbSerialPort> = synchronized(portLock) { openedPorts.toList() }
            portsSnapshot.forEach { port ->
                try { port.close() } catch (_: Exception) {}
            }
            synchronized(portLock) { openedPorts.clear() }

            serialPort = null
            usbIoManager = null
            try { manualReadJobs.forEach { it.cancel() } } catch (_: Exception) {}
            manualReadJobs.clear()
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

    fun setToastOnSend(enabled: Boolean) {
        toastOnSend = enabled
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
}