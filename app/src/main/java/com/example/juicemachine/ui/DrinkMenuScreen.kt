package com.example.juicemachine.ui

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.juicemachine.R
import com.example.juicemachine.data.database.CupConfig
import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.ui.theme.JuiceMachineTheme
import com.example.juicemachine.ui.theme.AccentOrange
import com.example.juicemachine.ui.viewmodel.DrinkMenuUiState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.text.style.TextOverflow

private fun getDrawableForRecipe(recipeName: String): Int {
    return when (recipeName) {
       "茉莉雪芽" -> R.drawable.mo_li_xue_ya
       "柳橙百香" -> R.drawable.liu_cheng_bai_xiang
       "满杯桑葚" -> R.drawable.man_bei_sang_shen 
        else -> R.drawable.placeholder
    }
}

@Composable
fun DrinkMenuScreen(
    uiState: DrinkMenuUiState,
    onRecipeClick: (Recipe) -> Unit,
    onHeaderLongClick: () -> Unit,
    onDismissDialog: () -> Unit,
    onConfirmDialog: (Recipe, String, Boolean) -> Unit,
    onLoginAttempt: (String) -> Unit,
    onDismissError: () -> Unit,
    onSimulateWeightChange: () -> Unit,  // 新增
    onContinueRecipe: () -> Unit,         // 新增
    onRestartRecipe: () -> Unit           // 新增
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header(
                onLongClick = onHeaderLongClick,
                temperature = uiState.temperature
            )
            DrinkGrid(
                recipes = uiState.recipes,
                onRecipeSelected = onRecipeClick
            )
            
            // 新增：测试按钮
            Button(
                onClick = onSimulateWeightChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("模拟杯子移动检测", color = MaterialTheme.colorScheme.onSecondary)
            }
        }

        // The bottom action buttons are removed as they will be moved to the Admin screen.
        /*
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(text = "自动加水", onClick = onAddWater)
                ActionButton(text = "温度测试", onClick = onTestTemp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(text = "一键清洗", onClick = onClean)
                ActionButton(text = "开始制作", onClick = onMakeJuice, isPrimary = true)
            }
        }
        */
    }

    if (uiState.selectedRecipe != null) {
        JuiceCustomizationDialog(
            recipe = uiState.selectedRecipe,
            onConfirm = { cupSize, withIce ->
                onConfirmDialog(uiState.selectedRecipe, cupSize, withIce)
            },
            onDismiss = onDismissDialog
        )
    }

    if (uiState.showLoginDialog) {
        LoginDialog(
            isError = uiState.loginError,
            onConfirm = onLoginAttempt,
            onDismiss = onDismissDialog
        )
    }

    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { onDismissError() },
            title = { Text("提示") },
            text = { Text(uiState.errorMessage) },
            confirmButton = {
                Button(onClick = { onDismissError() }) { Text("确定") }
            }
        )
    }
    
    // 新增：重量变化弹窗
    if (uiState.showWeightChangeDialog) {
        WeightChangeDialog(
            onContinue = onContinueRecipe,
            onRestart = onRestartRecipe,
            onDismiss = { /* 可以添加取消逻辑 */ }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Header(
    onLongClick: () -> Unit,
    temperature: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 品牌logo区域
                Card(
                    modifier = Modifier.size(36.dp), // 进一步减小logo尺寸
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.2f)
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🍊",
                            fontSize = 18.sp // 减小emoji大小
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp)) // 减少间距
                
                // 品牌名称
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "果然新鲜",
                        style = MaterialTheme.typography.headlineSmall,
                        fontSize = 20.sp, // 减小字体
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "FreshFruit",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp, // 减小字体
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                
                Spacer(Modifier.weight(1f))

                // 温度显示区域
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.2f)
                    )
                ) {
                    val displayTemp = if (temperature == "未连接" || temperature.isBlank()) "--°" else temperature
                    Text(
                        text = "温度: $displayTemp",
                        fontSize = 14.sp, // 减小温度字体
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp) // 减少padding
                    )
                }
            }
        }
    }
}

@Composable
fun DrinkGrid(recipes: List<Recipe>, onRecipeSelected: (Recipe) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3), // 固定3列，更适合平板屏幕
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(400.dp), // 固定高度，为按钮留出空间
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(recipes) { recipe ->
            DrinkCard(recipe, onRecipeSelected)
        }
    }
}

