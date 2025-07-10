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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    recipes: List<Recipe>,
    onAddRecipe: () -> Unit,
    onEditRecipe: (Recipe) -> Unit,
    onDeleteRecipe: (Recipe) -> Unit,
    onClean: () -> Unit,
    onAddWater: () -> Unit,
    onTestTemp: () -> Unit,
    onConnect: () -> Unit,
    onNavigateBack: () -> Unit
) {
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(recipes) { recipe ->
                    RecipeAdminCard(
                        recipe = recipe,
                        onEditClick = { onEditRecipe(recipe) },
                        onDeleteClick = { onDeleteRecipe(recipe) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control buttons - Improved layout
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
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
                            text = "停止加水",
                            onClick = onAddWater,
                            modifier = Modifier.weight(1f),
                            isPrimary = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Second row of buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionButton(
                            text = "连接测试",
                            onClick = onTestTemp,
                            modifier = Modifier.weight(1f),
                            isPrimary = true
                        )
                        ActionButton(
                            text = "开始制作",
                            onClick = onConnect,
                            modifier = Modifier.weight(1f),
                            isPrimary = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeAdminCard(
    recipe: Recipe,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = recipe.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "果汁通道: ${recipe.juiceChannel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "剩余重量: ${recipe.remainWeight}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
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
            onAddWater = {},
            onTestTemp = {},
            onConnect = {},
            onNavigateBack = {}
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .width(150.dp)
            .height(60.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
} 