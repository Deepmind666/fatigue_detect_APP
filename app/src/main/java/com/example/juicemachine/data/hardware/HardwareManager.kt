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

    fun setOnStatusListener(listener: (String) -> Unit) {
        onStatusListener = listener
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
        // 7字节：0xFF + 5字节有效数据 + 0xFE
        val command = ByteArray(7)
        command[0] = 0xFF.toByte()
        
        // 正确的协议：
        // 0x02 = 正常冰制作指令
        // 0x03 = 去冰制作指令
        command[1] = if (withIce) 0x02.toByte() else 0x03.toByte()

        // 根据杯型计算实际配方量
        val waterAmount = when (cupSize) {
            "大杯" -> (recipe.water * 1.3).toInt() // 大杯增加30%
            else -> recipe.water // 中杯使用原配方
        }
        
        val juiceAmount = when (cupSize) {
            "大杯" -> (recipe.juice * 1.3).toInt() // 大杯增加30%
            else -> recipe.juice // 中杯使用原配方
        }

        // 根据STM32端期望：A、B、C、D分别代表4个通道的投放量
        // 需要根据juiceChannel决定哪个通道投放果汁，其他通道为0
        command[2] = (waterAmount and 0xFF).toByte() // materialA - 水量
        command[3] = if (recipe.juiceChannel == 1) (juiceAmount and 0xFF).toByte() else 0x00 // materialB - 果汁通道1
        command[4] = if (recipe.juiceChannel == 2) (juiceAmount and 0xFF).toByte() else 0x00 // materialC - 果汁通道2
        command[5] = if (recipe.juiceChannel == 3) (juiceAmount and 0xFF).toByte() else 0x00 // materialD - 果汁通道3
        command[6] = 0xFE.toByte()

        // 调试信息
        val iceStatus = if(withIce) "正常冰" else "去冰"
        val commandType = if(withIce) "0x02" else "0x03"
        val debugMessage = "制作指令: $cupSize $iceStatus ($commandType), 配方: 水=${waterAmount}g 果汁=${juiceAmount}g 通道=${recipe.juiceChannel}"
        
        Log.d("HardwareManager", debugMessage)
        Toast.makeText(context, debugMessage, Toast.LENGTH_LONG).show()

        sendCommand(command)
    }

    // Admin commands are identified by command code 0x01
    fun sendAdminCommand(commandCode: Int) {
        // 7字节：0xFF + 5字节有效数据 + 0xFE
        // 根据指令表：所有管理员指令都使用Type=0x01，具体功能码放在byte[2]位置
        val command = byteArrayOf(
            0xFF.toByte(),
            0x01.toByte(), // Type: 固定为管理员功能类型
            commandCode.toByte(), // Data1: 具体的功能码(0x00清洗, 0x01停水, 0x02测试, 0x04制作)
            0x00, // Data2
            0x00, // Data3
            0x00, // Data4
            0xFE.toByte()
        )

        // 添加管理员指令的调试信息
        val commandName = when (commandCode) {
            0x00 -> "一键清洗"
            0x01 -> "停止加水"
            0x02 -> "连接测试"
            0x04 -> "开始制作"
            else -> "未知指令"
        }

        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, "管理员指令: $commandName (0x01-0x${commandCode.toString(16).uppercase()})", Toast.LENGTH_LONG).show()
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
        // Handle incoming data if needed
    }

    override fun onRunError(e: Exception) {
        Log.e("HardwareManager", "Serial port run error", e)
    }
} 