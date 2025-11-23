package com.example.juicemachine.ui

import android.util.Log
import android.os.SystemClock
import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
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
import coil.request.CachePolicy
import coil.size.Precision
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import android.net.Uri
import androidx.compose.foundation.Image
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val ADS_TAG = "AdsScreen"

// 公共数据模型：广告项（URI + 可选标题）
data class AdItem(val uri: String, val title: String? = null)

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AdsScreen(
    images: List<AdItem>,
    intervalMs: Long = 10_000L,
    onNavigateToUser: () -> Unit,
    // 首图加载成功时触发，用于释放启动页
    onFirstImageReady: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isPaused by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableStateOf(SystemClock.uptimeMillis()) }

    val data = remember(images) { if (images.isNotEmpty()) images else emptyList() }
    val gestureScope = rememberCoroutineScope()
    val loopPages = remember(data) { if (data.isEmpty()) 1 else (data.size * 1000).coerceAtLeast(1000) }
    val startPage = remember(data) { if (data.isEmpty()) 0 else ((loopPages / 2) / data.size) * data.size }
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { loopPages })
    val prefetchContext = LocalContext.current

    // 暂时移除系统栏控制以排除窗口相关崩溃来源（后续再按设备恢复）
    val activity = LocalContext.current as? Activity
    var firstImageDisplayed by remember { mutableStateOf(false) }
    var notifiedSplash by remember { mutableStateOf(false) }
// 删除空行无符号标记
     // 自动轮播：按间隔切换到下一张，循环；按压或拖拽时暂停
     LaunchedEffect(data, intervalMs) {
         if (data.isEmpty()) return@LaunchedEffect
         while (true) {
             kotlinx.coroutines.delay(intervalMs)
             if (data.size > 1 && !isPaused && !pagerState.isScrollInProgress) {
               val next = pagerState.currentPage + 1
               try {
                   pagerState.animateScrollToPage(
                       next,
                       animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)
                   )
               } catch (_: Exception) {}
             }
         }
     }

    // 每次索引变化时记录当前展示的图片地址
    LaunchedEffect(pagerState.currentPage, data) {
        val uri = if (data.isEmpty()) null else data[pagerState.currentPage % data.size].uri
        val logicalIndex = if (data.isEmpty()) 0 else (pagerState.currentPage % data.size)
        Log.d(ADS_TAG, "Displaying index=${logicalIndex} uri=$uri")
    }

    // 移除 Ken Burns 动效，避免观感上的“被裁剪”效果
    val scale = remember { Animatable(1.0f) }
    LaunchedEffect(pagerState.currentPage, isPaused, data, intervalMs, firstImageDisplayed) {
        scale.snapTo(1.0f)
    }

    // 自动播放进度（用于底部细进度条）
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pagerState.currentPage, isPaused, data, intervalMs) {
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
            ,
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
            androidx.compose.runtime.CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { it },
                    beyondBoundsPageCount = 2,
                    flingBehavior = PagerDefaults.flingBehavior(state = pagerState)
                ) { page ->
                val item = if (data.isEmpty()) AdItem(uri = "") else data[page % data.size]
                val uri = item.uri
                val title = item.title?.takeIf { it.isNotBlank() } ?: deriveNameFromUri(uri)
                Box(Modifier.fillMaxSize()) {
                    // 解析 android.resource URI 为资源ID，作为更稳健的加载源
                    val ctx = LocalContext.current
                    val modelData: Any = remember(uri, ctx) {
                        val u = runCatching { Uri.parse(uri) }.getOrNull()
                        if (u?.scheme == "android.resource") {
                            val segments = u.pathSegments
                            val type = segments.getOrNull(0)
                            val name = segments.getOrNull(1)
                            if (type == "drawable" && !name.isNullOrBlank()) {
                                val resId = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
                                if (resId != 0) resId else uri
                            } else uri
                        } else uri
                    }
                    val id = (modelData as? Int) ?: 0
                    val resPainter = if (id != 0) painterResource(id) else null
                    val imageBitmap: ImageBitmap? = remember(id) {
                        if (id != 0) ImageBitmap.imageResource(ctx.resources, id) else null
                    }

                    // 取消左右虚化与Canvas裁切，统一使用单图全屏拉伸，避免设备差异

                    // 前景：取消虚化与比例保留，强制全屏拉伸（高度顶格，宽度可拉长）
                    coil.compose.AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(modelData)
                            .size(screenWidthPx, screenHeightPx)
                            .precision(Precision.EXACT)
                            .crossfade(false)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        placeholder = resPainter,
                        error = resPainter,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = {
                            if (!firstImageDisplayed) {
                                firstImageDisplayed = true
                                try { onFirstImageReady?.invoke() } catch (_: Exception) {}
                            }
                        },
                        onError = { err ->
                            Log.e(ADS_TAG, "广告图加载失败: ${err.result.throwable?.message}")
                        }
                    )
                    // 取消顶/底遮罩
                    // 左右不再使用整屏渐变，改为“边缘虚化填充”，避免白边
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
                        val active = i == ((pagerState.currentPage % data.size) % displayCount)
                        Box(
                            Modifier
                                .height(6.dp)
                                .width(if (active) 20.dp else 6.dp)
                                .background(
                                    color = if (active) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.5f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
                // 预取相邻页，减少滚动时的加载闪烁
                LaunchedEffect(page, data) {
                    val loader = coil.Coil.imageLoader(prefetchContext)
                    listOf(page + 1, page - 1).forEach { p ->
                        if (data.isNotEmpty()) {
                            val u = data[(p % loopPages + loopPages) % loopPages % data.size].uri
                            loader.enqueue(
                                ImageRequest.Builder(prefetchContext)
                                    .data(u)
                                    .precision(Precision.INEXACT)
                                    .build()
                            )
                        }
                    }
                }
                }
            }
        }
    }
}
