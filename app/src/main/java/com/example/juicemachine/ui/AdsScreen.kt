package com.example.juicemachine.ui

import android.util.Log
import android.os.SystemClock
import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val ADS_TAG = "AdsScreen"

// 公共数据模型：广告项（URI + 可选标题）
data class AdItem(val uri: String, val title: String? = null)

@Composable
fun AdsScreen(
    images: List<AdItem>,
    intervalMs: Long = 10_000L,
    onNavigateToUser: () -> Unit,
    // 首图加载成功时触发，用于释放启动页
    onFirstImageReady: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var currentIndex by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableStateOf(SystemClock.uptimeMillis()) }

    val data = remember(images) { if (images.isNotEmpty()) images else emptyList() }
    val gestureScope = rememberCoroutineScope()

    // 进入广告页 -> 沉浸式全屏；离开时恢复系统栏
    // 调整：首帧后轻微延迟再隐藏，避免部分设备在冷启动阶段全屏黑
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.let { act ->
            WindowCompat.setDecorFitsSystemWindows(act.window, false)
            val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // 首帧后再隐藏系统栏：具体隐藏动作在下面的 LaunchedEffect 中执行
        }
        onDispose {
            activity?.let { act ->
                val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(act.window, true)
            }
        }
    }
    // 首图就绪后隐藏系统栏（含 500ms 兜底），避免冷启动阶段的窗口切换导致白板/闪烁
    var barsHiddenOnce by remember { mutableStateOf(false) }
    var firstImageDisplayed by remember { mutableStateOf(false) }
    var notifiedSplash by remember { mutableStateOf(false) }
    LaunchedEffect(activity, firstImageDisplayed) {
        val act = activity ?: return@LaunchedEffect
        if (barsHiddenOnce) return@LaunchedEffect
        if (!firstImageDisplayed) {
            // 若首图尚未就绪，等待极短时间后仍隐藏一次，避免长时间不全屏
            kotlinx.coroutines.delay(500)
        }
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        barsHiddenOnce = true
    }
