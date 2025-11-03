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
 
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
 
import android.hardware.usb.UsbManager
import com.example.juicemachine.data.hardware.HardwareManager
import com.example.juicemachine.util.DebugLogger
import androidx.core.content.FileProvider
import android.annotation.SuppressLint

class MainActivity : ComponentActivity() {

    private val viewModel: DrinkMenuViewModel by viewModels {
        DrinkMenuViewModelFactory(
            (application as JuiceMachineApplication).repository,
            (application as JuiceMachineApplication).hardwareManager,
            (application as JuiceMachineApplication).orderRepository
        )
    }


    // 权限请求启动器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { (permission, isGranted) ->
            Log.d("MainActivity", "权限 $permission: ${if (isGranted) "已授权" else "被拒绝"}")
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavigation(viewModel = viewModel)
                    }
                }
            }
        }
        // 监听标题长按，导出日志
        val root = findViewById<android.view.View>(android.R.id.content)
        root.setOnLongClickListener {
            val file = DebugLogger.getLogFile()
            if (file == null || !file.exists()) {
                DebugLogger.w("MainActivity", "暂无日志可导出", showToast = false)
                return@setOnLongClickListener true
            }
            try {
                val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
                val share = Intent(Intent.ACTION_SEND)
                share.type = "text/plain"
                share.putExtra(Intent.EXTRA_STREAM, uri)
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(share, "导出诊断日志"))
            } catch (e: Exception) {
                DebugLogger.e("MainActivity", "导出失败: ${e.message}", e, showToast = false)
            }
            true
        }
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

    override fun onStart() {
        super.onStart()
        // 页面可见时主动连接串口
        (application as JuiceMachineApplication).hardwareManager.connect { status ->
            Log.d("MainActivity", "硬件状态: $status")
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        // 无需注销USB权限广播（改为由 HardwareManager 统一管理）
    }
}