package com.example.juicemachine.data.hardware

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Manages the connection and communication with the juice machine hardware via USB-Serial.
 * This class is a Singleton to ensure only one instance manages the hardware connection.
 */
class HardwareManager private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) : SerialInputOutputManager.Listener {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private var usbIoManager: SerialInputOutputManager? = null

    // Connection status exposed as a StateFlow for UI observation
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    companion object {
        private const val TAG = "HardwareManager"
        private const val WRITE_WAIT_MILLIS = 2000 // Timeout for write operations
        private const val BAUD_RATE = 115200 // Common baud rate, adjust if hardware requires differently

        // Singleton instance
        @Volatile
        private var INSTANCE: HardwareManager? = null

        fun getInstance(context: Context, scope: CoroutineScope): HardwareManager {
            return INSTANCE ?: HardwareManager(context.applicationContext, scope).also { INSTANCE = it }
        }
    }

    /**
     * Tries to connect to the first available USB-Serial device.
     */
    fun connect() {
        scope.launch(Dispatchers.IO) {
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            if (availableDrivers.isEmpty()) {
                Log.w(TAG, "No serial drivers available.")
                _isConnected.value = false
                return@launch
            }

            val driver = availableDrivers[0]
            val device = driver.device

            // Request permission if not already granted
            if (!usbManager.hasPermission(device)) {
                val permissionIntent = PendingIntent.getBroadcast(
                    context, 0, Intent("com.example.juicemachine.USB_PERMISSION"), PendingIntent.FLAG_IMMUTABLE
                )
                usbManager.requestPermission(device, permissionIntent)
                Log.i(TAG, "Requested USB permission.")
                return@launch
            }

            var connection: UsbDeviceConnection?
            try {
                connection = usbManager.openDevice(device)
                if (connection == null) {
                    Log.e(TAG, "Failed to open USB device.")
                    return@launch
                }

                serialPort = driver.ports[0] // Most devices have one port
                serialPort?.open(connection)
                serialPort?.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                usbIoManager = SerialInputOutputManager(serialPort, this@HardwareManager)
                usbIoManager?.start()

                _isConnected.value = true
                Log.i(TAG, "Serial port connected successfully.")

            } catch (e: IOException) {
                Log.e(TAG, "Error connecting to serial port", e)
                disconnect()
            }
        }
    }

    /**
     * Disconnects the serial port and stops the I/O manager.
     */
    fun disconnect() {
        scope.launch(Dispatchers.IO) {
            try {
                usbIoManager?.stop()
                serialPort?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error disconnecting serial port", e)
            } finally {
                serialPort = null
                usbIoManager = null
                _isConnected.value = false
                Log.i(TAG, "Serial port disconnected.")
            }
        }
    }

    /**
     * Sends a command to make juice with the specified ingredients.
     * Command format: FF 00 [ice] [juice] [water] 00 FE
     */
    fun sendMakeJuiceCommand(ice: Int, juice: Int, water: Int) {
        // Validate that values are within the allowed range (0-254)
        if (ice !in 0..254 || juice !in 0..254 || water !in 0..254) {
            Log.e(TAG, "Ingredient amount out of range (0-254). Ice: $ice, Juice: $juice, Water: $water")
            return
        }

        val command = byteArrayOf(
            0xFF.toByte(),              // Packet Header
            0x00.toByte(),              // User command
            ice.toByte(),               // Ice amount
            juice.toByte(),             // Juice amount
            water.toByte(),             // Water amount
            0x00.toByte(),              // Reserved byte
            0xFE.toByte()               // Packet Trailer
        )
        write(command)
    }

    /**
     * Sends the command for machine self-cleaning.
     * Command format: FF 01 00 00 00 00 FE
     */
    fun sendCleanCommand() {
        val command = byteArrayOf(
            0xFF.toByte(),              // Packet Header
            0x01.toByte(),              // Admin command
            0x00.toByte(),              // Data
            0x00.toByte(),              // Data
            0x00.toByte(),              // Data
            0x00.toByte(),              // Data
            0xFE.toByte()               // Packet Trailer
        )
        write(command)
    }

    /**
     * Private helper to write data to the serial port.
     */
    private fun write(data: ByteArray) {
        scope.launch(Dispatchers.IO) {
            if (serialPort == null || !_isConnected.value) {
                Log.w(TAG, "Cannot write, not connected.")
                return@launch
            }
            try {
                serialPort?.write(data, WRITE_WAIT_MILLIS)
                Log.d(TAG, "Wrote data: ${data.joinToString(" ") { String.format("%02X", it) }}")
            } catch (e: IOException) {
                Log.e(TAG, "Write error", e)
                // Optionally, trigger a reconnect or show an error state
            }
        }
    }

    // --- SerialInputOutputManager.Listener Implementation ---

    /**
     * Called when new data is received from the serial port.
     * (Currently just logs the data, can be expanded to parse responses from hardware)
     */
    override fun onNewData(data: ByteArray) {
        val hexString = data.joinToString(separator = " ") { "%02x".format(it) }
        Log.i(TAG, "Received data: $hexString")
        // Here you can parse responses from the juice machine
    }

    /**
     * Called when a communication error occurs.
     */
    override fun onRunError(e: Exception) {
        Log.e(TAG, "Serial IO Manager error", e)
        disconnect() // Disconnect on error
    }
} 