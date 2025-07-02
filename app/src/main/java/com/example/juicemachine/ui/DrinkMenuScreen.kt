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
import coil.request.ImageRequest
import com.example.juicemachine.R
import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.ui.theme.JuiceMachineTheme
import com.example.juicemachine.ui.viewmodel.DrinkMenuUiState
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun DrinkMenuScreen(
    uiState: DrinkMenuUiState,
    onRecipeSelected: (Recipe) -> Unit,
    onHeaderLongClick: () -> Unit,
    onDismissDialog: () -> Unit,
    onConfirmDialog: (Recipe, String) -> Unit,
    onMakeJuice: () -> Unit,
    onClean: () -> Unit,
    onAddWater: () -> Unit,
    onTestTemp: () -> Unit,
    onConnectClick: () -> Unit,
    onLoginAttempt: (String) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header(
                onLongClick = onHeaderLongClick,
                isConnected = uiState.isMachineConnected,
                onConnectClick = onConnectClick
            )
            DrinkGrid(
                recipes = uiState.recipes,
                onRecipeSelected = onRecipeSelected
            )
        }

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
    }

    if (uiState.selectedRecipe != null) {
        JuiceCustomizationDialog(
            recipe = uiState.selectedRecipe,
            onDismiss = onDismissDialog,
            onConfirm = { cupSize ->
                onConfirmDialog(uiState.selectedRecipe, cupSize)
            }
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
    isConnected: Boolean,
    onConnectClick: () -> Unit
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isConnected) "已连接" else "未连接",
                fontSize = 20.sp,
                color = if (isConnected) Color(0xFF008000) else Color.Red,
                fontWeight = FontWeight.Bold
            )
            if (!isConnected) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConnectClick) {
                    Text("连接")
                }
            }
        }
        Spacer(Modifier.width(16.dp))

        Text("温度: --°C", fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
    Card(
        modifier = Modifier.clickable { onRecipeSelected(recipe) },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val imageModifier = Modifier
                .height(130.dp)
                .fillMaxWidth()

            when {
                recipe.imageUri != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(recipe.imageUri)
                            .crossfade(true)
                            .build(),
                        placeholder = painterResource(R.drawable.placeholder),
                        error = painterResource(R.drawable.placeholder),
                        contentDescription = recipe.name,
                        modifier = imageModifier,
                        contentScale = ContentScale.Crop
                    )
                }
                recipe.imageResId != null -> {
                    Image(
                        painter = painterResource(id = recipe.imageResId),
                        contentDescription = recipe.name,
                        modifier = imageModifier,
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    Image(
                        painter = painterResource(id = R.drawable.placeholder),
                        contentDescription = "Placeholder",
                        modifier = imageModifier,
                        contentScale = ContentScale.Crop
                    )
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
    onDismiss: () -> Unit,
    onConfirm: (cupSize: String) -> Unit
) {
    var selectedCupSize by remember { mutableStateOf("中杯") }
    val config = when (selectedCupSize) {
        "大杯" -> recipe.large
        "中杯" -> recipe.medium
        else -> recipe.small
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(450.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(recipe.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CupSizeButton("大杯", selectedCupSize == "大杯", modifier = Modifier.weight(1f)) { selectedCupSize = "大杯" }
                    CupSizeButton("中杯", selectedCupSize == "中杯", modifier = Modifier.weight(1f)) { selectedCupSize = "中杯" }
                    CupSizeButton("小杯", selectedCupSize == "小杯", modifier = Modifier.weight(1f)) { selectedCupSize = "小杯" }
                }
                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                        Text("配比详情:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            Text("冰: ${config.ice}g", fontSize = 18.sp)
                            Text("果汁: ${config.juice}g", fontSize = 18.sp)
                            Text("水: ${config.water}g", fontSize = 18.sp)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)) { Text("取消", fontSize = 16.sp) }
                    Button(onClick = { onConfirm(selectedCupSize) }, modifier = Modifier.weight(1f).height(50.dp)) { Text("确定", fontSize = 16.sp) }
                }
            }
        }
    }
}

@Composable
fun CupSizeButton(text: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text, fontSize = 18.sp, maxLines = 1)
    }
}

@Composable
fun ActionButton(text: String, onClick: () -> Unit, isPrimary: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(150.dp).height(60.dp),
        colors = if (isPrimary) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.buttonColors(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 18.sp, color = if (isPrimary) Color.White else Color.Unspecified, maxLines = 1)
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
fun DrinkMenuScreenPreview() {
    JuiceMachineTheme {
        val dummyRecipes = listOf(
            Recipe(id = 1, name = "桃你欢心", imageResId = R.drawable.tao_ni_huan_xin),
            Recipe(id = 2, name = "牛油果生椰拿铁", imageResId = R.drawable.niu_you_guo),
            Recipe(id = 3, name = "鸭屎香柠檬茶", imageResId = R.drawable.ya_shi_xiang)
        )
        DrinkMenuScreen(
            uiState = DrinkMenuUiState(recipes = dummyRecipes),
            onRecipeSelected = {},
            onHeaderLongClick = {},
            onDismissDialog = {},
            onConfirmDialog = { _, _ -> },
            onMakeJuice = {},
            onClean = {},
            onAddWater = {},
            onTestTemp = {},
            onConnectClick = {},
            onLoginAttempt = {}
        )
    }
} 