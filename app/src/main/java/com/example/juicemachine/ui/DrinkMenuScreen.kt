package com.example.juicemachine.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.juicemachine.R
import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.ui.theme.AccentOrange
import com.example.juicemachine.ui.viewmodel.DrinkMenuUiState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.StopCircle
import com.example.juicemachine.ui.theme.FreshGreen
import com.example.juicemachine.ui.theme.FreshOrange
import com.example.juicemachine.ui.theme.FreshRed
import com.example.juicemachine.ui.model.IceMode
// 移除下单页温度/重量图标后，相关导入不再需要

private fun getDrawableForRecipe(recipeName: String): Int {
    return when (recipeName) {
        "茉莉雪芽" -> R.drawable.mo_li_xue_ya3
        "柳橙百香" -> R.drawable.liu_cheng_bai_xiang3
        "鸭屎香柠檬茶" -> R.drawable.ya_shi_xiang3
        // 兼容旧名称：满杯桑葚 已被鸭屎香柠檬茶替换
        "满杯桑葚" -> R.drawable.ya_shi_xiang3
        else -> R.drawable.placeholder
    }
}

@Composable
    fun DrinkMenuScreen(
        uiState: DrinkMenuUiState,
        onRecipeClick: (Recipe) -> Unit,
        onHeaderLongClick: () -> Unit,
        onWaterOnlyToggle: () -> Unit,
        onEmergencyStop: () -> Unit,
        onDismissDialog: () -> Unit,
        onConfirmDialog: (Recipe, String, IceMode) -> Unit,
        onLoginAttempt: (String) -> Unit,
        onDismissError: () -> Unit,
        onContinueRecipe: () -> Unit,    // 预留
        onRestartRecipe: () -> Unit,      // 预留
        onDismissWeighResult: () -> Unit,
        onCancelWeighWaiting: () -> Unit,
    // 无操作超时返回广告页
    onTimeoutToAds: () -> Unit = {},
    timeoutMs: Long = 60_000L,
    onResetStock: (Recipe) -> Unit = {}
) {
    // 新增：用于展示全局提示的 Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // 新增：全局交互监听与超时
    var lastInteraction by remember { mutableStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(
        timeoutMs,
        uiState.selectedRecipe,
        uiState.showLoginDialog,
        uiState.showWeighWaitingDialog,
        uiState.showWeightChangeDialog,
        uiState.weighResultMessage,
        uiState.isWaterOnlyActive
    ) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            val now = SystemClock.uptimeMillis()
            val interacting = (
                uiState.selectedRecipe != null ||
                uiState.showLoginDialog ||
                uiState.showWeighWaitingDialog ||
                uiState.showWeightChangeDialog ||
                uiState.weighResultMessage != null ||
                // 加水期间视为持续交互：暂停广告页计时
                uiState.isWaterOnlyActive
            )
            if (interacting) {
                // 弹窗或对话框可见时认为用户仍在交互，持续重置计时
                lastInteraction = now
                continue
            }
            if (now - lastInteraction >= timeoutMs) {
                onTimeoutToAds()
                break
            }
        }
    }

    // 新增：停止只出水后，重置一次广告页倒计时
    LaunchedEffect(uiState.isWaterOnlyActive) {
        if (!uiState.isWaterOnlyActive) {
            lastInteraction = SystemClock.uptimeMillis()
        }
    }

    // 新增：硬件中性完成事件触发的广告页倒计时重置
    LaunchedEffect(uiState.adsResetTick) {
        // 每次计数变化都重置最近交互时间
        lastInteraction = SystemClock.uptimeMillis()
    }

    Box(
        Modifier
            .fillMaxSize()
            // 捕获任意触摸事件以重置计时
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
        Column(Modifier.fillMaxSize()) {
            Header(
                onLongClick = onHeaderLongClick,
                onWaterOnlyToggle = onWaterOnlyToggle,
                onEmergencyStop = onEmergencyStop,
                isWaterOnlyActive = uiState.isWaterOnlyActive
            )
            DrinkGrid(
                recipes = uiState.recipes,
                onRecipeSelected = onRecipeClick,
                onResetStock = onResetStock,
                modifier = Modifier.weight(1f)
            )
        }

        // 其他错误消息用 Snackbar 展示 - 仅在没有重量异常弹窗时触发
        val showDialog = uiState.showWeightChangeDialog
        LaunchedEffect(uiState.errorMessage, showDialog) {
            val msg = uiState.errorMessage
            if (!showDialog && msg != null) {
                snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
                onDismissError()
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    uiState.selectedRecipe?.let { selected ->
        JuiceCustomizationDialog(
            recipe = selected,
            onConfirm = { cup, mode -> onConfirmDialog(selected, cup, mode) },
            onDismiss = onDismissDialog,
            onAnyInteraction = { lastInteraction = SystemClock.uptimeMillis() }
        )
    }

    if (uiState.showLoginDialog) {
        LoginDialog(
            isError = uiState.loginError,
            onConfirm = onLoginAttempt,
            onDismiss = onDismissDialog
        )
    }

    // 新增：称重等待与结果弹窗（全局）
    if (uiState.showWeighWaitingDialog) {
        Dialog(onDismissRequest = onCancelWeighWaiting) {
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(12.dp),
                modifier = Modifier.pointerInput(Unit) {
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
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在称重，请稍候…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    uiState.weighResultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = onDismissWeighResult,
            title = { Text("称重结果") },
            text = { Text(msg) },
            confirmButton = { Button(onClick = onDismissWeighResult) { Text("知道了") } }
        )
    }
}

@Composable
// 新增：显式 OptIn 实验性 API
@OptIn(ExperimentalFoundationApi::class)
fun Header(
    onLongClick: () -> Unit,
    onWaterOnlyToggle: () -> Unit,
    onEmergencyStop: () -> Unit,
    isWaterOnlyActive: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier.size(30.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f))
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "🍊", fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "果然新鲜",
                        style = MaterialTheme.typography.headlineSmall,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "FreshFruit",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                // 右侧操作区：急停 + 只出水（移除温度/重量状态芯片）
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 急停：仅保留三角警示图标（不显示文字）
                    IconButton(
                        onClick = onEmergencyStop,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "急停",
                            tint = FreshRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    // 只出水：水滴图标，高亮时用绿色，未激活用浅色
                    IconButton(onClick = onWaterOnlyToggle, colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.15f))) {
                        Icon(
                            imageVector = Icons.Filled.WaterDrop,
                            contentDescription = "只出水",
                            tint = if (isWaterOnlyActive) FreshGreen else Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DrinkGrid(
    recipes: List<Recipe>,
    onRecipeSelected: (Recipe) -> Unit,
    onResetStock: (Recipe) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hPadding = 8.dp
    val vPadding = 6.dp

    if (recipes.size <= 3) {
        // 严格三等分：单行平均分成3块，并让卡片高度占满可用区域
        Box(
            modifier = modifier
                .padding(horizontal = hPadding, vertical = vPadding)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recipes.forEach { recipe ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        DrinkCard(recipe = recipe, onRecipeSelected = onRecipeSelected, onResetStock = onResetStock, expandToHeight = true)
                    }
                }
                // 若少于3个，使用占位空格保持三等分
                repeat((3 - recipes.size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    } else {
        // 多于3个：保持三列网格与滚动
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = modifier
                .padding(horizontal = hPadding, vertical = vPadding)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(recipes, key = { it.id }) { recipe ->
                DrinkCard(recipe = recipe, onRecipeSelected = onRecipeSelected, onResetStock = onResetStock)
            }
        }
    }
}

@Composable
fun DrinkCard(
    recipe: Recipe,
    onRecipeSelected: (Recipe) -> Unit,
    onResetStock: (Recipe) -> Unit = {},
    expandToHeight: Boolean = false
) {
    val isSoldOut = recipe.currentRemainingWeight < recipe.juice
    val isLowStock = !isSoldOut && (recipe.currentRemainingWeight < recipe.juice * 3)
    // 计算默认占位图与库存比例
    val defaultPainter = painterResource(id = getDrawableForRecipe(recipe.name))
    val stockRatio = if (recipe.defaultRemainingWeight <= 0) 0f else
        (recipe.currentRemainingWeight.toFloat() / recipe.defaultRemainingWeight.toFloat()).coerceIn(0f, 1f)
    var showResetDialog by remember { mutableStateOf(false) }

    Card(
        onClick = { onRecipeSelected(recipe) },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp,
            hoveredElevation = 10.dp
        ),
        enabled = !isSoldOut,
        colors = CardDefaults.cardColors(
            containerColor = if (isSoldOut) Color.Gray.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = if (expandToHeight) Modifier.fillMaxSize() else Modifier.aspectRatio(0.75f)
        ) {
            // 背景图：优先显示本地保存图片
            if (!recipe.imageUri.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(recipe.imageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = recipe.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = if (isSoldOut) 0.3f else 1.0f,
                    error = defaultPainter,
                    fallback = defaultPainter,
                    onError = { error ->
                        Log.d("DrinkCard", "Image load error for ${recipe.name}: ${error.result.throwable.message}")
                    }
                )
            } else {
                Image(
                    painter = defaultPainter,
                    contentDescription = recipe.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = if (isSoldOut) 0.3f else 1.0f
                )
            }

            // 价格与标签
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                PriceTag(price = recipe.price)
                // 调整：移除这里的“鲜料重置”按钮，避免与价格区紧贴
                // 低库存提醒
                if (!isSoldOut && isLowStock) {
                    LowStockBadge()
                }
            }
            // 新位置：将“鲜料重置”按钮放置在顶部居中，介于左上售罄标记与右上价格区域之间
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                // 仅在售罄时显示“鲜料重置”按钮
                if (isSoldOut) {
                    ResetStockButton(onLongPress = { showResetDialog = true })
                }
            }
            if (showResetDialog) {
                ResetStockDialog(
                    recipe = recipe,
                    onConfirm = {
                        onResetStock(recipe)
                        showResetDialog = false
                    },
                    onDismiss = { showResetDialog = false }
                )
            }

            // 售罄标记固定左上角
            if (isSoldOut) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) { SoldOutTag() }
            }
            // 移除售罄时的“鲜料重置”按钮


            // （删除中间悬浮标题）
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x80000000))
                    .padding(8.dp)
            ) {
                Text(
                    text = recipe.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val stockRatio = if (recipe.defaultRemainingWeight <= 0) 0f else
                        (recipe.currentRemainingWeight.toFloat() / recipe.defaultRemainingWeight.toFloat()).coerceIn(0f, 1f)
                    val stockColor = when {
                        stockRatio >= 0.5f -> Color(0xFF4CAF50)
                        stockRatio >= 0.1f -> MaterialTheme.colorScheme.primary
                        else -> Color(0xFFF44336)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(stockRatio)
                                .clip(RoundedCornerShape(4.dp))
                                .background(stockColor)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val cups = if (recipe.juice <= 0) 0 else recipe.currentRemainingWeight / recipe.juice
                    Text(
                        text = "约${cups}杯",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
            }
        }
}
}

