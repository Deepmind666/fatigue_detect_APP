package com.example.juicemachine.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.juicemachine.R
import com.example.juicemachine.data.database.Recipe
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    recipes: List<Recipe>,
    errorMessage: String? = null,
    onAddRecipe: () -> Unit,
    onEditRecipe: (Recipe) -> Unit,
    onDeleteRecipe: (Recipe) -> Unit,
    onClean: () -> Unit,
    onStop: () -> Unit,
    onNavigateBack: () -> Unit,
    onDismissError: () -> Unit = {},
    onRestoreDefaults: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {}
) {
    // 确认弹窗开关
    var showConfirmClean by remember { mutableStateOf(false) }
    var showConfirmStop by remember { mutableStateOf(false) }
    var showConfirmRestore by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("后台管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    AssistChip(
                        onClick = onNavigateToStatistics,
                        label = { Text("统计") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = "统计",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRecipe) {
                Icon(Icons.Filled.Add, contentDescription = "添加新配方")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(recipes) { r ->
                    RecipeRow(
                        recipe = r,
                        onEdit = { onEditRecipe(r) },
                        onDelete = { onDeleteRecipe(r) }
                    )
                }
            }
            // 控制区域
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showConfirmClean = true }, modifier = Modifier.weight(1f)) { Text("一键清洗") }
                Button(onClick = { showConfirmStop = true }, modifier = Modifier.weight(1f)) { Text("清洗停止") }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                Button(
                    onClick = { showConfirmRestore = true },
                    modifier = Modifier.fillMaxWidth(0.7f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("恢复默认配方", color = Color.White) }
            }
        }
    }

    // 确认弹窗：一键清洗
    if (showConfirmClean) {
        AlertDialog(
            onDismissRequest = { showConfirmClean = false },
            title = { Text("确认清洗") },
            text = { Text("确定要执行一键清洗吗？此操作将立刻发送清洗指令。") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmClean = false
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
            text = { Text("确定要停止清洗吗？将发送停止指令。") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmStop = false
                    onStop()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmStop = false }) { Text("取消") }
            }
        )
    }

    // 确认弹窗：恢复默认配方
    if (showConfirmRestore) {
        AlertDialog(
            onDismissRequest = { showConfirmRestore = false },
            title = { Text("恢复默认配方") },
            text = { Text("确定要恢复默认配方吗？此操作将覆盖当前自定义配方，且不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmRestore = false
                    onRestoreDefaults()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRestore = false }) { Text("取消") }
            }
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("提示") },
            text = { Text(errorMessage) },
            confirmButton = { Button(onClick = onDismissError) { Text("确定") } }
        )
    }
}

@Composable
private fun RecipeRow(
    recipe: Recipe,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val defaultPainter = painterResource(id = getDrawableForRecipe(recipe.name))
        if (!recipe.imageUri.isNullOrEmpty()) {
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(recipe.imageUri)
                .crossfade(true)
                .build(),
            contentDescription = recipe.name,
            modifier = Modifier.size(64.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            error = defaultPainter,
            fallback = defaultPainter
        )
        } else {
        Image(
            painter = defaultPainter,
            contentDescription = recipe.name,
            modifier = Modifier.size(64.dp)
        )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = recipe.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "水: ${recipe.water}g, 果汁: ${recipe.juice}g", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEdit) { Text("编辑") }
            Button(onClick = onDelete) { Text("删除") }
        }
    }
}

private fun getDrawableForRecipe(recipeName: String): Int {
    return when (recipeName) {
        "茉莉雪芽" -> R.drawable.mo_li_xue_ya
        "柳橙百香" -> R.drawable.liu_cheng_bai_xiang
        "满杯桑葚" -> R.drawable.man_bei_sang_shen
        else -> R.drawable.placeholder
    }
}