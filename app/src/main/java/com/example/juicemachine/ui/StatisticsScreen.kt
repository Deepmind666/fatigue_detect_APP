package com.example.juicemachine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juicemachine.JuiceMachineApplication
import com.example.juicemachine.data.database.DailySalesStats
import com.example.juicemachine.data.database.PopularRecipeStats
import com.example.juicemachine.data.database.InventoryConsumptionStats
import com.example.juicemachine.data.database.RecipeCupStats
import com.example.juicemachine.data.database.RecipeDailyTrendStats
import com.example.juicemachine.ui.viewmodel.StatisticsUiState
import com.example.juicemachine.ui.viewmodel.TimePeriod
import com.example.juicemachine.ui.viewmodel.StatisticsViewModel
import com.example.juicemachine.ui.viewmodel.StatisticsViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import java.text.NumberFormat
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize


enum class ChartMetric { Revenue, Volume }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    uiState: StatisticsUiState,
    onNavigateBack: () -> Unit,
    onTimePeriodChanged: (TimePeriod) -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onApplyCustomRange: (Long, Long) -> Unit,
    onClearCustomRange: () -> Unit
) {
    var showDateRangeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "销售统计",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                            )
                            Spacer(Modifier.width(4.dp))
                            val subtitle = if (uiState.customStartTime != null && uiState.customEndTime != null) {
                                formatRange(uiState.customStartTime, uiState.customEndTime)
                            } else {
                                uiState.selectedTimePeriod.displayName
                            }
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val rangeLabel = if (uiState.customStartTime != null && uiState.customEndTime != null) {
                        "自定义: " + formatRange(uiState.customStartTime, uiState.customEndTime)
                    } else {
                        uiState.selectedTimePeriod.displayName
                    }
                    AssistChip(
                        onClick = { showDateRangeDialog = true },
                        label = { Text(rangeLabel) },
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDateRangeDialog = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("自定义范围")
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 时间段选择器 + 自定义范围
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimePeriodSelector(
                        selectedPeriod = uiState.selectedTimePeriod,
                        onPeriodSelected = onTimePeriodChanged
                    )
                }
            }
            
            // 概览卡片
            item {
                OverviewCards(
                    totalRevenue = uiState.totalRevenue,
                    totalOrders = uiState.totalOrders,
                    todayRevenue = uiState.todayRevenue,
                    todayOrders = uiState.todayOrders
                )
            }
            
            // 销售趋势图表
            item {
                SalesTrendChart(
                    dailySales = uiState.dailySales,
                    timePeriod = uiState.selectedTimePeriod
                )
            }
            // 杯型趋势图（多序列：中杯/大杯）
            // item {
            //     CupSizeTrendChart(
            //         trends = uiState.recipeDailyTrends,
            //         timePeriod = uiState.selectedTimePeriod
            //     )
            // }
            
            // 新增：按饮品（中/大杯）趋势图
            item {
                RecipeTrendsByDrinkCharts(
                    trends = uiState.recipeDailyTrends,
                    timePeriod = uiState.selectedTimePeriod
                )
            }
            
            // 热销商品
            item {
                PopularRecipesCard(
                    popularRecipes = uiState.popularRecipes
                )
            }
            // 新增：各饮品（按杯型）统计
            item {
                RecipeCupStatsCard(
                    stats = uiState.recipeCupStats
                )
            }
            
            // 库存消耗统计
            item {
                InventoryConsumptionCard(
                    inventoryStats = uiState.inventoryStats
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    if (showDateRangeDialog) {
        DateRangePickerDialog(
            onDismiss = { showDateRangeDialog = false },
            onConfirm = { start, end ->
                showDateRangeDialog = false
                if (start != null && end != null) {
                    onApplyCustomRange(start, end)
                }
            }
        )
    }
    
    // 错误对话框
    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("错误") },
            text = { Text(uiState.errorMessage) },
            confirmButton = {
                Button(onClick = onDismissError) {
                    Text("确定")
                }
            }
        )
    }
    
    // 加载指示器
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("加载统计数据中...")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long?, Long?) -> Unit
) {
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60L * 60L * 1000L

    fun startOfDay(ms: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ms
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    val selectable = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= now
        override fun isSelectableYear(year: Int): Boolean {
            val y = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            return year <= y
        }
    }
    val rangeState = rememberDateRangePickerState(selectableDates = selectable)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val s = rangeState.selectedStartDateMillis
            val e = rangeState.selectedEndDateMillis
            TextButton(onClick = { onConfirm(s, e) }, enabled = s != null && e != null) {
                Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        Column(Modifier.padding(8.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    TextButton(onClick = {
                        val start = startOfDay(now - 6L * dayMs)
                        val end = now
                        onConfirm(start, end)
                    }) { Text("最近7天") }
                }
                item {
                    TextButton(onClick = {
                        val start = startOfDay(now - 29L * dayMs)
                        val end = now
                        onConfirm(start, end)
                    }) { Text("最近30天") }
                }
                item {
                    TextButton(onClick = {
                        val cal = java.util.Calendar.getInstance()
                        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
                        val delta = (dow + 5) % 7
                        val start = startOfDay(now - delta * dayMs)
                        val end = now
                        onConfirm(start, end)
                    }) { Text("本周") }
                }
                item {
                    TextButton(onClick = {
                        val cal = java.util.Calendar.getInstance()
                        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                        val start = startOfDay(cal.timeInMillis)
                        val end = now
                        onConfirm(start, end)
                    }) { Text("本月") }
                }
                item {
                    TextButton(onClick = {
                        val thisMonthStartCal = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        val lastMonthStartCal = (thisMonthStartCal.clone() as java.util.Calendar).apply {
                            add(java.util.Calendar.MONTH, -1)
                        }
                        val start = lastMonthStartCal.timeInMillis
                        val end = thisMonthStartCal.timeInMillis - 1
                        onConfirm(start, end)
                    }) { Text("上月") }
                }
            }

            Spacer(Modifier.height(4.dp))
            DateRangePicker(
                state = rangeState,
                showModeToggle = true
            )
        }
    }
}

