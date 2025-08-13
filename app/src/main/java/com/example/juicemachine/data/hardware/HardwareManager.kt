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
    var isConnected: Boolean = false
        private set
    private var onStatusListener: ((String) -> Unit)? = null
    private var onWeightAnomalyListener: ((WeightAnomalyData) -> Unit)? = null

    fun setOnStatusListener(listener: (String) -> Unit) {
        onStatusListener = listener
    }

    fun setOnWeightAnomalyListener(listener: (WeightAnomalyData) -> Unit) {
        onWeightAnomalyListener = listener
    }

    companion object {
        const val USB_PERMISSION_ACTION = "com.android.example.USB_PERMISSION"
    }

    fun connect(onStatus: (String) -> Unit) {
        Log.d("HardwareManager", "connect called")
        Toast.makeText(context, "开始连接USB设备", Toast.LENGTH_SHORT).show()
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            isConnected = false
            Log.e("HardwareManager", "未检测到设备")
            Toast.makeText(context, "未检测到USB设备", Toast.LENGTH_LONG).show()
            onStatus("未检测到设备")
            onStatusListener?.invoke("未检测到设备")
            return
        }

        val driver = availableDrivers[0]
        val device = driver.device
        // 动态USB权限请求
        if (!usbManager.hasPermission(device)) {
            val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(USB_PERMISSION_ACTION), PendingIntent.FLAG_IMMUTABLE)
            Log.d("HardwareManager", "请求USB权限，action=${USB_PERMISSION_ACTION}")
            Toast.makeText(context, "请求USB权限，请授权", Toast.LENGTH_LONG).show()
            usbManager.requestPermission(device, permissionIntent)
            isConnected = false
            onStatus("等待USB授权")
            onStatusListener?.invoke("等待USB授权")
            return
        }
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            isConnected = false
            Log.e("HardwareManager", "无法打开连接")
            Toast.makeText(context, "无法打开USB连接", Toast.LENGTH_LONG).show()
            onStatus("无法打开连接")
            onStatusListener?.invoke("无法打开连接")
            return
        }
        serialPort = driver.ports[0]
        serialPort?.open(connection)
        serialPort?.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        usbIoManager = SerialInputOutputManager(serialPort, this)
        usbIoManager?.start()
        isConnected = true
        Log.d("HardwareManager", "串口已连接")
        Toast.makeText(context, "USB串口已连接成功", Toast.LENGTH_SHORT).show()
        onStatus("已连接")
        onStatusListener?.invoke("已连接")
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
        Toast.makeText(context, debugMessage, Toast.LENGTH_LONG).show()

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

        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, "管理员指令: $commandName (0x${commandCode.toString(16).uppercase()})", Toast.LENGTH_LONG).show()
        }

        sendCommand(command)
    }



    private fun sendCommand(data: ByteArray) {
        if (serialPort == null || !isConnected) {
            Log.e("HardwareManager", "Serial port not available.")
            scope.launch(Dispatchers.Main) {
                Toast.makeText(context, "串口未连接，无法发送", Toast.LENGTH_SHORT).show()
            }
            onStatusListener?.invoke("串口未连接，无法发送指令")
            return
        }
        scope.launch {
            try {
                val hexString = data.joinToString(separator = " ") { "%02X".format(it) }
                Log.d("HardwareManager", "Sending command: $hexString")
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "发送指令: $hexString", Toast.LENGTH_LONG).show()
                }
                serialPort?.write(data, 2000)
            } catch (e: Exception) {
                Log.e("HardwareManager", "Error writing to serial port", e)
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "串口写入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
                onStatusListener?.invoke("串口写入失败: ${e.message}")
            }
        }
    }

    fun disconnect() {
        try {
            usbIoManager?.stop()
            serialPort?.close()
        } catch (e: Exception) {
            Log.e("HardwareManager", "Error disconnecting", e)
        } finally {
            serialPort = null
            usbIoManager = null
            scope.cancel()
        }
    }

    override fun onNewData(data: ByteArray) {
        // 处理接收到的数据
        if (data.isNotEmpty()) {
            val hexString = data.joinToString(separator = " ") { "%02X".format(it) }
            Log.d("HardwareManager", "接收到数据: $hexString")
            
            // 尝试解析为文本响应
            val textResponse = String(data, Charsets.UTF_8).trim()
            when {
                textResponse.contains("TEST_OK") -> {
                    Log.d("HardwareManager", "收到连接测试确认: $textResponse")
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "设备连接测试成功", Toast.LENGTH_SHORT).show()
                    }
                }
                textResponse.contains("MAKE_COMPLETE") -> {
                    Log.d("HardwareManager", "收到制作完成确认: $textResponse")
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "饮品制作完成", Toast.LENGTH_LONG).show()
                    }
                }
                // 检查是否为重量异常指令 FF 09 00 00 00 00 FE
                data.size == 7 && 
                data[0] == 0xFF.toByte() && data[1] == 0x09.toByte() && 
                data[2] == 0x00.toByte() && data[3] == 0x00.toByte() && 
                data[4] == 0x00.toByte() && data[5] == 0x00.toByte() && 
                data[6] == 0xFE.toByte() -> {
                    // 收到固定的重量异常指令，直接触发弹窗
                    Log.d("HardwareManager", "收到重量异常指令: FF 09 00 00 00 00 FE")
                    scope.launch(Dispatchers.Main) {
                        onWeightAnomalyListener?.invoke(WeightAnomalyData(
                            currentWeight = 0,
                            expectedWeight = 0,
                            severity = WeightAnomalySeverity.MEDIUM,
                            timestamp = System.currentTimeMillis()
                        ))
                        Toast.makeText(context, "检测到重量异常", Toast.LENGTH_LONG).show()
                    }
                }
                else -> {
                    Log.d("HardwareManager", "收到未知数据: $textResponse")
                }
            }
        }
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
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, "发送继续制作指令", Toast.LENGTH_SHORT).show()
        }
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
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, "发送重新制作指令", Toast.LENGTH_SHORT).show()
        }
        sendCommand(command)
    }

    override fun onRunError(e: Exception) {
        Log.e("HardwareManager", "Serial port run error", e)
    }
}