package com.example.juicemachine.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.util.Log
import com.example.juicemachine.R
import com.example.juicemachine.data.database.CupConfig
import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.ui.theme.JuiceMachineTheme
import com.example.juicemachine.ui.viewmodel.DrinkMenuUiState
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipeScreen(
    recipe: Recipe,
    onNameChange: (String) -> Unit,
    onWaterChange: (String) -> Unit,
    onJuiceChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onStockChange: (String) -> Unit,
    // 新增：当前剩余重量手动设定
    onCurrentRemainWeightChange: (String) -> Unit,
    onJuiceTypeChange: (String) -> Unit,
    onJuiceChannelChange: (String) -> Unit,
    onHasPulpChange: (Boolean) -> Unit,
    onJuiceSpeedChange: (String) -> Unit,
    // 新增：果肉补偿相关参数（按饮品独立）
    onPulpTotalCupsChange: (String) -> Unit,
    onPulpDecIntervalChange: (String) -> Unit,
    onPulpDecAmountChange: (String) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    onImageSelected: (Uri?) -> Unit = {}, // 新增：图片选择回调
    selectedImageUri: Uri? = null // 新增：选中的图片URI
) {
    val isNewRecipe = recipe.id == 0
    val isSavable = recipe.name.isNotBlank() && recipe.water > 0 && recipe.juice > 0 && recipe.price > 0 && recipe.juiceChannel in 1..3

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        try {
            if (uri != null) {
                Log.d("EditRecipeScreen", "图片选择成功: $uri")
                onImageSelected(uri)
            } else {
                Log.d("EditRecipeScreen", "用户取消了图片选择")
            }
        } catch (e: Exception) {
            Log.e("EditRecipeScreen", "图片选择处理失败: ${e.message}", e)
            onImageSelected(null)
        }
    }

    // 状态管理
    var showCropDialog by remember { mutableStateOf(false) }
    var showScaleDialog by remember { mutableStateOf(false) }
    // 新增：保存确认弹窗
    var showConfirmSave by remember { mutableStateOf(false) }
    
    // 调试日志
    LaunchedEffect(selectedImageUri) {
        Log.d("EditRecipeScreen", "selectedImageUri changed: $selectedImageUri")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewRecipe) "添加新配方" else "编辑配方") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { if (isSavable) showConfirmSave = true }, enabled = isSavable) {
                        Icon(Icons.Filled.Done, contentDescription = "保存")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showConfirmSave = true }) {
                Icon(Icons.Filled.Done, contentDescription = "保存")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            // 优化：图片选择区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp), // 增加高度以容纳控制按钮
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 图片显示区域
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clickable {
                                Log.d("EditRecipeScreen", "点击选择图片区域，启动图片选择器")
                                try {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                    Log.d("EditRecipeScreen", "图片选择器启动成功")
                                } catch (e: Exception) {
                                    Log.e("EditRecipeScreen", "启动图片选择器失败: ${e.message}", e)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // 优先显示新选择的图片，其次显示已保存的图片
                        val imageToShow = selectedImageUri ?: recipe.imageUri?.let { it.toUri() }
                        
                        if (imageToShow != null) {
                            // 显示选中的图片 - 使用自适应缩放
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageToShow)
                                    .crossfade(true)
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = "饮品图片",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop, // 使用Crop确保填充整个区域
                                onSuccess = { _ ->
                                    Log.d("EditRecipeScreen", "图片加载成功: $imageToShow")
                                },
                                onError = { error ->
                                    Log.e("EditRecipeScreen", "图片加载失败: ${error.result.throwable.message}")
                                }
                            )
                        } else {
                            // 显示默认图片（根据配方名称映射到drawable-nodpi的新图片）
                            val defaultRes = getDrawableForRecipe(recipe.name)
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = defaultRes),
                                contentDescription = "默认饮品图片",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    
                    // 控制按钮区域 - 底部
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 选择图片按钮
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            )
                        ) {
                            IconButton(
                                onClick = {
                                    Log.d("EditRecipeScreen", "点击图片选择按钮")
                                    try {
                                        imagePickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                        Log.d("EditRecipeScreen", "图片选择器启动成功")
                                    } catch (e: Exception) {
                                        Log.e("EditRecipeScreen", "启动图片选择器失败: ${e.message}", e)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Filled.PhotoCamera,
                                    contentDescription = "选择图片",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        // 裁剪按钮（仅在有图片时显示）
                        if (selectedImageUri != null) {
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
                                )
                            ) {
                                IconButton(
                                    onClick = {
                                        Log.d("EditRecipeScreen", "点击裁剪按钮，selectedImageUri: $selectedImageUri")
                                        showCropDialog = true
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Crop,
                                        contentDescription = "裁剪图片",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        
                        // 缩放按钮（仅在有图片时显示）
                        if (selectedImageUri != null) {
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
                                )
                            ) {
                                IconButton(
                                    onClick = {
                                        Log.d("EditRecipeScreen", "点击缩放按钮，selectedImageUri: $selectedImageUri")
                                        showScaleDialog = true
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.ZoomIn,
                                        contentDescription = "缩放图片",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                    }
                    
                    // 图片信息提示（移动到 Row 外，在 BoxScope 中对齐）
                    if (selectedImageUri != null) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.7f)
                            )
                        ) {
                            Text(
                                text = "已选择图片",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = recipe.name,
                onValueChange = onNameChange,
                label = { Text("配方名称") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Helper function for validating numeric input within a range
            val validateInRange: (String, IntRange, (String) -> Unit) -> Unit = { input, range, onValidChange ->
                // 兼容不同键盘产生的全角/上标数字：先做 NFKC 规范化，再仅保留 0-9
                val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC)
                val filteredInput = normalized.filter { it in '0'..'9' }
                if (filteredInput.isBlank()) {
                    onValidChange("")
                } else {
                    val value = filteredInput.toIntOrNull() ?: 0
                    onValidChange(value.coerceIn(range).toString())
                }
            }

            OutlinedTextField(
                value = recipe.water.toString(),
                onValueChange = { validateInRange(it, 0..255, onWaterChange) },
                label = { Text("水量 (ml) [0-255]") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 移除：水流速输入（水速为全局设置，不随配方变化）
            OutlinedTextField(
                value = recipe.juice.toString(),
                onValueChange = { validateInRange(it, 0..255, onJuiceChange) },
                label = { Text("果汁量 (ml) [0-255]") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = recipe.price.toString(),
                onValueChange = onPriceChange,
                label = { Text("价格 (元)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = recipe.defaultRemainingWeight.toString(),
                onValueChange = onStockChange,
                label = { Text("库存(克)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 新增：当前剩余重量（手动设定）
            OutlinedTextField(
                value = recipe.currentRemainingWeight.toString(),
                onValueChange = { input ->
                    val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC)
                    val digits = normalized.filter { it in '0'..'9' }
                    val value = digits.toIntOrNull() ?: 0
                    onCurrentRemainWeightChange(value.coerceAtLeast(0).toString())
                },
                label = { Text("当前剩余重量(克)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 新增：果汁类型（自由文本）
            OutlinedTextField(
                value = recipe.juiceType,
                onValueChange = { input ->
                    // 允许中英文及常见符号；移除首尾空格但保留中间空格
                    val normalized = input.trim()
                    onJuiceTypeChange(normalized)
                },
                label = { Text("果汁类型") },
                placeholder = { Text("例如：橙汁 / 百香果 / 柠檬茶") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = recipe.juiceChannel.toString(),
                onValueChange = { raw ->
                    // 通道只允许单个数字（1..3）：取最后一位有效数字，避免“12”被整体解析成 12 后被强制夹到 3
                    val normalized = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC)
                    val digits = normalized.filter { it in '0'..'9' }
                    if (digits.isEmpty()) {
                        onJuiceChannelChange("")
                    } else {
                        val last = digits.last().digitToInt()
                        val clamped = last.coerceIn(1, 3)
                        onJuiceChannelChange(clamped.toString())
                    }
                },
                label = { Text("果汁通道 (1-3)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 果汁流速：上移到果汁通道之上，两个模式通用
            OutlinedTextField(
                value = recipe.juiceSpeed.toString(),
                onValueChange = { input -> validateInRange(input, 0..255, onJuiceSpeedChange) },
                label = { Text("果汁流速 (0-255)") },
                placeholder = { Text("0 表示设备默认") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 新增：是否含果肉
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "含果肉", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = recipe.hasPulp,
                    onCheckedChange = onHasPulpChange
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            // 新增：果肉补偿参数（仅在含果肉为 true 时启用）
            Text(text = "果肉补偿参数", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = recipe.pulpTotalCups.toString(),
                    onValueChange = { input -> validateInRange(input, 1..255, onPulpTotalCupsChange) },
                    label = { Text("总杯数") },
                    enabled = recipe.hasPulp,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = recipe.pulpDecInterval.toString(),
                    onValueChange = { input -> validateInRange(input, 1..255, onPulpDecIntervalChange) },
                    label = { Text("递减间隔（杯数）") },
                    enabled = recipe.hasPulp,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = recipe.pulpDecAmount.toString(),
                    onValueChange = { input -> validateInRange(input, 1..255, onPulpDecAmountChange) },
                    label = { Text("递减量（ml）") },
                    enabled = recipe.hasPulp,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            // 已移除：底部果汁流速（已上移至果汁通道附近，避免与果肉分组）
        }
    }
    
    // 新增：保存确认弹窗
    if (showConfirmSave) {
        AlertDialog(
            onDismissRequest = { showConfirmSave = false },
            title = { Text("确认修改配方？") },
            text = { Text("确定要保存当前配方的修改吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmSave = false
                    onSave()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSave = false }) { Text("取消") }
            }
        )
    }

    // 显示裁剪对话框
    if (showCropDialog && selectedImageUri != null) {
        ImageCropDialog(
            imageUri = selectedImageUri,
            onCropComplete = { croppedUri ->
                onImageSelected(croppedUri)
                showCropDialog = false
            },
            onDismiss = {
                showCropDialog = false
            }
        )
    }
    
    // 显示缩放对话框
    if (showScaleDialog && selectedImageUri != null) {
        ImageScaleDialog(
            imageUri = selectedImageUri,
            onScaleComplete = { scaledUri ->
                onImageSelected(scaledUri)
                showScaleDialog = false
            },
            onDismiss = {
                showScaleDialog = false
            }
        )
    }
}

// 添加获取饮品图片的函数（与DrinkMenuScreen保持一致）
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



@Preview(showBackground = true)
@Composable
fun EditRecipeScreenPreview() {
    EditRecipeScreen(
        recipe = Recipe(id = 1, name = "茉莉雪芽", water = 105, juice = 175, price = 8, defaultRemainingWeight = 1000, currentRemainingWeight = 1000, juiceChannel = 1, imageUri = null),
        onNameChange = {},
        onWaterChange = {},
        onJuiceChange = {},
        onPriceChange = {},
        onStockChange = {},
        onCurrentRemainWeightChange = {},
        onJuiceTypeChange = {},
        onJuiceChannelChange = {},
        onHasPulpChange = {},
        onJuiceSpeedChange = {},
        onPulpTotalCupsChange = {},
        onPulpDecIntervalChange = {},
        onPulpDecAmountChange = {},
        onSave = {},
        onNavigateBack = {},
        onImageSelected = {},
        selectedImageUri = null
    )
}