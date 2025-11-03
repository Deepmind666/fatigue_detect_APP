package com.example.juicemachine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juicemachine.ui.theme.FreshOrange
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import android.os.SystemClock
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerCleanScreen(
    onClean: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    // 新增：无操作超时返回广告页
    onTimeoutToAds: () -> Unit = {},
    timeoutMs: Long = 60_000L
) {
    var showConfirmClean by remember { mutableStateOf(false) }
    var showConfirmStop by remember { mutableStateOf(false) }
    // 新增：清洗活动状态，用于暂停屏保计时
    var isCleaningActive by remember { mutableStateOf(false) }

    // 新增：全局交互监听与超时
    var lastInteraction by remember { mutableStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(timeoutMs, isCleaningActive) {
        while (true) {
            delay(1000)
            val now = SystemClock.uptimeMillis()
            // 清洗期间暂停屏保：持续重置交互时间
            if (isCleaningActive) {
                lastInteraction = now
                continue
            }
            if (now - lastInteraction >= timeoutMs) {
                onTimeoutToAds()
                break
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("客户管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FreshOrange,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        // 包裹一层 Box 用于捕获任意触摸事件以重置计时
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            lastInteraction = SystemClock.uptimeMillis()
                            try { tryAwaitRelease() } finally { lastInteraction = SystemClock.uptimeMillis() }
                        },
                        onTap = { lastInteraction = SystemClock.uptimeMillis() },
                        onLongPress = { lastInteraction = SystemClock.uptimeMillis() },
                        onDoubleTap = { lastInteraction = SystemClock.uptimeMillis() }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 美观的清洗按钮
                Button(
                    onClick = { showConfirmClean = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FreshOrange
                    )
                ) {
                    Text(
                        "一键清洗",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 美观的停止按钮
                Button(
                    onClick = { showConfirmStop = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text(
                        "清洗停止",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // 确认弹窗：一键清洗
    if (showConfirmClean) {
        AlertDialog(
            onDismissRequest = { showConfirmClean = false },
            title = { Text("确认清洗") },
            text = { Text("确定要执行一键清洗吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmClean = false
                    // 点击确定开始清洗 -> 暂停屏保计时
                    isCleaningActive = true
                    onClean()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClean = false }) { Text("取消") }
            }
        )
    }

    // 确认弹窗：清洗停止
    if (showConfirmStop) {
        AlertDialog(
            onDismissRequest = { showConfirmStop = false },
            title = { Text("确认停止") },
            text = { Text("确定要停止清洗吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmStop = false
                    // 点击停止 -> 恢复屏保计时（从当前时刻重新开始15秒）
                    isCleaningActive = false
                    lastInteraction = SystemClock.uptimeMillis()
                    onStop()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmStop = false }) { Text("取消") }
            }
        )
    }
}