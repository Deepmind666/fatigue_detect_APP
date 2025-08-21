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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juicemachine.JuiceMachineApplication
import com.example.juicemachine.data.database.DailySalesStats
import com.example.juicemachine.data.database.PopularRecipeStats
import com.example.juicemachine.data.database.InventoryConsumptionStats
import com.example.juicemachine.data.database.RecipeCupStats
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    uiState: StatisticsUiState,
    onNavigateBack: () -> Unit,
    onTimePeriodChanged: (TimePeriod) -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "销售统计", 
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
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
            
            // 时间段选择器
            item {
                TimePeriodSelector(
                    selectedPeriod = uiState.selectedTimePeriod,
                    onPeriodSelected = onTimePeriodChanged
                )
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
            value = "¥$totalRevenue",
            icon = Icons.Default.AccountBalance,
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "总订单",
            value = "$totalOrders",
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
            value = "¥$todayRevenue",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            color = Color(0xFFFF9800),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "今日订单",
            value = "$todayOrders",
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
    @Suppress("UNUSED_PARAMETER") timePeriod: TimePeriod
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
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (dailySales.isNotEmpty()) {
                SimpleLineChart(
                    data = dailySales,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
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

@Composable
private fun SimpleLineChart(
    data: List<DailySalesStats>,
    modifier: Modifier = Modifier
) {
    val maxRevenueRaw = data.maxOfOrNull { it.totalRevenue } ?: 0
    val maxRevenue = max(1f, maxRevenueRaw.toFloat())
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val leftPadding = 12.dp.toPx()
        val rightPadding = 12.dp.toPx()
        val topPadding = 12.dp.toPx()
        val bottomPadding = 18.dp.toPx()

        val width = size.width - leftPadding - rightPadding
        val height = size.height - topPadding - bottomPadding
        val stepX = if (data.size <= 1) 0f else width / (data.size - 1)

        // 映射点
        val points = data.mapIndexed { index, stats ->
            val x = leftPadding + index * stepX
            val ratio = (stats.totalRevenue / maxRevenue).coerceIn(0f, 1f)
            val y = topPadding + (1f - ratio) * height
            Offset(x, y)
        }

        // 网格和坐标轴
        val gridColor = onSurfaceVariant.copy(alpha = 0.15f)
        val axisColor = onSurfaceVariant.copy(alpha = 0.25f)
        val levels = 4
        repeat(levels + 1) { i ->
            val y = topPadding + height * i / levels
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, y),
                end = Offset(size.width - rightPadding, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        // X 轴
        drawLine(
            color = axisColor,
            start = Offset(leftPadding, topPadding + height),
            end = Offset(size.width - rightPadding, topPadding + height),
            strokeWidth = 1.5.dp.toPx()
        )

        // 平滑折线路径（Quadratic Bezier）
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            if (points.size >= 2) {
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val cur = points[i]
                    val midX = (prev.x + cur.x) / 2f
                    val midY = (prev.y + cur.y) / 2f
                    quadraticBezierTo(prev.x, prev.y, midX, midY)
                }
                // 收尾到最后一个点
                val last = points.last()
                quadraticBezierTo(points[points.size - 2].x, points[points.size - 2].y, last.x, last.y)
            }
        }

        // 渐变填充区域（当只有一个点时，填充区域退化为零宽，不会造成错误）
        val fillPath = Path().apply {
            addPath(linePath)
            // 从最后一个点向下闭合到底部轴线
            lineTo(points.last().x, topPadding + height)
            lineTo(points.first().x, topPadding + height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primary.copy(alpha = 0.25f), Color.Transparent),
                startY = topPadding,
                endY = topPadding + height
            )
        )

        // 折线描边（单点时只会绘制起点，无异常）
        drawPath(
            path = linePath,
            color = primary,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // 数据点
        val outer = primary
        val inner = Color.White
        val outerR = 4.dp.toPx()
        val innerR = 2.dp.toPx()
        points.forEach { p ->
            drawCircle(color = outer, radius = outerR, center = p)
            drawCircle(color = inner, radius = innerR, center = p)
        }
    }
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
                text = "销量: ${recipe.totalQuantity} | 营收: ¥${recipe.totalRevenue}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = "¥${recipe.avgPrice.toInt()}",
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
        onDismissError = vm::onDismissError
    )
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
                            text = "${s.totalQuantity} 杯  ¥${s.totalRevenue}",
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