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
    onJuiceChannelChange: (String) -> Unit,
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
                    IconButton(onClick = onSave, enabled = isSavable) {
                        Icon(Icons.Filled.Done, contentDescription = "保存")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onSave) {
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
                            // 显示默认图片或占位符
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.PhotoCamera,
                                    contentDescription = "选择图片",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "点击选择饮品图片",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "支持横图、竖图自动适配",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
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
                    }
                    
                    // 图片信息提示
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
                val filteredInput = input.filter { it.isDigit() }
                if (filteredInput.isBlank()) {
                    onValidChange("") // Or handle as "0"
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
                value = recipe.remainWeight.toString(),
                onValueChange = { validateInRange(it, 0..1000000, onStockChange) },
                label = { Text("剩余重量 (g) [0-1000000]") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = recipe.juiceChannel.toString(),
                onValueChange = { validateInRange(it, 1..3, onJuiceChannelChange) },
                label = { Text("果汁通道 (1-3)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
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
        "茉莉雪芽" -> R.drawable.mo_li_xue_ya
        "柳橙百香" -> R.drawable.liu_cheng_bai_xiang
        "满杯桑葚" -> R.drawable.man_bei_sang_shen 
        else -> R.drawable.placeholder
    }
}



@Preview(showBackground = true)
@Composable
fun EditRecipeScreenPreview() {
    JuiceMachineTheme {
        EditRecipeScreen(
            recipe = Recipe(id = 1, name = "茉莉雪芽", water = 105, juice = 175, price = 8, remainWeight = 1000, juiceChannel = 1, imageUri = null),
            onNameChange = {},
            onWaterChange = {},
            onJuiceChange = {},
            onPriceChange = {},
            onStockChange = {},
            onJuiceChannelChange = {},
            onSave = {},
            onNavigateBack = {},
            onImageSelected = {},
            selectedImageUri = null
        )
    }
}