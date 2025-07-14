package com.example.juicemachine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.ui.theme.JuiceMachineTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.juicemachine.R
import androidx.compose.material3.ButtonColors

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
    onTare: () -> Unit,
    onWeigh: () -> Unit,
    onNavigateBack: () -> Unit,
    onDismissError: () -> Unit = {},
    onRestoreDefaults: () -> Unit = {}
) {
    val coreRecipes = listOf("茉莉雪芽", "柳橙百香", "满杯桑葚")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("后台管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRecipe) {
                Icon(Icons.Filled.Add, contentDescription = "添加新配方")
            }
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 配方列表
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 8.dp)
                ) {
                    items(recipes) { recipe ->
                        RecipeRow(
                            recipe = recipe,
                            onEdit = { onEditRecipe(recipe) },
                            onDelete = { onDeleteRecipe(recipe) },
                            isCoreRecipe = recipe.name in coreRecipes
                        )
                    }
                }

                // 控制按钮区域
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "设备控制",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // First row of buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ActionButton(
                                text = "一键清洗",
                                onClick = onClean,
                                modifier = Modifier.weight(1f),
                                isPrimary = true
                            )
                            ActionButton(
                                text = "紧急停止",
                                onClick = onStop,
                                modifier = Modifier.weight(1f),
                                isPrimary = true // 修改为 true，与“一键清洗”保持一致
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Second row of buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ActionButton(
                                text = "去皮",
                                onClick = onTare,
                                modifier = Modifier.weight(1f),
                                isPrimary = true
                            )
                            ActionButton(
                                text = "称重",
                                onClick = onWeigh,
                                modifier = Modifier.weight(1f),
                                isPrimary = true
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Third row - Restore button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            ActionButton(
                                text = "恢复默认配方",
                                onClick = onRestoreDefaults,
                                modifier = Modifier.fillMaxWidth(0.6f),
                                isPrimary = false, // 保持为次要，但提供覆盖颜色
                                // 精确覆盖为旧的绿色（主题中的次要颜色）
                                overrideColors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }
                }
            }
        }
    )
    
    // 添加错误信息显示
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { onDismissError() },
            title = { Text("调试信息") },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = { onDismissError() }) { Text("确定") }
            }
        )
    }
}

@Composable
fun RecipeRow(
    recipe: Recipe,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isCoreRecipe: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            // 饮品图片
            Card(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Image(
                    painter = painterResource(id = getDrawableForRecipe(recipe.name)),
                    contentDescription = recipe.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 信息区域
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "水: ${recipe.water}g  •  汁: ${recipe.juice}g  •  ¥${recipe.price}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "通道: ${recipe.juiceChannel}  •  剩余: ${recipe.remainWeight}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // 操作按钮
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑配方",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onDelete,
                    enabled = !isCoreRecipe,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除配方",
                        tint = if (isCoreRecipe) Color.Gray else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// 添加获取饮品图片的函数
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
fun AdminScreenPreview() {
    val previewRecipes = listOf(
        Recipe(id = 1, name = "茉莉雪芽", water = 105, juice = 175, price = 8, remainWeight = 1000, juiceChannel = 1),
        Recipe(id = 2, name = "柳橙百香", water = 180, juice = 100, price = 9, remainWeight = 1000, juiceChannel = 2),
        Recipe(id = 3, name = "满杯桑葚", water = 130, juice = 150, price = 10, remainWeight = 1000, juiceChannel = 3)
    )
    JuiceMachineTheme {
        AdminScreen(
            recipes = previewRecipes,
            onAddRecipe = {},
            onEditRecipe = {},
            onDeleteRecipe = {},
            onClean = {},
            onStop = {},
            onTare = {},
            onWeigh = {},
            onNavigateBack = {},
            onDismissError = {},
            onRestoreDefaults = {}
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    overrideColors: ButtonColors? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        colors = overrideColors ?: if (isPrimary) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        }
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
} 