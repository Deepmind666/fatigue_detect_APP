package com.example.juicemachine.ui

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import coil.size.Precision
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.ui.res.painterResource
import android.net.Uri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.juicemachine.R
import java.lang.Math.floorMod

private const val ADS_TAG = "AdsScreen"

// 公共数据模型：广告项（URI + 可选标题）
data class AdItem(val uri: String, val title: String? = null)

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AdsScreen(
    images: List<AdItem>,
    pageHoldMs: Long = 10_000L,
    scrollDurationMs: Int = 4_000,
    onNavigateToUser: () -> Unit,
    // 首图加载成功时触发，用于释放启动页
    onFirstImageReady: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isPausedRef = remember { BooleanArray(1) }

    val data = remember(images) { if (images.isNotEmpty()) images else emptyList() }
    val loopPages = remember(data) { if (data.isEmpty()) 1 else (data.size * 1000).coerceAtLeast(1000) }
    val startPage = remember(data) { if (data.isEmpty()) 0 else ((loopPages / 2) / data.size) * data.size }
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { loopPages })
    val prefetchContext = LocalContext.current

    var firstImageDisplayed by remember { mutableStateOf(false) }
// 删除空行无符号标记
     // 自动轮播：每页停留一段时间后，丝滑滚动到下一张；按压或拖拽时暂停
     LaunchedEffect(data, loopPages, pageHoldMs, scrollDurationMs) {
         if (data.isEmpty()) return@LaunchedEffect
         while (true) {
             kotlinx.coroutines.delay(pageHoldMs)
             if (data.size > 1 && !isPausedRef[0] && !pagerState.isScrollInProgress) {
               val next = floorMod(pagerState.currentPage + 1, loopPages)
               try {
                   pagerState.animateScrollToPage(
                       next,
                       animationSpec = tween(
                           durationMillis = scrollDurationMs.coerceAtLeast(0),
                           easing = LinearEasing
                       )
                   )
                   pagerState.scrollToPage(next)
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

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context as? Activity
        val window = activity?.window
        val controller = if (window != null) WindowInsetsControllerCompat(window, view) else null
        if (window != null && controller != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            if (window != null && controller != null) {
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    LaunchedEffect(pagerState.currentPage, data, screenWidthPx, screenHeightPx, firstImageDisplayed) {
        if (data.isEmpty()) return@LaunchedEffect
        if (data.size <= 1) return@LaunchedEffect
        if (!firstImageDisplayed) return@LaunchedEffect
        val loader = coil.Coil.imageLoader(prefetchContext)
        val current = pagerState.currentPage
        listOf(current + 1, current - 1).forEach { p ->
            val u = data[floorMod(p, data.size)].uri
            loader.enqueue(
                ImageRequest.Builder(prefetchContext)
                    .data(u)
                    .size(screenWidthPx, screenHeightPx)
                    .precision(Precision.INEXACT)
                    .build()
            )
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
                        isPausedRef[0] = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            isPausedRef[0] = false
                        }
                    },
                    onTap = {
                        Log.d(ADS_TAG, "onTap: navigate to user")
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
                    pageSize = PageSize.Fill,
                    pageSpacing = 0.dp,
                    contentPadding = PaddingValues(0.dp),
                    flingBehavior = PagerDefaults.flingBehavior(state = pagerState)
                ) { page ->
                val item = if (data.isEmpty()) AdItem(uri = "") else data[page % data.size]
                val uri = item.uri
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
                    val fallbackPainter = painterResource(id = R.drawable.placeholder)
                    val placeholderPainter = resPainter ?: fallbackPainter

                    val contentScaleState = remember(uri, screenWidthPx, screenHeightPx) { mutableStateOf(ContentScale.FillBounds) }
                    val contentScale = contentScaleState.value

                    // 取消左右虚化与Canvas裁切，统一使用单图全屏拉伸，避免设备差异

                    // 前景：取消虚化与比例保留，强制全屏拉伸（高度顶格，宽度可拉长）
                    if (id != 0 && resPainter != null) {
                        LaunchedEffect(id, uri, screenWidthPx, screenHeightPx) {
                            runCatching {
                                val s = resPainter.intrinsicSize
                                val iw = s.width
                                val ih = s.height
                                if (iw > 0f && ih > 0f && screenWidthPx > 0 && screenHeightPx > 0) {
                                    val imageRatio = iw / ih
                                    val screenRatio = screenWidthPx.toFloat() / screenHeightPx.toFloat()
                                    val diff = kotlin.math.abs(imageRatio - screenRatio)
                                    contentScaleState.value = if (diff <= 0.01f) ContentScale.Fit else ContentScale.FillBounds
                                } else {
                                    contentScaleState.value = ContentScale.FillBounds
                                }
                            }
                            if (!firstImageDisplayed) {
                                firstImageDisplayed = true
                                runCatching { onFirstImageReady?.invoke() }
                            }
                        }
                        Image(
                            painter = resPainter,
                            contentDescription = null,
                            contentScale = contentScale,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        coil.compose.AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(modelData)
                                .size(screenWidthPx, screenHeightPx)
                                .precision(Precision.INEXACT)
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            contentScale = contentScale,
                            placeholder = placeholderPainter,
                            error = placeholderPainter,
                            fallback = placeholderPainter,
                            modifier = Modifier.fillMaxSize(),
                            onSuccess = {
                                runCatching {
                                    val drawable = it.result.drawable
                                    val iw = drawable.intrinsicWidth
                                    val ih = drawable.intrinsicHeight
                                    if (iw > 0 && ih > 0 && screenWidthPx > 0 && screenHeightPx > 0) {
                                        val imageRatio = iw.toFloat() / ih.toFloat()
                                        val screenRatio = screenWidthPx.toFloat() / screenHeightPx.toFloat()
                                        val diff = kotlin.math.abs(imageRatio - screenRatio)
                                        contentScaleState.value = if (diff <= 0.01f) ContentScale.Fit else ContentScale.FillBounds
                                    } else {
                                        contentScaleState.value = ContentScale.FillBounds
                                    }
                                }
                                if (!firstImageDisplayed) {
                                    firstImageDisplayed = true
                                    runCatching { onFirstImageReady?.invoke() }
                                }
                            },
                            onError = { err ->
                                Log.e(ADS_TAG, "广告图加载失败: ${err.result.throwable.message}")
                            }
                        )
                    }
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
                }
            }
        }
    }
}
