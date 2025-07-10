package com.example.juicemachine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.juicemachine.ui.AppNavigation
import com.example.juicemachine.ui.theme.JuiceMachineTheme
import com.example.juicemachine.ui.viewmodel.DrinkMenuViewModel
import com.example.juicemachine.ui.viewmodel.DrinkMenuViewModelFactory
import android.hardware.usb.UsbManager
import com.example.juicemachine.data.hardware.HardwareManager

class MainActivity : ComponentActivity() {

    private val viewModel: DrinkMenuViewModel by viewModels {
        DrinkMenuViewModelFactory(
            (application as JuiceMachineApplication).repository,
            (application as JuiceMachineApplication).hardwareManager
        )
    }

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == HardwareManager.USB_PERMISSION_ACTION) {
                android.util.Log.d("MainActivity", "USB权限广播收到，intent=$intent")
                synchronized(this) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        android.util.Log.d("MainActivity", "USB权限已授权，重新connect")
                        (application as JuiceMachineApplication).hardwareManager.connect { status ->
                            // 可根据需要更新UI
                        }
                    } else {
                        android.util.Log.e("MainActivity", "USB权限被拒绝")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JuiceMachineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
        // 注册USB权限广播接收器，action统一
        val filter = IntentFilter(HardwareManager.USB_PERMISSION_ACTION)
        registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbPermissionReceiver)
    }
}