@Composable
fun PriceTag(price: Int) {
    Box(
        modifier = Modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AccentOrange.copy(alpha = 0.95f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "¥$price",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun SoldOutTag() {
    Card(
        modifier = Modifier.padding(8.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F))
    ) {
        Text(
            text = "售罄",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun LowStockBadge() {
    Card(
        modifier = Modifier.padding(8.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA000))
    ) {
        Text(
            text = "库存低",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ResetStockButton(onLongPress: () -> Unit) {
    Button(
        onClick = onLongPress, // 同时支持单击触发，提升可发现性
        modifier = Modifier
            .widthIn(min = 120.dp) // 加宽按钮
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text("鲜料重置", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun ResetStockDialog(
    recipe: Recipe,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("鲜料重置确认") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "将把“${recipe.name}”的鲜料剩余重置为默认库存值。",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { input ->
                        password = input
                        // 当输入非空且不为“0”时即时提示错误
                        isError = input.isNotBlank() && input != "0"
                    },
                    label = { Text("请输入密码") },
                    singleLine = true,
                    isError = isError,
                    visualTransformation = PasswordVisualTransformation()
                )
                if (isError) {
                    Text(
                        text = "密码错误",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // 只有在密码为“0”时按钮才可点击，此处直接确认
                    isError = false
                    onConfirm()
                },
                enabled = password == "0"
            ) { Text("确认重置") }
        },
        dismissButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text("取消")
            }
        }
    )
}

@Composable
fun JuiceCustomizationDialog(
    recipe: Recipe,
    onConfirm: (cupSize: String, iceMode: IceMode) -> Unit,
    onDismiss: () -> Unit,
    onAnyInteraction: () -> Unit = {}
) {
    var iceMode by remember { mutableStateOf(IceMode.NORMAL) }
    var cupSize by remember { mutableStateOf("中杯") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onAnyInteraction()
                            try { tryAwaitRelease() } finally { onAnyInteraction() }
                        },
                        onTap = { onAnyInteraction() },
                        onLongPress = { onAnyInteraction() },
                        onDoubleTap = { onAnyInteraction() }
                    )
                },
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "定制您的${recipe.name}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 价格显示（大杯 +2）
                val displayPrice = if (cupSize == "大杯") recipe.price + 2 else recipe.price
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "¥$displayPrice",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 杯型选择
                Text(
                    text = "杯型选择",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        onClick = { cupSize = "中杯" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (cupSize == "中杯") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🥤", fontSize = 24.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("中杯", fontWeight = FontWeight.Bold, color = if (cupSize == "中杯") Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Card(
                        onClick = { cupSize = "大杯" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (cupSize == "大杯") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🍺", fontSize = 24.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("大杯", fontWeight = FontWeight.Bold, color = if (cupSize == "大杯") Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("+¥2", fontSize = 10.sp, color = if (cupSize == "大杯") Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 冰度/热饮选择
                Text(
                    text = "冰度选择",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        onClick = { iceMode = IceMode.NORMAL },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (iceMode == IceMode.NORMAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .height(96.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("❄️", fontSize = 32.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("正常冰", fontWeight = FontWeight.Bold, color = if (iceMode == IceMode.NORMAL) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Card(
                        onClick = { iceMode = IceMode.NO_ICE },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (iceMode == IceMode.NO_ICE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .height(96.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🌡️", fontSize = 32.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("去冰", fontWeight = FontWeight.Bold, color = if (iceMode == IceMode.NO_ICE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Card(
                        onClick = { iceMode = IceMode.HOT },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (iceMode == IceMode.HOT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .height(96.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("☕️", fontSize = 32.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("热饮", fontWeight = FontWeight.Bold, color = if (iceMode == IceMode.HOT) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("取消", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)) }
                    Button(
                        onClick = {
                            val modeText = when (iceMode) { IceMode.NORMAL -> "正常冰"; IceMode.NO_ICE -> "去冰"; IceMode.HOT -> "热饮" }
                            Log.d("JuiceCustomizationDialog", "确认制作: 杯型=$cupSize, 冰度=$modeText")
                            onConfirm(cupSize, iceMode)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("开始制作", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)) }
                }
            }
        }
    }
}

@Composable
fun LoginDialog(
    isError: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val shake = remember { Animatable(0f) }

    // 错误时触发 Haptic 与抖动
    LaunchedEffect(isError) {
        if (isError) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // 轻微抖动：-6..+6..0 px
            shake.snapTo(0f)
            val seq = listOf(-6f, 6f, -4f, 4f, -2f, 2f, 0f)
            for (x in seq) {
                shake.animateTo(x, animationSpec = tween(durationMillis = 30))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理员登录") },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("请输入密码") },
                    isError = isError,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.offset(x = with(density) { shake.value.dp })
                )
                AnimatedVisibility(visible = isError, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        "密码错误，请重试",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(password) }, enabled = password.isNotBlank()) { Text("确认") }
        },
        dismissButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text("取消")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DrinkMenuScreenPreview() {
    val dummyRecipes = listOf(
        Recipe(id = 1, name = "茉莉雪芽", water = 105, juice = 175, price = 8, defaultRemainingWeight = 1000, currentRemainingWeight = 1000, juiceChannel = 1, imageUri = null),
        Recipe(id = 2, name = "柳橙百香", water = 180, juice = 100, price = 9, defaultRemainingWeight = 30, currentRemainingWeight = 30, juiceChannel = 2, imageUri = null),
        Recipe(id = 3, name = "鸭屎香柠檬茶", water = 130, juice = 150, price = 10, defaultRemainingWeight = 0, currentRemainingWeight = 0, juiceChannel = 3, imageUri = null)
    )
    val previewState = DrinkMenuUiState(
        recipes = dummyRecipes
    )
    DrinkMenuScreen(
        uiState = previewState,
        onRecipeClick = {},
        onHeaderLongClick = {},
        onWaterOnlyToggle = {},
        onEmergencyStop = {},
        onDismissDialog = {},
        onConfirmDialog = { _, _, _ -> },
        onLoginAttempt = {},
        onDismissError = {},
        onContinueRecipe = {},
        onRestartRecipe = {},
        onDismissWeighResult = {},
        onCancelWeighWaiting = {}
    )
}

// 新增：全局使用的重量变化弹窗，供 AppNavigation 调用
@Composable
fun WeightChangeDialog(
    onContinue: () -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = FreshRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("检测到杯子被移动", color = FreshRed)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("制作过程中检测到重量发生变化，请选择操作：")
                Text("请清掉饮品并且重新放置杯子！", color = FreshRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FreshOrange, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("继续制作")
                }
                Button(
                    onClick = onRestart,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FreshGreen, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("重新制作")
                }
            }
        },
        dismissButton = {}
    )
}
