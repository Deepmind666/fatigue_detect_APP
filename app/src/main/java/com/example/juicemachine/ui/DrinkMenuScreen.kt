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
import com.example.juicemachine.ui.viewmodel.DrinkMenuUiState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning

private fun getDrawableForRecipe(recipeName: String): Int {
    return when (recipeName) {
        "茉莉雪芽" -> R.drawable.bin_fen_bai_guo
        "柳橙百香" -> R.drawable.niu_you_guo
        "满杯桑葚" -> R.drawable.tao_ni_huan_xin
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
    onLoginAttempt: (String) -> Unit
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Header(
    onLongClick: () -> Unit,
    temperature: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            )
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "智能茶饮系统",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.weight(1f))

        // 只显示温度，不显示“设备未连接”字样
        val displayTemp = if (temperature == "未连接" || temperature.isBlank()) "--°" else temperature
        Text("温度: $displayTemp", fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun DrinkGrid(recipes: List<Recipe>, onRecipeSelected: (Recipe) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(recipes) { recipe ->
            DrinkCard(recipe, onRecipeSelected)
        }
    }
}

@Composable
fun DrinkCard(recipe: Recipe, onRecipeSelected: (Recipe) -> Unit) {
    val painter = painterResource(id = getDrawableForRecipe(recipe.name))

    val isSoldOut = recipe.stock <= 0
    // Show low stock warning if it can make 3 or fewer drinks, but is not yet sold out.
    val isLowStock = !isSoldOut && recipe.stock <= 3

    Card(
        modifier = Modifier.clickable(
            enabled = !isSoldOut,
            onClick = { onRecipeSelected(recipe) }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painter,
                    contentDescription = recipe.name,
                    modifier = Modifier
                        .height(130.dp)
                        .fillMaxWidth()
                        .alpha(if (isSoldOut) 0.5f else 1.0f),
                    contentScale = ContentScale.Crop
                )

                if (isSoldOut) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "已售罄",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (isLowStock) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "库存不足警告",
                            tint = Color.Yellow,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    minLines = 2,
                    maxLines = 2
                )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "定制您的饮品: ${recipe.name}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("选择冰量:")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    val normalIceColor = if (withIce) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    val noIceColor = if (!withIce) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()

                    Button(onClick = { withIce = true }, colors = normalIceColor) {
                        Text("正常冰")
                    }
                    Button(onClick = { withIce = false }, colors = noIceColor) {
                        Text("去冰")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm("中杯", withIce) }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("取消")
            }
        }
    )
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

@Preview(showBackground = true)
@Composable
fun DrinkMenuScreenPreview() {
    val previewRecipes = listOf(
        Recipe(id = 1, name = "茉莉雪芽", water = 30, juice = 45, price = 8, stock = 10, juiceChannel = 1),
        Recipe(id = 2, name = "柳橙百香", water = 20, juice = 60, price = 9, stock = 3, juiceChannel = 2),
        Recipe(id = 3, name = "满杯桑葚", water = 15, juice = 65, price = 10, stock = 0, juiceChannel = 3)
    )
    JuiceMachineTheme {
        DrinkMenuScreen(
            uiState = DrinkMenuUiState(recipes = previewRecipes, temperature = "25℃"),
            onRecipeClick = {},
            onHeaderLongClick = {},
            onDismissDialog = {},
            onConfirmDialog = { _, _, _ -> },
            onLoginAttempt = {}
        )
    }
} 