@Composable
private fun TimePeriodSelector(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        LazyRow(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TimePeriod.values()) { period ->
                FilterChip(
                    onClick = { onPeriodSelected(period) },
                    label = { Text(period.displayName) },
                    selected = selectedPeriod == period,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun OverviewCards(
    totalRevenue: Int,
    totalOrders: Int,
    todayRevenue: Int,
    todayOrders: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "总营收",
            value = formatCurrency(totalRevenue),
            icon = Icons.Default.AccountBalance,
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "总订单",
            value = formatNumber(totalOrders),
            icon = Icons.Default.ShoppingCart,
            color = Color(0xFF2196F3),
            modifier = Modifier.weight(1f)
        )
    }
    
    Spacer(modifier = Modifier.height(12.dp))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "今日营收",
            value = formatCurrency(todayRevenue),
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            color = Color(0xFFFF9800),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "今日订单",
            value = formatNumber(todayOrders),
            icon = Icons.Default.Today,
            color = Color(0xFF9C27B0),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SalesTrendChart(
    dailySales: List<DailySalesStats>,
    timePeriod: TimePeriod
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "销售趋势",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = timePeriod.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // 指标切换：营收/销量（订单数）
            var metric by remember { mutableStateOf(ChartMetric.Revenue) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = metric == ChartMetric.Revenue,
                    onClick = { metric = ChartMetric.Revenue },
                    label = { Text("营收") }
                )
                FilterChip(
                    selected = metric == ChartMetric.Volume,
                    onClick = { metric = ChartMetric.Volume },
                    label = { Text("销量") }
                )
            }
            
            // 单位提示
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (metric == ChartMetric.Revenue) "单位：元" else "单位：单",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            if (dailySales.isNotEmpty()) {
                SimpleLineChart(
                    data = dailySales,
                    metric = metric,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "暂无销售数据",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// --------- Helpers ---------
private fun formatNumber(value: Number): String {
    return NumberFormat.getIntegerInstance(Locale.CHINA).format(value.toLong())
}

private fun formatCurrency(value: Number): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale.CHINA)
    fmt.maximumFractionDigits = 0
    fmt.minimumFractionDigits = 0
    return fmt.format(value.toLong())
}

private fun formatRange(start: Long, end: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    return "${sdf.format(Date(start))} ~ ${sdf.format(Date(end))}"
}

@Composable
private fun SimpleLineChart(
    data: List<DailySalesStats>,
    metric: ChartMetric,
    modifier: Modifier = Modifier
) {
    // 交互状态：当前手指/鼠标位置的x坐标，用于高亮点
    var hoverX by remember(data, metric) { mutableStateOf<Float?>(null) }

    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(data, metric) {
        anim.snapTo(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(550, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )
    }

    val gestureModifier = modifier
        .pointerInput(data, metric) {
            detectDragGestures(
                onDragStart = { offset -> hoverX = offset.x },
                onDrag = { change, _ -> hoverX = change.position.x },
                onDragEnd = { /* 保持最后位置 */ },
                onDragCancel = { /* no-op */ }
            )
        }
        .pointerInput(data, metric) {
            detectTapGestures(
                onPress = { offset -> hoverX = offset.x; try { tryAwaitRelease() } finally { } })
        }

    Canvas(modifier = gestureModifier) {
        if (data.isEmpty()) return@Canvas

        // 最大值按当前指标选择
        val maxRaw = when (metric) {
            ChartMetric.Revenue -> data.maxOfOrNull { it.totalRevenue } ?: 0
            ChartMetric.Volume -> data.maxOfOrNull { it.completedOrders } ?: 0
        }
        val maxValue = max(1f, maxRaw.toFloat())

        // 更大的边距为坐标轴标签留空间
        val leftPadding = 48.dp.toPx()
        val rightPadding = 12.dp.toPx()
        val topPadding = 12.dp.toPx()
        val bottomPadding = 28.dp.toPx()

        val width = size.width - leftPadding - rightPadding
        val height = size.height - topPadding - bottomPadding
        val stepX = if (data.size <= 1) 0f else width / (data.size - 1)

        // 计算点位
        fun valueOf(stats: DailySalesStats): Int = when (metric) {
            ChartMetric.Revenue -> stats.totalRevenue
            ChartMetric.Volume -> stats.completedOrders
        }
        val baseY = topPadding + height
        val points = data.mapIndexed { index, stats ->
            val x = leftPadding + index * stepX
            val ratio = (valueOf(stats) / maxValue).coerceIn(0f, 1f)
            val yRaw = topPadding + (1f - ratio) * height
            val y = baseY + (yRaw - baseY) * anim.value
            Offset(x, y)
        }
        
        val gridColor = onSurfaceVariant.copy(alpha = 0.15f)
        val axisColor = onSurfaceVariant.copy(alpha = 0.25f)
        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = onSurfaceVariant.toArgb()
            textSize = 10.dp.toPx()
        }
        drawLine(color = axisColor, start = Offset(leftPadding, baseY), end = Offset(size.width - rightPadding, baseY), strokeWidth = 1.5.dp.toPx())
        // 水平网格线 + Y轴刻度文本（0 到 max，等分）
        val levels = 4
        repeat(levels + 1) { i ->
            val y = topPadding + height * i / levels
            drawLine(color = gridColor, start = Offset(leftPadding, y), end = Offset(size.width - rightPadding, y), strokeWidth = 1.dp.toPx())
            val value = ((levels - i) * maxRaw / levels.toFloat()).roundToInt()
            val label = when (metric) { ChartMetric.Revenue -> formatCurrency(value); ChartMetric.Volume -> formatNumber(value); else -> formatNumber(value) }
            drawContext.canvas.nativeCanvas.drawText(
                label,
                leftPadding - 8.dp.toPx() - labelPaint.measureText(label),
                y + 3.dp.toPx(),
                labelPaint
            )
        }

        drawLine(color = axisColor, start = Offset(leftPadding, baseY), end = Offset(size.width - rightPadding, baseY), strokeWidth = 1.5.dp.toPx())

        // 在水平/垂直辅助线中引入虚线效果（仅用于竖向参考线及标签竖线）
        val gridDash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
        val labelCount = if (data.size < 4) data.size else 4
        if (labelCount > 0) {
            val indices = if (labelCount == data.size) (0 until data.size).toList() else when (labelCount) {
                1 -> listOf(0)
                2 -> listOf(0, data.size - 1)
                3 -> listOf(0, data.size / 2, data.size - 1)
                else -> listOf(0, (data.size - 1) / 3, (2 * (data.size - 1)) / 3, data.size - 1)
            }
            indices.distinct().sorted().forEach { idx ->
                val labelX = leftPadding + idx * stepX
                val rawDate = data[idx].date
                val label = if (rawDate.length >= 10) rawDate.substring(5, 10) else rawDate
                drawLine(
                    color = gridColor,
                    start = Offset(labelX, topPadding),
                    end = Offset(labelX, baseY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = gridDash
                )
                drawContext.canvas.nativeCanvas.drawText(label, labelX - labelPaint.measureText(label) / 2f, baseY + 14.dp.toPx(), labelPaint)
            }
        }

        // 分段曲线（值为0不连线）
        val valuesForSeg = data.map { valueOf(it) }
        val segmentsIdx = mutableListOf<List<Int>>().apply {
            var cur = mutableListOf<Int>()
            for (i in valuesForSeg.indices) {
                if (valuesForSeg[i] > 0) {
                    cur.add(i)
                } else if (cur.isNotEmpty()) {
                    add(cur.toList()); cur.clear()
                }
            }
            if (cur.isNotEmpty()) add(cur.toList())
        }
        fun buildSmoothPath(segIdxs: List<Int>): Path {
            val segPoints = segIdxs.map { points[it] }
            return Path().apply {
                moveTo(segPoints.first().x, segPoints.first().y)
                if (segPoints.size >= 2) {
                    for (i in 1 until segPoints.size) {
                        val prev = segPoints[i - 1]
                        val cur = segPoints[i]
                        val midX = (prev.x + cur.x) / 2f
                        val midY = (prev.y + cur.y) / 2f
                        quadraticBezierTo(prev.x, prev.y, midX, midY)
                    }
                    val last = segPoints.last()
                    val pre = segPoints[segPoints.size - 2]
                    quadraticBezierTo(pre.x, pre.y, last.x, last.y)
                }
            }
        }
        segmentsIdx.forEach { seg ->
            if (seg.size >= 2) {
                val linePath = buildSmoothPath(seg)
                // 每段单独填充面积渐变
                val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo(points[seg.last()].x, baseY)
                    lineTo(points[seg.first()].x, baseY)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(primary.copy(alpha = 0.25f), Color.Transparent),
                        startY = topPadding,
                        endY = baseY
                    )
                )
                drawPath(
                    path = linePath,
                    color = primary,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            // 段内仅绘制非零点
            val outer = primary
            val inner = Color.White
            val outerR = 4.dp.toPx()
            val innerR = 2.dp.toPx()
            seg.forEach { i ->
                val p = points[i]
                drawCircle(color = outer, radius = outerR, center = p)
                drawCircle(color = inner, radius = innerR, center = p)
            }
        }

        // 交互：高亮选中点与Tooltip
        hoverX?.let { hx ->
            val clampedX = hx.coerceIn(leftPadding, size.width - rightPadding)
            val idx = if (stepX == 0f) 0 else ((clampedX - leftPadding) / stepX).roundToInt().coerceIn(0, points.lastIndex)
            val p = points[idx]
            // 竖直参考线
            drawLine(
                color = primary.copy(alpha = 0.4f),
                start = Offset(p.x, topPadding),
                end = Offset(p.x, baseY),
                strokeWidth = 1.dp.toPx()
            )
            // 高亮点
            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = p)
            drawCircle(color = primary, radius = 7.dp.toPx(), center = p, style = Stroke(width = 2.dp.toPx()))

            val stats = data[idx]
            val dateLabel = if (stats.date.length >= 10) stats.date.substring(5, 10) else stats.date
            val valueLabel = when (metric) {
                ChartMetric.Revenue -> formatCurrency(stats.totalRevenue)
                ChartMetric.Volume -> formatNumber(stats.completedOrders)
            }
            val tip = "$dateLabel  $valueLabel"
            val tipPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = Color.White.toArgb()
                textSize = 12.dp.toPx()
            }
            val tipPadding = 8.dp.toPx()
            val textW = tipPaint.measureText(tip)
            val textH = tipPaint.fontMetrics.let { it.bottom - it.top }
            val boxW = textW + tipPadding * 2
            val boxH = textH + tipPadding * 1.5f
            val boxLeft = (p.x - boxW / 2).coerceIn(leftPadding, size.width - rightPadding - boxW)
            val boxTop = (p.y - 36.dp.toPx() - boxH).coerceAtLeast(topPadding + 4.dp.toPx())
            // 阴影（柔和）：在主框体下方轻微偏移一层暗色
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.15f),
                topLeft = Offset(boxLeft + 1.dp.toPx(), boxTop + 2.dp.toPx()),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )
            drawRoundRect(
                color = primary.copy(alpha = 0.95f),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )
            drawContext.canvas.nativeCanvas.drawText(
                tip,
                boxLeft + tipPadding,
                boxTop + tipPadding + (textH - tipPaint.fontMetrics.bottom),
                tipPaint
            )
        }
    }
}


