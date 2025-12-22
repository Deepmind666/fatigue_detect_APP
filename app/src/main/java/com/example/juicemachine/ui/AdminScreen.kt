@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.juicemachine.ui
import androidx.compose.material.icons.automirrored.filled.Sort

import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.juicemachine.R
import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.ui.theme.FreshOrange
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.*
import kotlin.math.roundToInt
import androidx.compose.material3.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AdminScreen(
    recipes: List<Recipe>,
    errorMessage: String?,
    onAddRecipe: () -> Unit,
    onEditRecipe: (Recipe) -> Unit,
    onDeleteRecipe: (Recipe) -> Unit,
    onClean: () -> Unit,
    onStop: () -> Unit,
    onTare: () -> Unit,
    onWeigh: () -> Unit,
    onCalibrateStandard: () -> Unit,
    onNavigateBack: () -> Unit,
    onDismissError: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    // 新增：温度与重量展示
    currentTemperature: Int?,
    currentWeight: Int?,
    // 新增：只出水水速与输入回调
    waterOnlySpeed: Int,
    onWaterOnlySpeedChange: (String) -> Unit,
    onSaveWaterOnlySpeed: () -> Unit
) {
    // 确认弹窗开关
    var showConfirmClean by remember { mutableStateOf(false) }
    var showConfirmStop by remember { mutableStateOf(false) }
    var showConfirmRestore by remember { mutableStateOf(false) }
    // 新增：广告图片管理弹窗
    var showAdsManager by remember { mutableStateOf(false) }
    // 新增：保存成功弹窗
    var showSaveSuccess by remember { mutableStateOf(false) }

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
                    // 在统计图标左侧加入温度/重量信息芯片（与后台风格一致）
                    AssistChip(
                        onClick = {},
                        label = { Text("温度 ${currentTemperature?.let { "$it°C" } ?: "—°C"}") },
                        leadingIcon = { Icon(Icons.Outlined.DeviceThermostat, contentDescription = "温度") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text("重量 ${currentWeight?.let { "$it g" } ?: "— g"}") },
                        leadingIcon = { Icon(Icons.Outlined.MonitorWeight, contentDescription = "重量") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = onNavigateToStatistics,
                        label = { Text("统计") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Analytics,
                                contentDescription = "统计",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = { showAdsManager = true },
                        label = { Text("广告图片") },
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

            // 新增：称重控制区
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onTare, modifier = Modifier.weight(1f)) { Text("去皮") }
                Button(onClick = onWeigh, modifier = Modifier.weight(1f)) { Text("称重") }
                Button(onClick = onCalibrateStandard, modifier = Modifier.weight(1f)) { Text("标准校准(1000g)") }
            }

            // 新增：只出水水速调控（0-255），作为指令中的水通道速度
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("水速 (0-255)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.widthIn(min = 120.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = waterOnlySpeed.coerceIn(0, 255).toString(),
                    onValueChange = onWaterOnlySpeedChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    label = { Text("输入整数0-255") }
                )
                val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                Button(
                    onClick = {
                        onSaveWaterOnlySpeed()
                        keyboardController?.hide()
                        showSaveSuccess = true
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("保存")
                }
            }

            // 已移除：系统偏好面板（与本APP需求不符）

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                Button(
                    onClick = { showConfirmRestore = true },
                    modifier = Modifier.fillMaxWidth(0.7f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("恢复默认配方", color = Color.White) }
            }

            // 已移除：含果肉补偿参数设置区（迁移到每个配方的编辑页）
        }
    }

    // 新增：广告图片管理对话框（全屏）
    if (showAdsManager) {
        AdsManagerDialog(
            recipes = recipes,
            onDismiss = { showAdsManager = false }
        )
    }

    // 确认弹窗：一键清洗
    if (showConfirmClean) {
        AlertDialog(
            onDismissRequest = { showConfirmClean = false },
            title = { Text("确认清洗") },
            text = { Text("确定要执行一键清洗吗？") },
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
            text = { Text("确定要停止清洗吗？") },
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
            title = { Text("确认恢复") },
            text = { Text("确定要恢复默认配方吗？这将覆盖当前所有配方。") },
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

    // 新增：保存成功弹窗
    if (showSaveSuccess) {
        AlertDialog(
            onDismissRequest = { showSaveSuccess = false },
            title = { Text("保存成功") },
            text = { Text("水速已更新为 $waterOnlySpeed") },
            confirmButton = {
                TextButton(onClick = { showSaveSuccess = false }) { Text("确定") }
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

// 新增：广告图片管理对话框实现
@Composable
private fun AdsManagerDialog(
    recipes: List<Recipe>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("ads_prefs", android.content.Context.MODE_PRIVATE)

    val initial: List<AdItem> = remember {
        val json = prefs.getString("ads_image_uris", null)
        try {
            if (json.isNullOrBlank()) emptyList() else org.json.JSONArray(json).let { arr ->
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        when (val e = arr.get(i)) {
                            is org.json.JSONObject -> add(
                                AdItem(
                                    uri = e.optString("uri"),
                                    title = e.optString("title").takeIf { it.isNotBlank() }
                                )
                            )
                            is String -> add(AdItem(uri = e))
                            else -> {}
                        }
                    }
                }
            }
        } catch (_: Exception) { emptyList() }
    }
    // 显示广告页图片：优先持久化，其次使用内置广告资源，不再默认使用饮品图片
    val builtInAds = remember(context) {
        val pkg = context.packageName
        listOf(
            AdItem(uri = "android.resource://$pkg/drawable/ba_qi_qing_ning_ad", title = "霸气青柠"),
            AdItem(uri = "android.resource://$pkg/drawable/ba_qi_yang_mei_ad", title = "霸气杨梅"),
            AdItem(uri = "android.resource://$pkg/drawable/ya_shi_xiang_ad", title = "鸭屎香柠檬茶")
        )
    }
    // 可选：从配方名称推导广告图（使用广告资源映射，而不是配方自定义图片）
    val fromRecipes = remember(recipes, context) {
        recipes
            .map {
                val resId = getDrawableForRecipe(context, it.name)
                val resName = try { context.resources.getResourceEntryName(resId) } catch (_: Exception) { null }
                val uri = if (resName != null) "android.resource://${context.packageName}/drawable/$resName" else "android.resource://${context.packageName}/drawable/placeholder"
                AdItem(uri = uri, title = it.name)
            }
            .distinctBy { it.uri }
            .take(5)
    }
    val initialAds = if (initial.isEmpty()) builtInAds else initial

    val images = remember { mutableStateListOf<AdItem>().apply { addAll(initialAds) } }
    // 用哈希字符串做脏检查（顺序敏感，包含标题）
    var lastSavedHash by remember { mutableStateOf(initialAds.joinToString("|") { it.uri + "#" + (it.title ?: "") }) }
    // 使用 derivedStateOf 使其在 images 内容变化时自动刷新（需 remember 包裹）
    val isDirty by remember(images, lastSavedHash) { derivedStateOf { images.joinToString("|") { it.uri + "#" + (it.title ?: "") } != lastSavedHash } }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 选择模式状态与工具
    var selectionMode by remember { mutableStateOf(false) }
    // 新增：排序模式（通过“顺序管理”进入），与选择模式互斥
    var reorderMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val exitSelection: () -> Unit = remember { { selected.clear(); selectionMode = false } }
    LaunchedEffect(images) {
        selected.removeAll { uri -> images.none { it.uri == uri } }
        if (selected.isEmpty()) selectionMode = false
    }

    // 统一保存逻辑：顶部按钮和底部悬浮按钮共用
    val save: () -> Unit = remember(images) {
        {
            val arr = org.json.JSONArray()
            images.forEach { ad ->
                val obj = org.json.JSONObject().apply {
                    put("uri", ad.uri)
                    if (!ad.title.isNullOrBlank()) put("title", ad.title)
                }
                arr.put(obj)
            }
            prefs.edit().putString("ads_image_uris", arr.toString()).apply()
            lastSavedHash = images.joinToString("|") { it.uri + "#" + (it.title ?: "") }
            scope.launch { snackbarHostState.showSnackbar("保存成功") }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        var added = 0
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* ignore */ }
            val s = uri.toString()
            if (images.none { it.uri == s }) { images.add(AdItem(uri = s)); added++ }
        }
        if (added > 0) scope.launch { snackbarHostState.showSnackbar("已添加 $added 张图片") }
    }

    // Lightbox 预览
    var previewUri by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = { if (isDirty) save() },
                        expanded = isDirty,
                        icon = { Icon(Icons.Filled.Save, contentDescription = null) },
                        text = { Text("保存") },
                        modifier = Modifier.alpha(if (isDirty) 1f else 0.6f)
                    )
                }
            ) { innerPadding ->
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    // 顶部栏 + 动作（与其它页统一：半透明渐变 + 内容区域使用 Surface 背景）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                                        FreshOrange.copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("广告图片管理", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                // 操作采用 AssistChip，统一风格
                                AssistChip(
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                     onClick = { picker.launch(arrayOf("image/*")) },
                                     label = { Text("添加") },
                                     leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                     colors = AssistChipDefaults.assistChipColors(
                                         containerColor = Color.White.copy(alpha = 0.12f),
                                         labelColor = Color.White
                                     )
                                 )
                                AssistChip(
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                     onClick = { images.clear(); images.addAll(builtInAds) },
                                     enabled = builtInAds.isNotEmpty(),
                                     label = { Text("默认") },
                                     leadingIcon = { Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                     colors = AssistChipDefaults.assistChipColors(
                                         containerColor = Color.White.copy(alpha = 0.12f),
                                         labelColor = Color.White
                                     )
                                 )
                                AssistChip(
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                     onClick = { reorderMode = true; selectionMode = false },
                                     enabled = images.isNotEmpty(),
                                     label = { Text("顺序管理") },
                                     leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                     colors = AssistChipDefaults.assistChipColors(
                                         containerColor = Color.White.copy(alpha = 0.12f),
                                         labelColor = Color.White
                                     )
                                 )
                                AssistChip(
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                     onClick = {
                                         var added = 0
                                         fromRecipes.forEach { u -> if (images.none { it.uri == u.uri }) { images.add(u); added++ } }
                                         scope.launch { snackbarHostState.showSnackbar("已追加 $added 张") }
                                     },
                                     enabled = fromRecipes.isNotEmpty(),
                                     label = { Text("从配方") },
                                     leadingIcon = { Icon(Icons.Filled.Collections, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                     colors = AssistChipDefaults.assistChipColors(
                                         containerColor = Color.White.copy(alpha = 0.12f),
                                         labelColor = Color.White
                                     )
                                 )
                                // 进入选择模式
                                AssistChip(
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                     onClick = { selectionMode = true },
                                     enabled = images.isNotEmpty(),
                                     label = { Text("选择") },
                                     leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                     colors = AssistChipDefaults.assistChipColors(
                                         containerColor = Color.White.copy(alpha = 0.12f),
                                         labelColor = Color.White
                                     )
                                 )
                                var showConfirmClear by remember { mutableStateOf(false) }
                                if (showConfirmClear) {
                                    AlertDialog(
                                        onDismissRequest = { showConfirmClear = false },
                                        title = { Text("清空列表") },
                                        text = { Text("确定要清空当前广告图片列表吗？") },
                                        confirmButton = { TextButton(onClick = { showConfirmClear = false; images.clear() }) { Text("确定") } },
                                        dismissButton = { TextButton(onClick = { showConfirmClear = false }) { Text("取消") } }
                                    )
                                }
                                AssistChip(
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                     onClick = { showConfirmClear = true },
                                     label = { Text("清空") },
                                     leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                     colors = AssistChipDefaults.assistChipColors(
                                         containerColor = Color.White.copy(alpha = 0.12f),
                                         labelColor = Color.White
                                     )
                                 )
                                AssistChip(
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                     onClick = {
                                         scope.launch {
                                             val removed = withContext(Dispatchers.IO) {
                                                 val cr = context.contentResolver; val toRemove = mutableListOf<String>()
                                                 images.forEach { ad ->
                                                     val s = ad.uri
                                                     val u = Uri.parse(s)
                                                     val ok = when (u.scheme) {
                                                         null, "", "file" -> File(u.path ?: s).exists()
                                                         "content" -> runCatching { cr.openInputStream(u)?.close(); true }.getOrElse { false }
                                                         else -> runCatching { cr.openInputStream(u)?.close(); true }.getOrElse { false }
                                                     }
                                                     if (!ok) toRemove.add(s)
                                                 }
                                                 images.removeAll { it.uri in toRemove }
                                                 toRemove.size
                                             }
                                             snackbarHostState.showSnackbar(if (removed > 0) "已移除 $removed 个失效" else "未发现失效")
                                         }
                                     },
                                     label = { Text("清理") },
                                     leadingIcon = { Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                     colors = AssistChipDefaults.assistChipColors(
                                         containerColor = Color.White.copy(alpha = 0.12f),
                                         labelColor = Color.White
                                     )
                                 )
                                AssistChip(
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                     onClick = save,
                                     enabled = isDirty,
                                     label = { Text("保存") },
                                     leadingIcon = { Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                     colors = AssistChipDefaults.assistChipColors(
                                         containerColor = Color.White.copy(alpha = if (isDirty) 0.18f else 0.06f),
                                         labelColor = Color.White.copy(alpha = if (isDirty) 1f else 0.6f)
                                     )
                                 )
                                AssistChip(
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                     onClick = onDismiss,
                                     label = { Text("关闭") },
                                     leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                     colors = AssistChipDefaults.assistChipColors(
                                         containerColor = Color.White.copy(alpha = 0.12f),
                                         labelColor = Color.White
                                     )
                                 )
                            }
                        }
                    }

                    // 选择模式操作条（当选择模式开启时显示）
                    if (selectionMode) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("已选择 ${selected.size} 张", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    AssistChip(modifier = Modifier.minimumInteractiveComponentSize(), onClick = { selected.clear(); selected.addAll(images.map { it.uri }) }, label = { Text("全选") })
                                    AssistChip(modifier = Modifier.minimumInteractiveComponentSize(), onClick = {
                                        val count = selected.size
                                        if (count > 0) {
                                            val toRemove = selected.toSet()
                                            images.removeAll { it.uri in toRemove }
                                            exitSelection()
                                            scope.launch { snackbarHostState.showSnackbar("已删除 $count 张") }
                                        }
                                    }, label = { Text("删除") })
                                }
                                AssistChip(modifier = Modifier.minimumInteractiveComponentSize(), onClick = exitSelection, label = { Text("取消") })
                            }
                        }
                    }
                    // 排序模式操作条（当排序模式开启时显示）
                    if (reorderMode) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("排序模式", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AssistChip(
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                     onClick = { reorderMode = false },
                                     label = { Text("完成") },
                                     leadingIcon = { Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                    }

                    // 内容区域 - 预览网格更精致
                    // 内容区域 - 默认三等分网格展示；排序模式下切换为列表编辑
                     Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        if (images.isNotEmpty()) {
                             Text("预览", style = MaterialTheme.typography.titleMedium)
                             Spacer(Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                 LazyVerticalGrid(
                                     columns = GridCells.Fixed(3),
                                     modifier = Modifier.fillMaxSize(),
                                     contentPadding = PaddingValues(0.dp)
                                 ) {
                                     gridItemsIndexed(images, key = { _, it -> it.uri }) { idx, ad ->
                                         val uri = ad.uri
                                         Card(
                                             modifier = Modifier
                                                 .padding(8.dp)
                                                 .fillMaxWidth()
                                                 .aspectRatio(3f/4f)
                                                 .combinedClickable(
                                                     onClick = {
                                                         if (selectionMode) {
                                                             if (selected.contains(uri)) selected.remove(uri) else selected.add(uri)
                                                         } else {
                                                             previewUri = uri
                                                         }
                                                     },
                                                     onLongClick = {
                                                        if (!selectionMode) selectionMode = true
                                                        if (!selected.contains(uri)) selected.add(uri)
                                                     }
                                                 ),
                                             shape = RoundedCornerShape(16.dp),
                                             elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                         ) {
                                             Box(Modifier.fillMaxSize()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize(),
                                                    error = painterResource(id = R.drawable.placeholder),
                                                    fallback = painterResource(id = R.drawable.placeholder)
                                                )
                                                 // 左上角序号圆角徽标
                                                 Box(
                                                     modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(color = Color(0x99000000), shape = RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                                                 ) { Text(text = "${idx + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                                                 // 底部渐变信息条，显示标题或文件名
                                                 val fileName = remember(uri) {
                                                     val u = Uri.parse(uri)
                                                     when (u.scheme) {
                                                         "android.resource" -> u.lastPathSegment ?: uri
                                                         else -> (u.path?.substringAfterLast('/') ?: uri)
                                                     }
                                                 }
                                                 val titleToShow = ad.title?.takeIf { it.isNotBlank() } ?: fileName
                                                 Box(
                                                     modifier = Modifier
                                                         .align(Alignment.BottomStart)
                                                         .fillMaxWidth()
                                                         .background(
                                                             Brush.verticalGradient(
                                                                 colors = listOf(Color.Transparent, Color(0xAA000000))
                                                             )
                                                         )
                                                         .padding(horizontal = 10.dp, vertical = 8.dp)
                                                 ) {
                                                     Text(titleToShow, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                 }
                                                // 选择态勾选标识
                                                if (selectionMode) {
                                                    val checked = selected.contains(uri)
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        tint = if (checked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                                    )
                                                }
                                             }
                                         }
                                     }
                                 }
                             }
                             Spacer(Modifier.height(16.dp))
                         }

                         if (images.isEmpty()) {
                             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                 Text("暂无图片，请点击‘添加’或‘默认’。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                             }
                        } else if (reorderMode) {
                             // 拖拽排序所需状态
                             val listState = rememberLazyListState()
                             val rowHeight = 80.dp
                             // val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() } // removed unused
                             var dragOffset by remember { mutableStateOf(0f) }
                             // var dragStartIndex by remember { mutableStateOf(0) } // removed unused
                             var draggingIndex by remember { mutableStateOf<Int?>(null) }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(bottom = 72.dp),
                                 state = listState
                             ) {
                                 itemsIndexed(images, key = { _, it -> it.uri }) { index, ad ->
                                     val isDragging = draggingIndex == index
                                     Row(
                                         modifier = Modifier.fillMaxWidth().height(rowHeight).then(
                                             if (isDragging) Modifier.offset { IntOffset(0, dragOffset.roundToInt()) }.zIndex(1f) else Modifier
                                         ).padding(vertical = 6.dp),
                                         verticalAlignment = Alignment.CenterVertically,
                                         horizontalArrangement = Arrangement.spacedBy(12.dp)
                                     ) {
                                         Card(shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                                             AsyncImage(model = ImageRequest.Builder(context).data(ad.uri).build(), contentDescription = null, modifier = Modifier.size(72.dp), contentScale = ContentScale.Fit)
                                         }
                                         val fileName = remember(ad.uri) {
                                             val u = Uri.parse(ad.uri)
                                             when (u.scheme) {
                                                 "android.resource" -> u.lastPathSegment ?: ad.uri
                                                 else -> (u.path?.substringAfterLast('/') ?: ad.uri)
                                             }
                                         }
                                         OutlinedTextField(
                                             value = ad.title.orEmpty(),
                                             onValueChange = { new -> images[index] = images[index].copy(title = new) },
                                             modifier = Modifier.weight(1f),
                                             singleLine = true,
                                             placeholder = { Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                             label = { Text("名称（可选）") }
                                         )
                                         IconButton(onClick = { if (index > 0) { val x = images.removeAt(index); images.add(0, x) } }) { Icon(Icons.Filled.VerticalAlignTop, contentDescription = "置顶") }
                                         IconButton(onClick = { if (index > 0) { val x = images.removeAt(index); images.add(index - 1, x) } }) { Icon(Icons.Filled.ArrowUpward, contentDescription = "上移") }
                                         IconButton(onClick = { if (index < images.size - 1) { val x = images.removeAt(index); images.add(index + 1, x) } }) { Icon(Icons.Filled.ArrowDownward, contentDescription = "下移") }
                                         IconButton(onClick = { images.removeAt(index) }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
                                     }
                                 }
                             }
                        }
                     } // end else
                 } // end content Column
             } // end wrapper Column
         } // end Scaffold
     } // end Surface
     // keep function open for preview dialog

    // 大图预览对话框（单独对话框，置于函数内部）
    if (previewUri != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { previewUri = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(previewUri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    error = painterResource(id = R.drawable.placeholder),
                    fallback = painterResource(id = R.drawable.placeholder)
                )
                IconButton(onClick = { previewUri = null }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                }
            }
        }
    }
    } // end Ads Dialog
 
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
        val defaultPainter = painterResource(id = getBeverageDrawableForRecipe(LocalContext.current, recipe.name))
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
            Text(text = if (recipe.name == "山野栀子") "鸭屎香柠檬茶" else recipe.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "水: ${recipe.water}g, 果汁: ${recipe.juice}g", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEdit) { Text("编辑") }
            Button(onClick = onDelete) { Text("删除") }
        }
    }
}