@Composable
fun DrinkCard(recipe: Recipe, onRecipeSelected: (Recipe) -> Unit) {
    val defaultPainter = painterResource(id = getDrawableForRecipe(recipe.name))

    val isSoldOut = recipe.remainWeight < recipe.juice
    val isLowStock = !isSoldOut && (recipe.remainWeight / recipe.juice) in 1..3

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
            modifier = Modifier.aspectRatio(0.85f) // 调整比例以更好适应图片比例
        ) {
            // Background Image - 优先显示保存的图片，否则显示默认图片
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
                    contentScale = ContentScale.Fit,
                    alpha = if (isSoldOut) 0.3f else 1.0f
                )
            }

            if (isSoldOut) {
                // 售罄遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Red.copy(alpha = 0.9f)
                        )
                    ) {
                        Text(
                            text = "售罄",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 内容区域
            Box(modifier = Modifier.fillMaxSize()) {
                // 底部信息区域
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                        .padding(12.dp)
                ) {
                    // 饮品名称
                    Text(
                        text = recipe.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 18.sp, // 增大字体
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 库存状态条和剩余重量
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 库存状态条
                        val stockRatio = (recipe.remainWeight.toFloat() / (recipe.juice * 10)).coerceIn(0f, 1f)
                        val stockColor = when {
                            stockRatio >= 0.5f -> Color(0xFF4CAF50) // 绿色
                            stockRatio >= 0.1f -> Color(0xFFFF9800) // 橙色
                            else -> Color(0xFFF44336) // 红色
                        }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(stockRatio)
                                    .fillMaxHeight()
                                    .background(stockColor, RoundedCornerShape(3.dp))
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // 剩余重量 - 放在绿色条右边
                        Text(
                            text = "${recipe.remainWeight}g",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp, // 增大字体
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // 价格标签
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "¥${recipe.price}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun JuiceCustomizationDialog(
    recipe: Recipe,
    onConfirm: (cupSize: String, withIce: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var withIce by remember { mutableStateOf(true) } // Default to normal ice
    var cupSize by remember { mutableStateOf("中杯") } // Default to medium cup

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题区域
                Text(
                    text = "定制您的${recipe.name}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "请选择您的偏好",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 价格显示
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    val displayPrice = when(cupSize) {
                        "大杯" -> recipe.price + 2
                        else -> recipe.price
                    }
                    Text(
                        text = "¥${displayPrice}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                // 杯型选择
                Text(
                    text = "杯型选择",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 中杯按钮
                    Card(
                        onClick = { cupSize = "中杯" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (cupSize == "中杯") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (cupSize == "中杯") 8.dp else 2.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🥤",
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "中杯",
                                fontWeight = FontWeight.Bold,
                                color = if (cupSize == "中杯") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "标准份量",
                                fontSize = 10.sp,
                                color = if (cupSize == "中杯") Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    // 大杯按钮
                    Card(
                        onClick = { cupSize = "大杯" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (cupSize == "大杯") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (cupSize == "大杯") 8.dp else 2.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🍺",
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "大杯",
                                fontWeight = FontWeight.Bold,
                                color = if (cupSize == "大杯") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "+¥2",
                                fontSize = 10.sp,
                                color = if (cupSize == "大杯") Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 冰度选择
                Text(
                    text = "冰度选择",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 正常冰按钮
                    Card(
                        onClick = { withIce = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (withIce) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (withIce) 8.dp else 2.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "❄️",
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "正常冰",
                                fontWeight = FontWeight.Bold,
                                color = if (withIce) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 去冰按钮
                    Card(
                        onClick = { withIce = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!withIce) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (!withIce) 8.dp else 2.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🌡️",
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "去冰",
                                fontWeight = FontWeight.Bold,
                                color = if (!withIce) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 取消按钮
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "取消",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    
                    // 确认按钮
                    Button(
                        onClick = { 
                            // 添加调试信息
                            Log.d("JuiceCustomizationDialog", "确认制作: 杯型=$cupSize, 冰度=${if(withIce) "正常冰" else "去冰"}")
                            onConfirm(cupSize, withIce) 
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "开始制作",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
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
                    visualTransformation = PasswordVisualTransformation()
                )
                if (isError) {
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
            Button(onClick = { onConfirm(password) }) {
                Text("确认")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
fun WeightChangeDialog(
    onContinue: () -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "警告",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "检测到杯子被移动",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "制作过程中检测到重量变化，请选择操作：",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "提示",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "请倒掉饮品并重新放置杯子！",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "继续",
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "继续制作",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
                
                Button(
                    onClick = onRestart,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新制作",
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "重新制作",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }
        },
        dismissButton = null
    )
}

@Preview(showBackground = true)
@Composable
fun DrinkMenuScreenPreview() {
    val previewRecipes = listOf(
        Recipe(id = 1, name = "茉莉雪芽", water = 105, juice = 175, price = 8, remainWeight = 1000, juiceChannel = 1, imageUri = null),
        Recipe(id = 2, name = "柳橙百香", water = 180, juice = 100, price = 9, remainWeight = 3, juiceChannel = 2, imageUri = null),
        Recipe(id = 3, name = "满杯桑葚", water = 130, juice = 150, price = 10, remainWeight = 0, juiceChannel = 3, imageUri = null)
    )
    JuiceMachineTheme {
        DrinkMenuScreen(
            uiState = DrinkMenuUiState(recipes = previewRecipes, temperature = "25℃"),
            onRecipeClick = {},
            onHeaderLongClick = {},
            onDismissDialog = {},
            onConfirmDialog = { _, _, _ -> },
            onLoginAttempt = {},
            onDismissError = {},
            onSimulateWeightChange = {},
            onContinueRecipe = {},
            onRestartRecipe = {}
        )
    }
}