@Composable
private fun CupSizeTrendChart(
    trends: List<RecipeDailyTrendStats>,
    timePeriod: TimePeriod
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "杯型趋势",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = timePeriod.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            var metric by remember { mutableStateOf(ChartMetric.Revenue) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = metric == ChartMetric.Revenue,
                    onClick = { metric = ChartMetric.Revenue },
                    label = { Text("营收") }
                )
                FilterChip(
                    selected = metric == ChartMetric.Volume,
                    onClick = { metric = ChartMetric.Volume },
                    label = { Text("销量") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (metric == ChartMetric.Revenue) "单位：元" else "单位：单",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            val groupedByDate = remember(trends) { trends.groupBy { it.date } }
            val dates = remember(trends) { groupedByDate.keys.toList().sorted() }
            val middleKey = "中杯"
            val largeKey = "大杯"
            val middleSeries = remember(trends, metric) {
                dates.map { d ->
                    groupedByDate[d].orEmpty().filter { it.cupSize == middleKey }.sumOf {
                        if (metric == ChartMetric.Revenue) it.totalRevenue else it.totalQuantity
                    }
                }
            }
            val largeSeries = remember(trends, metric) {
                dates.map { d ->
                    groupedByDate[d].orEmpty().filter { it.cupSize == largeKey }.sumOf {
                        if (metric == ChartMetric.Revenue) it.totalRevenue else it.totalQuantity
                    }
                }
            }

            if (dates.isNotEmpty()) {
                MultiSeriesLineChart(
                    dates = dates,
                    series = listOf(
                        SeriesConfig(label = middleKey, values = middleSeries, color = MaterialTheme.colorScheme.secondary, strokeWidth = 3.dp),
                        SeriesConfig(label = largeKey, values = largeSeries, color = MaterialTheme.colorScheme.tertiary, strokeWidth = 4.dp)
                    ),
                    metric = metric,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "暂无趋势数据",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeTrendsByDrinkCharts(
    trends: List<RecipeDailyTrendStats>,
    timePeriod: TimePeriod
) {
    if (trends.isEmpty()) return

    val grouped = remember(trends) { trends.groupBy { it.recipeName } }
    val dates = remember(trends) { trends.map { it.date }.distinct().sorted() }

    // 构造每个饮品的序列（按日期缺失补0）
    val series = buildList {
        grouped.forEach { (name, list) ->
            val byDate = list.groupBy { it.date }
            val totalValues = dates.map { d -> byDate[d].orEmpty().sumOf { it.totalRevenue } }
            val middleValues = dates.map { d -> byDate[d].orEmpty().filter { it.cupSize == "中杯" }.sumOf { it.totalRevenue } }
            val largeValues = dates.map { d -> byDate[d].orEmpty().filter { it.cupSize == "大杯" }.sumOf { it.totalRevenue } }

            val base = Color(0xFF000000.toInt() or (name.hashCode() and 0x00FFFFFF))
            add(SeriesConfig(label = name + "·总", values = totalValues, color = base, strokeWidth = 3.dp))
            add(SeriesConfig(label = name + "·中杯", values = middleValues, color = base.copy(alpha = 0.9f), strokeWidth = 3.5.dp))
            add(SeriesConfig(label = name + "·大杯", values = largeValues, color = base.copy(alpha = 0.55f), strokeWidth = 2.5.dp))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "按饮品趋势（总/中杯/大杯）",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = timePeriod.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

            if (dates.isNotEmpty() && series.isNotEmpty()) {
                MultiSeriesLineChart(
                    dates = dates,
                    series = series,
                    metric = ChartMetric.Revenue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    xLabelCount = when (timePeriod) {
                        TimePeriod.TODAY -> 8
                        TimePeriod.WEEK -> 7
                        TimePeriod.MONTH -> 10
                        TimePeriod.QUARTER -> 12
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class SeriesConfig(
    val label: String,
    val values: List<Int>,
    val color: Color,
    val strokeWidth: Dp
)

@Composable
private fun MultiSeriesLineChart(
    dates: List<String>,
    series: List<SeriesConfig>,
    metric: ChartMetric,
    modifier: Modifier = Modifier,
    xLabelCount: Int? = null
) {
    var hoverX by remember(dates, series, metric) { mutableStateOf<Float?>(null) }

    var hiddenLabels by remember(series) { mutableStateOf(setOf<String>()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(dates, series, metric) {
        anim.snapTo(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(550, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )
    }

    val gestureModifier = modifier
        .onSizeChanged { canvasSize = it }
        .pointerInput(dates, series, metric, hiddenLabels) {
            detectDragGestures(
                onDragStart = { offset -> hoverX = offset.x },
                onDrag = { change, _ -> hoverX = change.position.x }
            )
        }
        .pointerInput(dates, series, metric, hiddenLabels, canvasSize) {
            detectTapGestures(
                onTap = { offset ->
                    with(density) {
                        val leftPadding = 48.dp.toPx()
                        val rightPadding = 12.dp.toPx()
                        val topPadding = 12.dp.toPx()
                        val legendLineW = 18.dp.toPx()
                        val legendLineYStep = 16.dp.toPx()
                        val legendLeft = leftPadding + 6.dp.toPx()
                        val legendTop = topPadding + 6.dp.toPx()
                        val maxContentW = (canvasSize.width.toFloat() - rightPadding - legendLeft - 8.dp.toPx()).coerceAtLeast(60f)
                        val hSpacing = 12.dp.toPx()
                        val labelPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = onSurfaceVariant.toArgb()
                            textSize = 10.dp.toPx()
                        }
                        data class LegendPlacement(val cfg: SeriesConfig, val x: Float, val row: Int, val w: Float)
                        val placements = mutableListOf<LegendPlacement>()
                        var curRow = 0
                        var curX = 0f
                        series.forEach { cfg ->
                            val textW = labelPaint.measureText(cfg.label)
                            val entryW = legendLineW + 6.dp.toPx() + textW
                            if (curX > 0f && curX + entryW > maxContentW) {
                                curRow += 1
                                curX = 0f
                            }
                            placements.add(LegendPlacement(cfg, curX, curRow, entryW))
                            curX += entryW + hSpacing
                        }
                        var toggled = false
                        placements.forEach { p ->
                            val x0 = legendLeft + p.x
                            val cy = legendTop + p.row * legendLineYStep
                            val rectLeft = x0
                            val rectTop = cy - 10.dp.toPx()
                            val rectRight = x0 + p.w
                            val rectBottom = cy + 10.dp.toPx()
                            if (offset.x in rectLeft..rectRight && offset.y in rectTop..rectBottom) {
                                hiddenLabels = if (hiddenLabels.contains(p.cfg.label)) hiddenLabels - p.cfg.label else hiddenLabels + p.cfg.label
                                toggled = true
                                return@with
                            }
                        }
                        if (!toggled) {
                            hoverX = offset.x
                        }
                    }
                },
                onPress = { pos ->
                    hoverX = pos.x
                    try { tryAwaitRelease() } finally { }
                }
            )
        }

    Canvas(modifier = gestureModifier) {
        if (dates.isEmpty() || series.isEmpty()) return@Canvas

        val visibleSeries = series.filter { it.label !in hiddenLabels }

        // val maxRaw = (series.maxOfOrNull { it.values.maxOfOrNull() ?: 0 } ?: 0).coerceAtLeast(0)
        val maxRaw = (visibleSeries.asSequence().mapNotNull { it.values.maxOrNull() }.maxOrNull() ?: 0).coerceAtLeast(0)
        val maxValue = max(1f, maxRaw.toFloat())

        val leftPadding = 48.dp.toPx()
        val rightPadding = 12.dp.toPx()
        val topPadding = 12.dp.toPx()
        val bottomPadding = 28.dp.toPx()

        val width = size.width - leftPadding - rightPadding
        val height = size.height - topPadding - bottomPadding
        val stepX = if (dates.size <= 1) 0f else width / (dates.size - 1)

        val baseY = topPadding + height
        val gridColor = onSurfaceVariant.copy(alpha = 0.15f)
        val axisColor = onSurfaceVariant.copy(alpha = 0.25f)
        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = onSurfaceVariant.toArgb()
            textSize = 10.dp.toPx()
        }

        // 水平网格线 + Y轴刻度文本（0 到 max，等分）
        val levels = 4
        repeat(levels + 1) { i ->
            val y = topPadding + height * i / levels
            drawLine(color = gridColor, start = Offset(leftPadding, y), end = Offset(size.width - rightPadding, y), strokeWidth = 1.dp.toPx())
            val value = ((levels - i) * maxRaw / levels.toFloat()).roundToInt()
            val label = when (metric) {
                ChartMetric.Revenue -> formatCurrency(value)
                ChartMetric.Volume -> formatNumber(value)
            }
            drawContext.canvas.nativeCanvas.drawText(
                label,
                leftPadding - 8.dp.toPx() - labelPaint.measureText(label),
                y + 3.dp.toPx(),
                labelPaint
            )
        }

        drawLine(color = axisColor, start = Offset(leftPadding, baseY), end = Offset(size.width - rightPadding, baseY), strokeWidth = 1.5.dp.toPx())

        val desiredLabels = xLabelCount ?: when (dates.size) {
            24 -> 8
            7 -> 7
            30 -> 10
            90 -> 12
            else -> kotlin.math.min(dates.size, 8)
        }
        val labelCount = if (desiredLabels <= 0) 0 else desiredLabels
        if (labelCount > 0) {
            val last = kotlin.math.max(0, dates.size - 1)
            val indices = if (labelCount == 1 || last == 0) listOf(0) else (0 until labelCount).map { i ->
                ((i * last.toFloat()) / (labelCount - 1)).roundToInt()
            }
            indices.distinct().sorted().forEach { idx ->
                val labelX = leftPadding + idx * stepX
                val rawDate = dates[idx]
                val label = if (rawDate.length == 2) rawDate else if (rawDate.length >= 10) rawDate.substring(5, 10) else rawDate
                drawLine(color = gridColor, start = Offset(labelX, topPadding), end = Offset(labelX, baseY), strokeWidth = 1.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(label, labelX - labelPaint.measureText(label) / 2f, baseY + 14.dp.toPx(), labelPaint)
            }
        }

        data class Pts(val cfg: SeriesConfig, val points: List<Offset>)
        val allPts: List<Pts> = visibleSeries.map { cfg ->
            val pts = cfg.values.mapIndexed { index, v ->
                val x = leftPadding + index * stepX
                val ratio = (v / maxValue).coerceIn(0f, 1f)
                val yRaw = topPadding + (1f - ratio) * height
                val y = baseY + (yRaw - baseY) * anim.value
                Offset(x, y)
            }
            Pts(cfg, pts)
        }

        allPts.forEach { (cfg, pts) ->
            if (pts.isNotEmpty()) {
                // 基于非零值分段，不跨零连线
                val segmentsIdx = mutableListOf<List<Int>>().apply {
                    var cur = mutableListOf<Int>()
                    for (i in cfg.values.indices) {
                        if (cfg.values[i] > 0) {
                            cur.add(i)
                        } else if (cur.isNotEmpty()) {
                            add(cur.toList()); cur.clear()
                        }
                    }
                    if (cur.isNotEmpty()) add(cur.toList())
                }
                fun buildSmoothPath(seg: List<Int>): Path {
                    val segPoints = seg.map { pts[it] }
                    return Path().apply {
                        moveTo(segPoints.first().x, segPoints.first().y)
                        if (segPoints.size >= 2) {
                            for (i in 1 until segPoints.size) {
                                val prev = segPoints[i - 1]
                                val cur = segPoints[i]
                                val midX = (prev.x + cur.x) / 2f
                                val midY = (prev.y + cur.y) / 2f
                                quadraticBezierTo(prev.x, prev.y, midX, midY)
                            }
                            val last = segPoints.last()
                            val pre = segPoints[segPoints.size - 2]
                            quadraticBezierTo(pre.x, pre.y, last.x, last.y)
                        }
                    }
                }
                segmentsIdx.forEach { seg ->
                    if (seg.size >= 2) {
                        val path = buildSmoothPath(seg)
                        drawPath(
                            path = path,
                            color = cfg.color,
                            style = Stroke(width = cfg.strokeWidth.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    // 段内数据点（仅非零）
                    seg.forEach { i ->
                        val p = pts[i]
                        drawCircle(color = cfg.color, radius = 3.dp.toPx(), center = p)
                    }
                }
            }
        }

        val legendPadding = 8.dp.toPx()
        val legendLineW = 18.dp.toPx()
        val legendLineYStep = 16.dp.toPx()
        val legendLeft = leftPadding + 6.dp.toPx()
        val legendTop = topPadding + 6.dp.toPx()
        val maxContentW = (size.width - rightPadding - legendLeft - 8.dp.toPx()).coerceAtLeast(60f)
        val hSpacing = 12.dp.toPx()
        data class LegendPlacement(val cfg: SeriesConfig, val x: Float, val row: Int)
        val placements = mutableListOf<LegendPlacement>()
        val rowWidths = mutableMapOf<Int, Float>()
        var curRow = 0
        var curX = 0f
        series.forEach { cfg ->
            val textW = labelPaint.measureText(cfg.label)
            val entryW = legendLineW + 6.dp.toPx() + textW
            if (curX > 0f && curX + entryW > maxContentW) {
                rowWidths[curRow] = curX - hSpacing
                curRow += 1
                curX = 0f
            }
            placements.add(LegendPlacement(cfg, curX, curRow))
            curX += entryW + hSpacing
        }
        rowWidths[curRow] = (rowWidths[curRow] ?: 0f).coerceAtLeast(curX - hSpacing)
        val rowsCount = (placements.maxOfOrNull { it.row } ?: 0) + 1
        val legendContentW = (0 until rowsCount).maxOfOrNull { rowWidths[it] ?: 0f } ?: 0f
        val legendBoxW = legendContentW + legendPadding * 2
        val legendBoxH = (rowsCount * legendLineYStep) + legendPadding
        // 背景
        drawRoundRect(
            color = Color.White.copy(alpha = 0.75f),
            topLeft = Offset(legendLeft - legendPadding, legendTop - legendPadding/2),
            size = Size(legendBoxW, legendBoxH),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
        placements.forEach { (cfg, relX, row) ->
            val x0 = legendLeft + relX
            val y = legendTop + row * legendLineYStep
            val isHidden = hiddenLabels.contains(cfg.label)
            val lineColor = if (isHidden) cfg.color.copy(alpha = 0.25f) else cfg.color
            val textPaint = android.graphics.Paint(labelPaint).apply { color = if (isHidden) onSurfaceVariant.copy(alpha = 0.5f).toArgb() else onSurfaceVariant.toArgb() }
            drawLine(color = lineColor, start = Offset(x0, y), end = Offset(x0 + legendLineW, y), strokeWidth = cfg.strokeWidth.toPx())
            drawContext.canvas.nativeCanvas.drawText(
                cfg.label,
                x0 + legendLineW + 6.dp.toPx(),
                y + 3.dp.toPx(),
                textPaint
            )
        }

        hoverX?.let { hx ->
            val clampedX = hx.coerceIn(leftPadding, size.width - rightPadding)
            val idx = if (stepX == 0f) 0 else ((clampedX - leftPadding) / stepX).roundToInt().coerceIn(0, dates.lastIndex)
            val x = leftPadding + idx * stepX
            drawLine(color = Color.Gray.copy(alpha = 0.4f), start = Offset(x, topPadding), end = Offset(x, baseY), strokeWidth = 1.dp.toPx())

            val dateLabel = if (dates[idx].length >= 10) dates[idx].substring(5, 10) else dates[idx]
            val parts = visibleSeries.map { cfg ->
                val value = cfg.values.getOrNull(idx) ?: 0
                val label = when (metric) { ChartMetric.Revenue -> formatCurrency(value); ChartMetric.Volume -> formatNumber(value) }
                cfg to label
            }
            val tipPadding = 8.dp.toPx()
            val tipPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = Color.White.toArgb()
                textSize = 12.dp.toPx()
            }
            val titlePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = Color.White.copy(alpha = 0.9f).toArgb()
                textSize = 11.dp.toPx()
            }
            val lines = listOf("$dateLabel") + parts.map { (cfg, label) -> "${cfg.label}  $label" }
            val textW = lines.maxOf { tipPaint.measureText(it) }
            val textH = tipPaint.fontMetrics.let { it.bottom - it.top }
            val boxW = textW + tipPadding * 2
            val boxH = (textH * lines.size) + tipPadding * 1.8f
            val boxLeft = (x - boxW / 2).coerceIn(leftPadding, size.width - rightPadding - boxW)
            val boxTop = (topPadding + 8.dp.toPx())
            drawRoundRect(color = Color.Black.copy(alpha = 0.15f), topLeft = Offset(boxLeft + 1.dp.toPx(), boxTop + 2.dp.toPx()), size = Size(boxW, boxH), cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()))
            drawRoundRect(color = primary.copy(alpha = 0.95f), topLeft = Offset(boxLeft, boxTop), size = Size(boxW, boxH), cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()))
            var ty = boxTop + tipPadding + (textH - tipPaint.fontMetrics.bottom)
            drawContext.canvas.nativeCanvas.drawText(lines.first(), boxLeft + tipPadding, ty, titlePaint)
            parts.forEachIndexed { _, (cfg, label) ->
                ty += textH
                val sw = 6.dp.toPx()
                val sh = 6.dp.toPx()
                drawRect(color = cfg.color, topLeft = Offset(boxLeft + tipPadding, ty - textH + (textH - sh) / 2), size = Size(sw, sh))
                drawContext.canvas.nativeCanvas.drawText(
                    "${cfg.label}  $label",
                    boxLeft + tipPadding + sw + 4.dp.toPx(),
                    ty,
                    tipPaint
                )
            }
        }
    }
}

// 包装版：在内部创建并使用 StatisticsViewModel，供导航直接调用
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val app = context as JuiceMachineApplication
    val vm: StatisticsViewModel = viewModel(
        factory = StatisticsViewModelFactory(app.orderRepository)
    )
    val uiState by vm.uiState.collectAsState()

    StatisticsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onTimePeriodChanged = vm::onTimePeriodChanged,
        onRefresh = vm::onRefresh,
        onDismissError = vm::onDismissError,
        onApplyCustomRange = vm::applyCustomRange,
        onClearCustomRange = vm::clearCustomRange
    )
}

@Composable
private fun PopularRecipesCard(
    popularRecipes: List<PopularRecipeStats>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700)
                )
                Text(
                    text = "热销商品",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (popularRecipes.isNotEmpty()) {
                popularRecipes.take(5).forEachIndexed { index, recipe ->
                    PopularRecipeItem(
                        recipe = recipe,
                        rank = index + 1
                    )
                    if (index < popularRecipes.size - 1 && index < 4) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无热销数据",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PopularRecipeItem(
    recipe: PopularRecipeStats,
    rank: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 排名徽章
        Card(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (rank) {
                    1 -> Color(0xFFFFD700)
                    2 -> Color(0xFFC0C0C0)
                    3 -> Color(0xFFCD7F32)
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = recipe.recipeName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "销量: ${formatNumber(recipe.totalQuantity)} | 营收: ${formatCurrency(recipe.totalRevenue)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = formatCurrency(recipe.avgPrice.toInt()),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun InventoryConsumptionCard(
    inventoryStats: List<InventoryConsumptionStats>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "库存消耗统计",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (inventoryStats.isNotEmpty()) {
                inventoryStats.forEach { stat ->
                    InventoryStatItem(stat)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无库存消耗数据",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryStatItem(
    stat: InventoryConsumptionStats
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = stat.recipeName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Medium
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "果汁消耗",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${stat.totalJuiceConsumed}ml",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFFFF6B35)
                    )
                }
                Column {
                    Text(
                        text = "水消耗",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${stat.totalWaterConsumed}ml",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF2196F3)
                    )
                }
                Column {
                    Text(
                        text = "平均果汁/单",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${stat.avgJuicePerOrder.toInt()}ml",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeCupStatsCard(
    stats: List<RecipeCupStats>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "各饮品（按杯型）统计",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (stats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无数据",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                stats.forEachIndexed { index, s ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${s.recipeName} · ${s.cupSize}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${formatNumber(s.totalQuantity)} 杯  ${formatCurrency(s.totalRevenue)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (index < stats.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}