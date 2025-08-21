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
import android.widget.Toast
import android.os.Build
// 新增：USB权限与设备广播
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.example.juicemachine.util.DebugLogger

// 重量异常数据类
data class WeightAnomalyData(
    val currentWeight: Int,
    val expectedWeight: Int,
    val severity: WeightAnomalySeverity,
    val timestamp: Long
)

enum class WeightAnomalySeverity {
    LOW, MEDIUM, HIGH
}

class HardwareManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : SerialInputOutputManager.Listener {

    companion object {
        const val USB_PERMISSION_ACTION: String = "com.example.juicemachine.USB_PERMISSION"
    }
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private var usbIoManager: SerialInputOutputManager? = null
    // 多端口兼容（双USB-TTL场景）：全部监听，写入广播
    private val openedPorts: MutableList<UsbSerialPort> = mutableListOf()
    private val ioManagers: MutableList<SerialInputOutputManager> = mutableListOf()
    // 新增：统一的端口列表锁，避免并发修改导致的崩溃
    private val portLock = Any()
    // 新增：统一写入锁，避免多处同时写串口导致阻塞或异常
    private val writeLock = Any()
    private var manualReadJob: Job? = null
    private val manualReadJobs: MutableList<Job> = mutableListOf()
    // 新增：自动重连控制
    private var reconnectJob: Job? = null
    private var reconnectAttempts: Int = 0
    // 二进制流缓冲，用于严格按7字节帧解析（FF ...... FE）
    private val binaryRxBuffer: MutableList<Byte> = ArrayList(2048)
    var isConnected: Boolean = false
        private set
    private var onStatusListener: ((String) -> Unit)? = null
    private var onWeightAnomalyListener: ((WeightAnomalyData) -> Unit)? = null
    private var onOrderCompletionListener: ((Boolean) -> Unit)? = null // 制作完成回调：true=成功，false=失败

    // 串口帧缓冲：用于处理分包/粘包，确保能从连续字节流中解析出以0xFF开始、0xFE结束的完整帧
    private val frameBuffer = ArrayList<Byte>(256)
    private val maxBufferSize = 2048

    // 新增：USB广播接收与注册标志
    private var usbReceiver: BroadcastReceiver? = null
    private var usbReceiverRegistered: Boolean = false

    private fun indexOfByte(buf: List<Byte>, value: Byte, start: Int = 0): Int {
        for (i in start until buf.size) if (buf[i] == value) return i
        return -1
    }

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
                            DebugLogger.i("HardwareManager", "订单完成(7B): cmd=$cmd status=0xAA 成功", showToast = true)
                            CoroutineScope(Dispatchers.Main).launch { onOrderCompletionListener?.invoke(true) }
                        }
                        0xAC -> {
                            Log.w("HardwareManager", "收到失败状态(7B): cmd=$cmd status=0xAC 失败")
                            DebugLogger.w("HardwareManager", "订单失败(7B): cmd=$cmd status=0xAC 失败", showToast = true)
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
        DebugLogger.i("HardwareManager", "开始连接", showToast = true)
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
            DebugLogger.w("HardwareManager", "未检测到设备", showToast = true)
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
                    DebugLogger.i("HardwareManager", "连接成功，端口=${port.portNumber}", showToast = true)
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
        DebugLogger.w("HardwareManager", "连接失败或无可用端口", showToast = true)
    }

    private fun scheduleReconnect() {
        // 若已有重连任务在进行，避免重复创建
        if (reconnectJob?.isActive == true) return
        val attempt = reconnectAttempts
        val delayMs = (1000L shl attempt.coerceAtMost(5)).coerceAtMost(30_000L)
        DebugLogger.w("HardwareManager", "计划在${delayMs / 1000}s后重连(第${attempt + 1}次)", showToast = attempt == 0)
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
        // 简单协议：FF CMD P0 P1 P2 P3 FE
        return byteArrayOf(
            0xFF.toByte(),
            (cmd and 0xFF).toByte(),
            (p0 and 0xFF).toByte(),
            (p1 and 0xFF).toByte(),
            (p2 and 0xFF).toByte(),
            (p3 and 0xFF).toByte(),
            0xFE.toByte()
        )
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
            DebugLogger.i("HardwareManager", "已发送: $hex 到端口 ${port.portNumber}")
            true
        } catch (e: IOException) {
            Log.e("HardwareManager", "发送失败(端口${port.portNumber}): ${e.message}", e)
            DebugLogger.e("HardwareManager", "发送失败(端口${port.portNumber}): ${e.message}", e, showToast = true)
            false
        } catch (e: Exception) {
            Log.e("HardwareManager", "发送过程中发生异常: ${e.message}", e)
            DebugLogger.e("HardwareManager", "发送过程中发生异常: ${e.message}", e, showToast = true)
            false
        }
    }

    fun makeJuice(recipe: Recipe, cupSize: String, withIce: Boolean): Boolean {
        // 7字节协议：FF [0x01 正常冰 | 0x02 去冰] A B C D FE
        // A=水量，B/C/D=三个果汁通道对应投放量（仅选中通道有值，其它为0）
        val type = if (withIce) 0x01 else 0x02
        // 按杯型放大用量（与订单统计一致：大杯≈1.3倍）
        val scale = if (cupSize == "大杯") 1.3 else 1.0
        val water = (recipe.water * scale).toInt().coerceIn(0, 255)
        val juice = (recipe.juice * scale).toInt().coerceIn(0, 255)
        val ch = recipe.juiceChannel
        val b = if (ch == 1) juice else 0
        val c = if (ch == 2) juice else 0
        val d = if (ch == 3) juice else 0
        val frame = buildFrame(type, water, b, c, d)
        DebugLogger.i(
            "HardwareManager",
            "发送制作指令(${if (withIce) "正常冰" else "去冰"} ${cupSize}): ${frame.joinToString(" ") { "%02X".format(it) }}"
        )
        return sendCommand(frame)
    }

    fun sendAdminCommand(code: Int): Boolean {
        // 7字节协议管理员指令：FF [code] 00 00 00 00 FE
        // 例如：0x03=一键清洗，0x04=清洗停止，0x05=去皮，0x06=称重
        val frame = buildFrame(code and 0xFF, 0, 0, 0)
        DebugLogger.i(
            "HardwareManager",
            "发送管理员指令 CMD=0x${(code and 0xFF).toString(16).uppercase()}: ${frame.joinToString(" ") { "%02X".format(it) }}"
        )
        return sendCommand(frame)
    }

    fun sendContinueCommand(): Boolean {
        // 7字节协议：继续制作 FF 07 00 00 00 00 FE（独立指令，不作为管理员子命令）
        val frame = buildFrame(0x07, 0, 0, 0, 0)
        DebugLogger.i("HardwareManager", "发送继续制作指令: ${frame.joinToString(" ") { "%02X".format(it) }}")
        return sendCommand(frame)
    }

    fun sendRestartCommand(): Boolean {
        // 7字节协议：重新制作 FF 08 00 00 00 00 FE（独立指令，不作为管理员子命令）
        val frame = buildFrame(0x08, 0, 0, 0, 0)
        DebugLogger.i("HardwareManager", "发送重新制作指令: ${frame.joinToString(" ") { "%02X".format(it) }}")
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
        DebugLogger.e("HardwareManager", "串口IO出错或已停止: ${e.message}", e, showToast = true)
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
}