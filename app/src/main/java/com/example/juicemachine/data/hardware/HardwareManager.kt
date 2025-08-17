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
import java.io.IOException
import android.app.PendingIntent
import android.content.Intent
import android.widget.Toast

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

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private var usbIoManager: SerialInputOutputManager? = null
    // 多端口兼容（双USB-TTL场景）：全部监听，写入广播
    private val openedPorts: MutableList<UsbSerialPort> = mutableListOf()
    private val ioManagers: MutableList<SerialInputOutputManager> = mutableListOf()
    private var manualReadJob: Job? = null
    private val manualReadJobs: MutableList<Job> = mutableListOf()
    // 二进制流缓冲，用于严格按7字节帧解析（FF ...... FE）
    private val binaryRxBuffer: MutableList<Byte> = ArrayList(2048)
    var isConnected: Boolean = false
        private set
    private var onStatusListener: ((String) -> Unit)? = null
    private var onWeightAnomalyListener: ((WeightAnomalyData) -> Unit)? = null
    // 不再需要previousTail，因为我们现在使用byteBuffer来累积所有数据
    
    // 新增：数据缓冲区，用于显示接收到的所有数据
    private val dataBuffer = StringBuilder()
    private val maxBufferSize = 1000 // 最大缓冲区大小
    
    // 新增：字节缓冲区，用于存储接收到的原始字节数据
    private val byteBuffer = ArrayList<Byte>()
    private val maxByteBufferSize = 1024 // 最大字节缓冲区大小

    fun setOnStatusListener(listener: (String) -> Unit) {
        onStatusListener = listener
    }

    fun setOnWeightAnomalyListener(listener: (WeightAnomalyData) -> Unit) {
        onWeightAnomalyListener = listener
        android.util.Log.e("HardwareManager", "=== 重量异常监听器已设置 ===")
        Log.d("HardwareManager", "重量异常监听器已设置")
    }
    
    // 新增：获取数据缓冲区内容
    fun getDataBuffer(): String {
        return dataBuffer.toString()
    }
    
    // 新增：清空数据缓冲区
    fun clearDataBuffer() {
        dataBuffer.clear()
    }
    
    // 新增：测试数据接收
    fun testDataReceive() {
        val testData = byteArrayOf(0xFF.toByte(), 0x09.toByte(), 0x00, 0x00, 0x00, 0x00, 0xFE.toByte())
        android.util.Log.e("HardwareManager", "=== 测试数据接收 ===")
        android.util.Log.e("HardwareManager", "=== 测试数据: ${testData.joinToString(separator = " ") { "%02X".format(it) }} ===")
        
        // 直接更新数据缓冲区
        val newData = "测试: ${testData.size}字节 [${testData.joinToString(separator = " ") { "%02X".format(it) }}]\n"
        dataBuffer.append(newData)
        if (dataBuffer.length > maxBufferSize) {
            dataBuffer.delete(0, dataBuffer.length - maxBufferSize)
        }
        
        // 直接调用onNewData
        onNewData(testData)
        
        // 删除Toast提示：不再显示测试数据发送确认
    }
    
    // 新增：手动添加测试数据到缓冲区
    fun addTestDataToBuffer() {
        val testData = "手动测试: 7字节 [FF 09 00 00 00 00 FE]\n"
        dataBuffer.append(testData)
        if (dataBuffer.length > maxBufferSize) {
            dataBuffer.delete(0, dataBuffer.length - maxBufferSize)
        }
        android.util.Log.e("HardwareManager", "=== 手动添加测试数据到缓冲区 ===")
        android.util.Log.e("HardwareManager", "=== 当前缓冲区长度: ${dataBuffer.length} ===")
        
        // 删除Toast提示：不再显示手动添加测试数据到缓冲区的提示
    }
    
    // 新增：强制触发中断处理弹窗
    fun forceTriggerDialog() {
        android.util.Log.e("HardwareManager", "=== 强制触发中断处理弹窗(请求) ===")
        
        scope.launch(Dispatchers.Main) {
            // 删除Toast：强制触发提示改为仅日志
            
            // 检查监听器是否存在
            if (onWeightAnomalyListener != null) {
                android.util.Log.e("HardwareManager", "=== 监听器存在，准备触发（可连续） ===")
                try {
                    onWeightAnomalyListener?.invoke(
                        WeightAnomalyData(
                            currentWeight = 0,
                            expectedWeight = 0,
                            severity = WeightAnomalySeverity.MEDIUM,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    android.util.Log.e("HardwareManager", "=== 触发监听器成功 ===")
                } catch (e: Exception) {
                    android.util.Log.e("HardwareManager", "=== 强制触发失败: ${e.message} ===")
                }
            } else {
                android.util.Log.e("HardwareManager", "=== 监听器为空，无法强制触发 ===")
            }
        }
    }
    
    // 新增：测试异常检测逻辑
    fun testAnomalyDetection() {
        android.util.Log.e("HardwareManager", "=== 测试异常检测逻辑 ===")
        
        // 直接触发监听器，不依赖检测逻辑
        android.util.Log.e("HardwareManager", "=== 直接触发监听器 ===")
        if (onWeightAnomalyListener != null) {
            android.util.Log.e("HardwareManager", "=== 监听器不为空，调用监听器 ===")
            onWeightAnomalyListener?.invoke(
                WeightAnomalyData(
                    currentWeight = 0,
                    expectedWeight = 0,
                    severity = WeightAnomalySeverity.MEDIUM,
                    timestamp = System.currentTimeMillis()
                )
            )
            android.util.Log.e("HardwareManager", "=== 监听器调用完成 ===")
        } else {
            android.util.Log.e("HardwareManager", "=== 监听器为空，无法触发 ===")
        }
    }

    // 保持简单：不再暴露接收HEX监听，避免外部依赖

    companion object {
        const val USB_PERMISSION_ACTION = "com.android.example.USB_PERMISSION"
    }

    fun connect(onStatus: (String) -> Unit) {
        Log.d("HardwareManager", "connect called")
        // 删除Toast：开始连接提示改为仅日志
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        Log.d("HardwareManager", "检测到 ${availableDrivers.size} 个驱动")
        
        if (availableDrivers.isEmpty()) {
            isConnected = false
            Log.e("HardwareManager", "未检测到设备")
            // 删除Toast：未检测到USB设备
            onStatus("未检测到设备")
            onStatusListener?.invoke("未检测到设备")
            return
        }
        
        // 为所有设备申请权限
        var pending = 0
        availableDrivers.forEach { drv ->
            val dev = drv.device
            Log.d("HardwareManager", "检查设备权限: ${dev.deviceId}, 已有权限: ${usbManager.hasPermission(dev)}")
            if (!usbManager.hasPermission(dev)) {
                val pi = PendingIntent.getBroadcast(context, 0, Intent(USB_PERMISSION_ACTION), PendingIntent.FLAG_IMMUTABLE)
                usbManager.requestPermission(dev, pi)
                pending++
                Log.d("HardwareManager", "请求设备权限: ${dev.deviceId}")
            }
        }
        if (pending > 0) {
            isConnected = false
            Log.d("HardwareManager", "等待 $pending 个设备授权")
            onStatus("等待USB授权($pending)")
            onStatusListener?.invoke("等待USB授权($pending)")
            return
        }
        
        // 打开所有端口并监听
        openedPorts.clear()
        ioManagers.clear()
        manualReadJobs.forEach { it.cancel() }
        manualReadJobs.clear()
        
        availableDrivers.forEach { drv ->
            val dev = drv.device
            val conn = usbManager.openDevice(dev)
            Log.d("HardwareManager", "尝试打开设备: ${dev.deviceId}, 连接结果: ${conn != null}")
            
            if (conn != null) {
                drv.ports.forEachIndexed { idx, port ->
                    try {
                        port.open(conn)
                        port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                        try {
                            port.setDTR(true)
                            port.setRTS(true)
                            port.purgeHwBuffers(true, true)
                        } catch (_: IOException) {}
                        val mgr = SerialInputOutputManager(port, this)
                        mgr.start()
                        openedPorts.add(port)
                        ioManagers.add(mgr)
                        
                        // 删除Toast：SerialInputOutputManager启动信息仅记录日志
                        // 每个端口加一条手动读取兜底
                        manualReadJobs.add(startManualReaderFor(port))
                        
                        // 显示连接成功信息
                        scope.launch(Dispatchers.Main) {
                            // 删除Toast：改为仅日志
                            // 位置：连接每个端口时
                            // 替换原有的 Toast.makeText(context, "端口${idx}连接成功", Toast.LENGTH_SHORT).show()
                        }
                        Log.d("HardwareManager", "已打开设备${dev.deviceId}端口#$idx")
                    } catch (e: Exception) {
                        Log.e("HardwareManager", "打开端口失败 设备${dev.deviceId}#$idx", e)
                    }
                }
            }
        }
        
        // 用第一个端口作为默认写口，读口为全部
        serialPort = openedPorts.firstOrNull()
        isConnected = openedPorts.isNotEmpty()
        
        Log.d("HardwareManager", "连接结果: isConnected=$isConnected, openedPorts.size=${openedPorts.size}, serialPort=${serialPort != null}")
        
        if (isConnected) {
            // 启动手动读取兜底，避免监听器异常导致收不到数据
            startManualReader()
            onStatus("已连接(${openedPorts.size})")
            onStatusListener?.invoke("已连接(${openedPorts.size})")
            // 保留关键提示：如需仅在UI显示连接状态，可将此Toast改为状态回调
            // Toast.makeText(context, "USB串口已连接成功(${openedPorts.size})", Toast.LENGTH_SHORT).show()
        } else {
            onStatus("无法打开连接")
            onStatusListener?.invoke("无法打开连接")
        }
    }

    fun makeJuice(recipe: Recipe, cupSize: String, withIce: Boolean) {
        val command = ByteArray(7)
        command[0] = 0xFF.toByte()

        // 1. 指令码根据冰度决定: 0x01=正常冰, 0x02=去冰
        command[1] = if (withIce) 0x01.toByte() else 0x02.toByte()

        // 2. 根据新的大杯计算逻辑调整配方
        val waterAmount = when (cupSize) {
            "大杯" -> recipe.water + 30
            else -> recipe.water
        }
        
        val juiceAmount = when (cupSize) {
            "大杯" -> recipe.juice + 70
            else -> recipe.juice
        }

        // 3. 填充配方数据
        command[2] = (waterAmount and 0xFF).toByte()
        command[3] = if (recipe.juiceChannel == 1) (juiceAmount and 0xFF).toByte() else 0x00
        command[4] = if (recipe.juiceChannel == 2) (juiceAmount and 0xFF).toByte() else 0x00
        command[5] = if (recipe.juiceChannel == 3) (juiceAmount and 0xFF).toByte() else 0x00
        command[6] = 0xFE.toByte()

        val iceStatus = if(withIce) "正常冰(0x01)" else "去冰(0x02)"
        val debugMessage = "制作指令: $cupSize $iceStatus, 配方: 水=${waterAmount}g 果汁=${juiceAmount}g 通道=${recipe.juiceChannel}"
        
        Log.d("HardwareManager", debugMessage)
        // 删除Toast：制作指令提示

        sendCommand(command)
    }

    // 4. 修正管理员指令码: 0x03=清洗, 0x04=停止, 0x05=去皮, 0x06=称重
    fun sendAdminCommand(commandCode: Int) {
        val command = byteArrayOf(
            0xFF.toByte(),
            commandCode.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0xFE.toByte()
        )

        val commandName = when (commandCode) {
            0x03 -> "管理员清洗"
            0x04 -> "管理员停止"
            0x05 -> "管理员去皮"
            0x06 -> "管理员称重"
            else -> "未知管理员指令"
        }

        // 删除Toast：管理员指令提示

        sendCommand(command)
    }



    private fun sendCommand(data: ByteArray) {
        Log.d("HardwareManager", "sendCommand called, isConnected=$isConnected, serialPort=${serialPort != null}, openedPorts.size=${openedPorts.size}")
        
        if (serialPort == null || !isConnected) {
            Log.e("HardwareManager", "Serial port not available.")
            scope.launch(Dispatchers.Main) {
                // 保留关键错误提示逻辑，但改为状态回调与日志
            android.util.Log.w("HardwareManager", "串口未连接，无法发送")
            onStatusListener?.invoke("串口未连接，无法发送")
            // 如需用户可见提示，请在UI层展示Snackbar或非阻塞提示
            }
            // 仅提示，不修改连接状态文案，避免UI误判
            return
        }
        scope.launch {
            try {
                val hexString = data.joinToString(separator = " ") { "%02X".format(it) }
                Log.d("HardwareManager", "Sending command: $hexString")
                // 删除Toast：发送指令内容的提示
                // 修复：优先使用多端口广播，否则使用默认端口
                if (openedPorts.isNotEmpty()) {
                    Log.d("HardwareManager", "使用多端口发送，端口数量: ${openedPorts.size}")
                    openedPorts.forEachIndexed { index, p ->
                        try { 
                            val result = p.write(data, 2000)
                            Log.d("HardwareManager", "端口$index 写入结果: $result")
                        } catch (e: Exception) { 
                            Log.e("HardwareManager", "端口$index 写入失败", e) 
                        }
                    }
                } else if (serialPort != null) {
                    Log.d("HardwareManager", "使用单端口发送")
                    val result = serialPort?.write(data, 2000)
                    Log.d("HardwareManager", "单端口写入结果: $result")
                } else {
                    Log.e("HardwareManager", "没有可用的串口")
                    scope.launch(Dispatchers.Main) {
                        // 删除非关键提示：没有可用的串口（由UI轮询/状态文案体现）
            onStatusListener?.invoke("没有可用的串口")
                    }
                    return@launch
                }
                // 删除Toast：写入完成提示
            } catch (e: Exception) {
                Log.e("HardwareManager", "Error writing to serial port", e)
                // 删除Toast：写入失败提示，仅保留日志
                // 不修改连接状态文案，避免UI显示未连接
                // 遇到 rc=-1 等典型错误时，尝试自动重连一次后重试写入
                val shouldRetry = e.message?.contains("rc=-1") == true || e is IOException
                if (shouldRetry && attemptReconnect()) {
                    try {
                        val hexString = data.joinToString(separator = " ") { "%02X".format(it) }
                        Log.d("HardwareManager", "重连成功后重试发送: $hexString")
                        if (openedPorts.isNotEmpty()) {
                            openedPorts.forEach { p ->
                                try { p.write(data, 2000) } catch (_: Exception) {}
                            }
                        } else if (serialPort != null) {
                            serialPort?.write(data, 2000)
                        }
                        // 删除Toast：重连后写入完成提示
                    } catch (ex: Exception) {
                        Log.e("HardwareManager", "重连后写入仍失败", ex)
                    }
                }
            }
        }
    }

    private fun attemptReconnect(): Boolean {
        return try {
            Log.w("HardwareManager", "尝试重连串口…")
            // 修复：清理所有端口和IO管理器
            ioManagers.forEach { m ->
                try { m.stop() } catch (_: Exception) {}
            }
            openedPorts.forEach { p ->
                try { p.close() } catch (_: Exception) {}
            }
            manualReadJob?.cancel()
            manualReadJobs.forEach { it.cancel() }
            usbIoManager?.stop()
            serialPort?.close()
            
            // 重新初始化多端口连接
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            if (availableDrivers.isEmpty()) return false
            
            openedPorts.clear()
            ioManagers.clear()
            manualReadJobs.clear()
            
            availableDrivers.forEach { drv ->
                val dev = drv.device
                val conn = usbManager.openDevice(dev)
                if (conn != null) {
                    drv.ports.forEachIndexed { idx, port ->
                        try {
                            port.open(conn)
                            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                            try {
                                port.setDTR(true)
                                port.setRTS(true)
                                port.purgeHwBuffers(true, true)
                            } catch (_: IOException) {}
                            val mgr = SerialInputOutputManager(port, this)
                            mgr.start()
                            openedPorts.add(port)
                            ioManagers.add(mgr)
                            manualReadJobs.add(startManualReaderFor(port))
                            Log.d("HardwareManager", "重连成功: 设备${dev.deviceId}端口#$idx")
                        } catch (e: Exception) {
                            Log.e("HardwareManager", "重连端口失败 设备${dev.deviceId}#$idx", e)
                        }
                    }
                }
            }
            
            serialPort = openedPorts.firstOrNull()
            isConnected = openedPorts.isNotEmpty()
            if (isConnected) {
                onStatusListener?.invoke("已连接(${openedPorts.size})")
                true
            } else {
                false
            }
        } catch (ex: Exception) {
            Log.e("HardwareManager", "重连失败", ex)
            isConnected = false
            false
        }
    }

    fun disconnect() {
        try {
            ioManagers.forEach { m ->
                try { m.stop() } catch (_: Exception) {}
            }
            openedPorts.forEach { p ->
                try { p.close() } catch (_: Exception) {}
            }
            manualReadJob?.cancel()
            manualReadJobs.forEach { it.cancel() }
            usbIoManager?.stop()
            serialPort?.close()
        } catch (e: Exception) {
            Log.e("HardwareManager", "Error disconnecting", e)
        } finally {
            ioManagers.clear()
            openedPorts.clear()
            manualReadJob = null
            manualReadJobs.clear()
            serialPort = null
            usbIoManager = null
            // 清空字节缓冲区
            byteBuffer.clear()
            isConnected = false
        }
    }

    private fun startManualReader() {
        val port = serialPort ?: return
        manualReadJob?.cancel()
        manualReadJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(256)
            while (isConnected && serialPort === port) {
                try {
                    val len = port.read(buffer, 200)
                    if (len > 0) {
                        val data = buffer.copyOf(len)
                        onNewData(data)
                    }
                } catch (e: Exception) {
                    Log.e("HardwareManager", "manual read error", e)
                    break
                }
            }
        }
    }

    private fun startManualReaderFor(target: UsbSerialPort): Job {
        return scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(256)
            while (isConnected && openedPorts.contains(target)) {
                try {
                    val len = target.read(buffer, 200)
                    if (len > 0) {
                        val data = buffer.copyOf(len)
                        android.util.Log.e("HardwareManager", "=== 手动读取: ${len}字节 ===")
                        onNewData(data)
                    }
                } catch (e: Exception) {
                    Log.e("HardwareManager", "manual read error on port", e)
                    break
                }
            }
        }
    }

    // 新增：批量数据缓冲 - 合并短时间内的数据
    private val batchBuffer = mutableListOf<Byte>()
    private var lastBatchTime = 0L
    private val batchTimeout = 300L // 300ms内的数据合并为一个批次

    override fun onNewData(data: ByteArray) {
        // 强制输出调试信息，确保能看到接收数据
        android.util.Log.e("HardwareManager", "=== onNewData被调用，数据长度: ${data.size} ===")
        android.util.Log.e("HardwareManager", "=== 原始数据: ${data.joinToString(separator = " ") { "%02X".format(it) }} ===")
        
        val currentTime = System.currentTimeMillis()
        
        // 将数据添加到批量缓冲区
        batchBuffer.addAll(data.toList())
        
        // 如果是第一批数据，或者距离上次数据超过批次超时时间，则处理批次
        if (lastBatchTime == 0L || (currentTime - lastBatchTime) > batchTimeout) {
            // 处理批次数据
            processBatchData()
            lastBatchTime = currentTime
        }
        
        // 检查监听器是否已设置
        if (onWeightAnomalyListener != null) {
            android.util.Log.e("HardwareManager", "=== 监听器已设置，可以处理异常 ===")
        } else {
            android.util.Log.e("HardwareManager", "=== 监听器未设置，无法处理异常 ===")
        }
        
        // 处理接收到的数据
        if (data.isNotEmpty()) {
            // 将新数据追加到字节缓冲区
            byteBuffer.addAll(data.toList())
            if (byteBuffer.size > maxByteBufferSize) {
                byteBuffer.subList(0, byteBuffer.size - maxByteBufferSize).clear()
            }
            
            // 获取当前字节缓冲区的所有数据
            val currentBuffer = byteBuffer.toByteArray()
            val hexString = currentBuffer.joinToString(separator = " ") { "%02X".format(it) }
            android.util.Log.e("HardwareManager", "=== 当前缓冲区数据: $hexString ===")
            Log.d("HardwareManager", "接收到数据: $hexString")
            
            // 尝试解析为文本响应
            val textResponse = String(currentBuffer, Charsets.UTF_8).trim()
            val textUpper = textResponse.uppercase()
            val latin1Upper = try { String(currentBuffer, Charsets.ISO_8859_1).uppercase() } catch (_: Exception) { "" }
            
            android.util.Log.e("HardwareManager", "=== 文本解析: '$textResponse' ===")
            android.util.Log.e("HardwareManager", "=== 大写文本: '$textUpper' ===")

            when {
                textResponse.contains("TEST_OK") -> {
                    Log.d("HardwareManager", "收到连接测试确认: $textResponse")
                    scope.launch(Dispatchers.Main) {
                        // 删除非关键提示：设备连接测试成功（由UI状态体现）
                    }
                }
                textResponse.contains("MAKE_COMPLETE") -> {
                    Log.d("HardwareManager", "收到制作完成确认: $textResponse")
                    // 删除Toast：饮品制作完成提示
                }
                else -> {
                    Log.d("HardwareManager", "收到未知数据: $textResponse")
                }
            }

            // 更健壮的异常检测：
            // 1) 二进制严格解析 0xFF ...... 0xFE, 统计指令码 == 0x09 的帧数
            // 2) 针对当前累积缓冲，放宽匹配：只要命令位是 0x09，忽略中间4字节内容
            android.util.Log.e("HardwareManager", "=== 开始异常检测 ===")
            val binCount = parseBinaryFrames(data)
            val genericDetected = containsAnomalyFrameInBuffer(currentBuffer)
            val detected = (binCount > 0) || genericDetected

            android.util.Log.e("HardwareManager", "=== 二进制解析命中数量: $binCount, 放宽匹配: $genericDetected, detected=$detected ===")

            if (detected) {
                android.util.Log.e("HardwareManager", "=== 检测到0x09异常帧，立即触发弹窗 ===")
                scope.launch(Dispatchers.Main) {
                    // 删除Toast：异常检测提示与监听器调用结果提示

                    if (onWeightAnomalyListener != null) {
                        android.util.Log.e("HardwareManager", "=== 调用监听器触发弹窗 ===")
                        try {
                            onWeightAnomalyListener?.invoke(
                                WeightAnomalyData(
                                    currentWeight = 0,
                                    expectedWeight = 0,
                                    severity = WeightAnomalySeverity.MEDIUM,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("HardwareManager", "=== 监听器调用失败: ${e.message} ===")
                        }
                    } else {
                        android.util.Log.e("HardwareManager", "=== 监听器为空 ===")
                    }
                }
            } else {
                android.util.Log.e("HardwareManager", "=== 未检测到0x09异常帧 ===")
            }

            // 调试信息（简化）
            Log.d("HardwareManager", "异常检测: binCount=$binCount, genericDetected=$genericDetected, textLength=${textResponse.length}")

            // 不再需要previousTail，因为我们现在使用byteBuffer来累积所有数据
        }
    }

    private fun parseBinaryFrames(chunk: ByteArray): Int {
        // 追加到缓冲
        for (b in chunk) binaryRxBuffer.add(b)
        var frameCount = 0
        
        // 解析循环
        var i = 0
        while (i <= binaryRxBuffer.size - 7) {
            // 搜索起始0xFF
            while (i < binaryRxBuffer.size && binaryRxBuffer[i] != 0xFF.toByte()) i++
            if (i > binaryRxBuffer.size - 7) break
            // 检查结束位
            if (binaryRxBuffer[i + 6] == 0xFE.toByte()) {
                val cmd = binaryRxBuffer[i + 1]
                // 检测0x09异常帧
                if (cmd == 0x09.toByte()) {
                    frameCount++
                    Log.d("HardwareManager", "严格二进制解析检测到09异常帧")
                }
                // 移除已消费的帧（含之前杂散字节）
                binaryRxBuffer.subList(0, i + 7).clear()
                i = 0
                continue
            }
            // 若不是有效帧，继续向后
            i++
        }
        // 限制缓冲大小，仅保留尾部少量字节
        if (binaryRxBuffer.size > 64) {
            val keep = binaryRxBuffer.takeLast(64)
            binaryRxBuffer.clear()
            binaryRxBuffer.addAll(keep)
        }
        return frameCount
    }

    // 保留简单API

    // 在数据块中查找 FF 09 00 00 00 00 FE 子序列（支持一块中多次出现）
    private fun containsAnomalyFrame(buffer: ByteArray): Boolean {
        if (buffer.size < 7) return false
        val target = byteArrayOf(0xFF.toByte(), 0x09.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFE.toByte())
        for (i in 0..buffer.size - target.size) {
            var matched = true
            for (j in target.indices) {
                if (buffer[i + j] != target[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }
        return false
    }

    // 正确的检测方法：检测到包头FF后，检测后面5位直到FE结束
    private fun containsAnomalyFrameInBuffer(buffer: ByteArray): Boolean {
        if (buffer.size < 7) return false
        
        for (i in 0..buffer.size - 7) {
            // 检测到包头FF
            if (buffer[i] == 0xFF.toByte()) {
                // 检测第二个字节是否为09
                if (buffer[i + 1] == 0x09.toByte()) {
                    // 检测中间4个字节是否都为00
                    if (buffer[i + 2] == 0x00.toByte() && 
                        buffer[i + 3] == 0x00.toByte() && 
                        buffer[i + 4] == 0x00.toByte() && 
                        buffer[i + 5] == 0x00.toByte()) {
                        // 检测结束字节是否为FE
                        if (buffer[i + 6] == 0xFE.toByte()) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }
    
    // 使用字节缓冲区检测异常帧
    private fun containsAnomalyFrameInByteBuffer(): Boolean {
        if (byteBuffer.size < 7) return false
        
        for (i in 0..byteBuffer.size - 7) {
            // 检测到包头FF
            if (byteBuffer[i] == 0xFF.toByte()) {
                // 检测第二个字节是否为09
                if (byteBuffer[i + 1] == 0x09.toByte()) {
                    // 检测中间4个字节是否都为00
                    if (byteBuffer[i + 2] == 0x00.toByte() && 
                        byteBuffer[i + 3] == 0x00.toByte() && 
                        byteBuffer[i + 4] == 0x00.toByte() && 
                        byteBuffer[i + 5] == 0x00.toByte()) {
                        // 检测结束字节是否为FE
                        if (byteBuffer[i + 6] == 0xFE.toByte()) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun countAnomalyFrames(buffer: ByteArray): Int {
        if (buffer.size < 7) return 0
        val target = byteArrayOf(0xFF.toByte(), 0x09.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFE.toByte())
        var count = 0
        var i = 0
        while (i <= buffer.size - target.size) {
            var matched = true
            var j = 0
            while (j < target.size) {
                if (buffer[i + j] != target[j]) { matched = false; break }
                j++
            }
            if (matched) {
                count++
                i += target.size
            } else {
                i++
            }
        }
        return count
    }

    // 一些下位机会以ASCII打印形式输出（例如调试串口），此时日志中会出现 "FF 09 00 00 00 00 FE" 字样
    private fun containsAnomalyAscii(hexLog: String): Boolean {
        return hexLog.contains("FF 09 00 00 00 00 FE")
    }
    private fun countAnomalyAscii(hexLog: String): Int {
        if (hexLog.isEmpty()) return 0
        val regex = Regex("FF\\s*09\\s*00\\s*00\\s*00\\s*00\\s*FE")
        return regex.findAll(hexLog).count()
    }

    // 对UTF-8文本内容做大小写与空白无关的匹配
    private fun containsAnomalyAsciiText(text: String): Boolean {
        if (text.isEmpty()) return false
        val spacedHex = Regex("FF\\s*09\\s*00\\s*00\\s*00\\s*00\\s*FE")
        return spacedHex.containsMatchIn(text)
    }

    // 将任意包含十六进制字符的字符串转成字节流（忽略非hex字符）
    private fun hexStringToBytes(text: String): ByteArray {
        if (text.isEmpty()) return ByteArray(0)
        val cleaned = buildString {
            for (ch in text) {
                if ((ch in '0'..'9') || (ch in 'A'..'F')) append(ch)
            }
        }
        if (cleaned.length < 14) return ByteArray(0) // 最少7字节14个hex
        val out = ArrayList<Byte>(cleaned.length / 2)
        var i = 0
        while (i + 1 < cleaned.length) {
            try {
                val b = cleaned.substring(i, i + 2).toInt(16).toByte()
                out.add(b)
            } catch (_: Exception) { /* ignore */ }
            i += 2
        }
        return out.toByteArray()
    }



    // 发送继续制作指令
    fun sendContinueCommand() {
        val command = byteArrayOf(
            0xFF.toByte(),
            0x07.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0xFE.toByte()
        )
        
        Log.d("HardwareManager", "发送继续制作指令: FF 07 00 00 00 00 FE")
        // 清除缓冲区
        byteBuffer.clear()
        // 删除Toast：发送继续制作指令提示
        sendCommand(command)
    }

    // 发送重新制作指令
    fun sendRestartCommand() {
        val command = byteArrayOf(
            0xFF.toByte(),
            0x08.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0xFE.toByte()
        )
        
        Log.d("HardwareManager", "发送重新制作指令: FF 08 00 00 00 00 FE")
        // 清除缓冲区
        byteBuffer.clear()
        // 删除Toast：发送重新制作指令提示
        sendCommand(command)
    }

    override fun onRunError(e: Exception) {
        Log.e("HardwareManager", "Serial port run error", e)
    }

    // 批次处理：将batchBuffer中的数据一次性写入dataBuffer
    private fun processBatchData() {
        if (batchBuffer.isEmpty()) return
        val bytes = batchBuffer.toByteArray()
        batchBuffer.clear()
        val hex = bytes.joinToString(" ") { "%02X".format(it) }
        val line = "批次接收: ${bytes.size}字节 [$hex]\n"
        dataBuffer.append(line)
        if (dataBuffer.length > maxBufferSize) {
            dataBuffer.delete(0, dataBuffer.length - maxBufferSize)
        }
        android.util.Log.e("HardwareManager", "=== 批次写入缓冲区: ${bytes.size} 字节，当前长度: ${dataBuffer.length} ===")
        
        // 将批次接收情况以低频Toast提示
        scope.launch(Dispatchers.Main) {
            // 删除非关键提示：批次接收信息改为日志
                Log.d("HardwareManager", "批次接收: ${bytes.size} 字节")
        }
    }
}