// 删除空行无符号标记
     // 自动轮播：按间隔切换到下一张，循环；按压或拖拽时暂停
     LaunchedEffect(data, isPaused, intervalMs) {
         if (data.isEmpty()) return@LaunchedEffect
         while (!isPaused) {
             kotlinx.coroutines.delay(intervalMs)
             if (isPaused) break
             if (data.size > 1) {
                 val next = (currentIndex + 1) % data.size
                 currentIndex = next
                 Log.d(ADS_TAG, "Auto-advance -> index=$next/${'$'}{data.size}")
             }
         }
     }

    // 每次索引变化时记录当前展示的图片地址
    LaunchedEffect(currentIndex, data) {
        val uri = data.getOrNull(currentIndex)?.uri
        Log.d(ADS_TAG, "Displaying index=$currentIndex uri=$uri")
    }

    // 轻微的 Ken Burns 动效
    val scale = remember { Animatable(1.0f) }
    LaunchedEffect(currentIndex, isPaused, data, intervalMs, firstImageDisplayed) {
        scale.snapTo(1.0f)
        // 冷启动优化：首图阶段不启用 Ken Burns 动效，减少首帧绘制开销
        if (firstImageDisplayed && !isPaused && data.isNotEmpty()) {
            scale.animateTo(
                targetValue = 1.03f,
                animationSpec = tween(durationMillis = (intervalMs * 0.95f).toInt(), easing = LinearEasing)
            )
        }
    }

    // 自动播放进度（用于底部细进度条）
    val progress = remember { Animatable(0f) }
    LaunchedEffect(currentIndex, isPaused, data, intervalMs) {
        progress.snapTo(0f)
        if (!isPaused && data.isNotEmpty()) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = (intervalMs * 0.98f).toInt(), easing = LinearEasing)
            )
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    fun deriveNameFromUri(uri: String): String {
        return try {
            val u = android.net.Uri.parse(uri)
            when (u.scheme) {
                "android.resource" -> u.lastPathSegment ?: uri
                else -> (u.path?.substringAfterLast('/') ?: uri)
            }
        } catch (_: Exception) {
            uri
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 背景改为应用主题背景，避免资源加载失败时整屏纯黑
            .background(MaterialTheme.colorScheme.background)
            // 点击进入点单页
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        lastInteraction = SystemClock.uptimeMillis()
                        val downAt = lastInteraction
                        Log.d(ADS_TAG, "onPress: pause autoplay at $downAt")
                        try {
                            tryAwaitRelease()
                        } finally {
                            isPaused = false
                            lastInteraction = SystemClock.uptimeMillis()
                            val upAt = lastInteraction
                            Log.d(ADS_TAG, "onRelease: resume autoplay, pressed ${upAt - downAt}ms")
                        }
                    },
                    onTap = {
                        lastInteraction = SystemClock.uptimeMillis()
                        Log.d(ADS_TAG, "onTap: navigate to user at $lastInteraction")
                        onNavigateToUser()
                    }
                )
            }
            // 左右拖动切换广告
            .pointerInput(data) {
                detectDragGestures(
                    onDragStart = {
                        isPaused = true
                    },
                    onDragEnd = {
                        isPaused = false
                    },
                    onDragCancel = {
                        isPaused = false
                    }
                ) { change, dragAmount ->
                    val dx = dragAmount.x
                    if (abs(dx) > 40f && data.size > 1) {
                        change.consume()
                        val next = if (dx < 0) (currentIndex + 1) % data.size else (currentIndex - 1 + data.size) % data.size
                        currentIndex = next
                        // 防抖：切换一次后暂停极短时间，避免一次滑动触发多次
                        isPaused = true
                        lastInteraction = SystemClock.uptimeMillis()
                        // 延迟一点点再恢复自动播放
                        gestureScope.launch {
                            kotlinx.coroutines.delay(300)
                            isPaused = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (data.isEmpty()) {
            Log.d(ADS_TAG, "No images to display; showing welcome text")
            Text(
                text = "欢迎使用智能果汁机",
                color = Color.White,
                fontSize = 28.sp,
                textAlign = TextAlign.Center
            )
        } else {
            Crossfade(targetState = currentIndex, label = "ads-crossfade") { index ->
                val item = data[index]
                val uri = item.uri
                val title = item.title?.takeIf { it.isNotBlank() } ?: deriveNameFromUri(uri)
                Box(Modifier.fillMaxSize()) {
                    // 使用 Subcompose 版本以在加载失败时提供更友好的兜底
                    coil.compose.SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(uri)
                            .size(screenWidthPx, screenHeightPx)
                            .precision(Precision.EXACT)
                            // 冷启动优化：首图禁用crossfade，其后仍保留（不改图片尺寸）
                            .crossfade(firstImageDisplayed)
                            .listener(object : ImageRequest.Listener {
                                override fun onSuccess(request: ImageRequest, result: coil.request.SuccessResult) {
                                    firstImageDisplayed = true
                                    if (!notifiedSplash) {
                                        onFirstImageReady?.invoke()
                                        notifiedSplash = true
                                    }
                                }
                                override fun onError(request: ImageRequest, result: coil.request.ErrorResult) {
                                    // 加载失败也通知一次，避免启动页长时间保留
                                    if (!notifiedSplash) {
                                        onFirstImageReady?.invoke()
                                        notifiedSplash = true
                                    }
                                }
                            })
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        loading = {
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.CircularProgressIndicator()
                            }
                        },
                        error = {
                            // 兜底：显示欢迎文字而非整屏黑
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Text(
                                    text = "欢迎使用智能果汁机",
                                    color = Color.Black,
                                    fontSize = 28.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = scale.value
                                scaleX = s
                                scaleY = s
                            }
                    )
                    // 顶/底渐变遮罩，提升层次感与文字可读性
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.12f),
                                        0.20f to Color.Transparent,
                                        0.80f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = 0.20f)
                                    )
                                )
                            }
                    )
                    // 底部标题（移除进度条）
                }

                // 页码指示点
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayCount = data.size.coerceAtMost(8)
                    repeat(displayCount) { i ->
                        val active = i == (currentIndex % displayCount)
                        Box(
                            Modifier
                                .height(6.dp)
                                .width(if (active) 20.dp else 6.dp)
                                .background(
                                    color = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}