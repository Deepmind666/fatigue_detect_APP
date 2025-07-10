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

class HardwareManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : SerialInputOutputManager.Listener {

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private var usbIoManager: SerialInputOutputManager? = null

    fun connect(onStatus: (String) -> Unit) {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            onStatus("--°C")
            return
        }

        val driver = availableDrivers[0]
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            onStatus("无法打开连接")
            return
        }

        serialPort = driver.ports[0]
        serialPort?.open(connection)
        serialPort?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

        usbIoManager = SerialInputOutputManager(serialPort, this)
        usbIoManager?.start()
        onStatus("已连接")
    }

    fun makeJuice(recipe: Recipe, withIce: Boolean) {
        val command = ByteArray(7)
        command[0] = 0xFF.toByte()
        command[1] = if (withIce) 0x02.toByte() else 0x03.toByte()
        command[2] = recipe.water.toByte()
        command[3] = if (recipe.juiceChannel == 1) recipe.juice.toByte() else 0x00
        command[4] = if (recipe.juiceChannel == 2) recipe.juice.toByte() else 0x00
        command[5] = if (recipe.juiceChannel == 3) recipe.juice.toByte() else 0x00
        command[6] = 0xFE.toByte()
        sendCommand(command)
    }

    // Admin commands are identified by command code 0x01
    fun sendAdminCommand(commandCode: Int) {
        val command = byteArrayOf(
            0xFF.toByte(),
            0x01.toByte(),
            commandCode.toByte(),
            0x00,
            0x00,
            0x00,
            0xFE.toByte()
        )
        sendCommand(command)
    }

    private fun sendCommand(data: ByteArray) {
        if (serialPort == null) {
            Log.e("HardwareManager", "Serial port not available.")
            return
        }
        scope.launch {
            try {
                val hexString = data.joinToString(separator = " ") { "%02X".format(it) }
                Log.d("HardwareManager", "Sending command: $hexString")
                serialPort?.write(data, 2000)
            } catch (e: Exception) {
                Log.e("HardwareManager", "Error writing to serial port", e)
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