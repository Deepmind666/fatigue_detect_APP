package com.example.juicemachine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Build
import android.util.Log
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

    // 权限请求启动器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { (permission, isGranted) ->
            Log.d("MainActivity", "权限 $permission: ${if (isGranted) "已授权" else "被拒绝"}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查并请求必要权限
        checkAndRequestPermissions()
        
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

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // 检查相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        
        // 检查存储权限（根据Android版本）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用 READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 12 及以下使用 READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            Log.d("MainActivity", "请求权限: $permissionsToRequest")
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d("MainActivity", "所有权限已授权")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbPermissionReceiver)
    }
}