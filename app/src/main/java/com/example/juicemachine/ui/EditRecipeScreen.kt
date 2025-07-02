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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.juicemachine.R
import com.example.juicemachine.data.database.CupConfig
import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.ui.theme.JuiceMachineTheme
import com.example.juicemachine.ui.viewmodel.DrinkMenuUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipeScreen(
    uiState: DrinkMenuUiState,
    onNavigateBack: () -> Unit,
    onSave: (Recipe) -> Unit,
    onNameChange: (String) -> Unit,
    onImageUriChange: (String?) -> Unit,
    onCupConfigChange: (size: String, field: String, value: String) -> Unit,
    recipeId: Long,
    loadRecipeForEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                // Persist access permissions
                val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flag)
                onImageUriChange(uri.toString())
            }
        }
    )

    LaunchedEffect(recipeId) {
        loadRecipeForEdit(recipeId)
    }

    val recipe = uiState.recipeToEdit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recipe.id == 0L) "添加新配方" else "编辑配方") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onSave(recipe) }) {
                Icon(Icons.Filled.Done, contentDescription = "保存")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uiState.recipeToEdit.imageUri ?: uiState.recipeToEdit.imageResId ?: R.drawable.placeholder)
                    .crossfade(true)
                    .build(),
                contentDescription = "饮品图片",
                modifier = Modifier
                    .size(150.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.placeholder),
                error = painterResource(R.drawable.placeholder)
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = recipe.name,
                onValueChange = onNameChange,
                label = { Text("饮品名称") },
                modifier = Modifier.fillMaxWidth()
            )

            CupConfigEditor(
                title = "小杯",
                config = recipe.small,
                onCupConfigChange = { field, value -> onCupConfigChange("小杯", field, value) }
            )
            CupConfigEditor(
                title = "中杯",
                config = recipe.medium,
                onCupConfigChange = { field, value -> onCupConfigChange("中杯", field, value) }
            )
            CupConfigEditor(
                title = "大杯",
                config = recipe.large,
                onCupConfigChange = { field, value -> onCupConfigChange("大杯", field, value) }
            )
        }
    }
}

@Composable
fun CupConfigEditor(
    title: String,
    config: CupConfig,
    onCupConfigChange: (field: String, value: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = config.ice.toString(),
                onValueChange = { onCupConfigChange("冰", it) },
                label = { Text("冰 (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = config.juice.toString(),
                onValueChange = { onCupConfigChange("果汁", it) },
                label = { Text("果汁 (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = config.water.toString(),
                onValueChange = { onCupConfigChange("水", it) },
                label = { Text("水 (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EditRecipeScreenPreview() {
    JuiceMachineTheme {
        EditRecipeScreen(
            uiState = DrinkMenuUiState(recipeToEdit = Recipe(id = 0, name = "测试饮品", imageResId = 0)),
            onNavigateBack = {},
            onSave = {},
            onNameChange = {},
            onImageUriChange = {},
            onCupConfigChange = { _, _, _ -> },
            recipeId = -1,
            loadRecipeForEdit = {}
        )
    }
} 