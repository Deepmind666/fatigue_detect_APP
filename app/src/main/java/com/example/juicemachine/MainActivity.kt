package com.example.juicemachine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
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

    // 延迟初始化，避免在 Activity 尚未完全 attach 前访问系统服务导致崩溃
    private val sharedPreferences by lazy { applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    private val viewModelFactory by lazy {
        DrinkMenuViewModelFactory(
            (application as JuiceMachineApplication).repository,
            (application as JuiceMachineApplication).hardwareManager,
            (application as JuiceMachineApplication).orderRepository,
            sharedPreferences
        )
    }
    private val viewModel: DrinkMenuViewModel by viewModels { viewModelFactory }


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
        // 安装官方 SplashScreen（支持 Android 12+ 及回退库），并设置超时关闭避免卡住
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }
        // 条件退出：首内容或首图就绪则退出；并设 200ms 上限兜底
        lifecycleScope.launchWhenCreated {
            try {
                kotlinx.coroutines.delay(200)
            } finally {
                keepSplash = false
            }
        }
        
        // 先绘制首帧，权限请求延后到 onStart，避免冷启动卡在启动背景
        try {
            setContent {
                JuiceMachineTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 首帧进入或首图就绪时回调，立即释放 Splash
                            AppNavigation(viewModel = viewModel, onFirstContentReady = { keepSplash = false })
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            // 兜底：若组合初始化异常，渲染一个简易提示界面以避免“无法打开”
            com.example.juicemachine.util.DebugLogger.e("MainActivity", "Compose 初始化失败: ${e.message}", e, showToast = false)
            // 异常也确保关闭 Splash
            keepSplash = false
            setContent {
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.material3.Text(
                        text = "应用启动异常，请导出日志并联系维护人员",
                        modifier = androidx.compose.ui.Modifier.align(Alignment.Center)
                    )
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
        // 在可见后再请求权限，避免阻塞首帧
        checkAndRequestPermissions()
        // 连接逻辑已由 ViewModel 管理（初始化时触发），避免重复连接导致资源竞争或多次注册
    }
    override fun onDestroy() {
        super.onDestroy()
        // 无需注销USB权限广播（改为由 HardwareManager 统一管理）
    }
}