private fun getDrawableForRecipe(context: android.content.Context, recipeName: String): Int {
    // 广告资源映射（横图）
    val key = when (recipeName) {
        // 版本B
        "霸气青柠" -> "ba_qi_qing_ning_ad"
        "霸气杨梅" -> "ba_qi_yang_mei_ad"
        "山野栀子" -> "shan_ye_zhi_zi_ad"
        "鸭屎香" -> "ya_shi_xiang_ad"
        // 版本A（兼容）
        "柳橙百香" -> "liu_cheng_bai_xiang_ad"
        "茉莉雪芽" -> "mo_li_xue_ya_ad"
        "鸭屎香柠檬茶" -> "ya_shi_xiang_ad"
        else -> null
    }
    if (key != null) {
        val id = context.resources.getIdentifier(key, "drawable", context.packageName)
        if (id != 0) return id
        // 双向映射：B→A 与 A→B
        val alias = when (recipeName) {
            // B 名缺图回退到 A
            "霸气青柠" -> "liu_cheng_bai_xiang_ad"
            "霸气杨梅" -> "mo_li_xue_ya_ad"
            "山野栀子" -> "ya_shi_xiang_ad"
            // A 名缺图回退到 B
            "柳橙百香" -> "ba_qi_qing_ning_ad"
            "茉莉雪芽" -> "ba_qi_yang_mei_ad"
            "鸭屎香柠檬茶" -> "shan_ye_zhi_zi_ad"
            else -> null
        }
        if (alias != null) {
            val rid = context.resources.getIdentifier(alias, "drawable", context.packageName)
            if (rid != 0) return rid
        }
    }
    return R.drawable.placeholder
}

private fun getBeverageDrawableForRecipe(context: android.content.Context, recipeName: String): Int {
    // 饮品资源映射（竖图）
    val key = when (recipeName) {
        // 版本B
        "霸气青柠" -> "ba_qi_qing_ning"
        "霸气杨梅" -> "ba_qi_yang_mei"
        "山野栀子" -> "shan_ye_zhi_zi"
        "鸭屎香" -> "ya_shi_xiang"
        // 版本A（兼容）
        "柳橙百香" -> "liu_cheng_bai_xiang"
        "茉莉雪芽" -> "mo_li_xue_ya"
        "鸭屎香柠檬茶" -> "ya_shi_xiang"
        else -> null
    }
    if (key != null) {
        val id = context.resources.getIdentifier(key, "drawable", context.packageName)
        if (id != 0) return id
        // 双向映射：B→A 与 A→B
        val alias = when (recipeName) {
            // B 名缺图回退到 A
            "霸气青柠" -> "liu_cheng_bai_xiang"
            "霸气杨梅" -> "mo_li_xue_ya"
            "山野栀子" -> "ya_shi_xiang"
            // A 名缺图回退到 B
            "柳橙百香" -> "ba_qi_qing_ning"
            "茉莉雪芽" -> "ba_qi_yang_mei"
            "鸭屎香柠檬茶" -> "shan_ye_zhi_zi"
            else -> null
        }
        if (alias != null) {
            val rid = context.resources.getIdentifier(alias, "drawable", context.packageName)
            if (rid != 0) return rid
        }
    }
    return R.drawable.placeholder
}

// PreferencesPanel 已删除：该模块与本APP需求无关
