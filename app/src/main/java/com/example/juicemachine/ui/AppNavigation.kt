package com.example.juicemachine.ui

import android.util.Log
import android.content.Context
import android.os.Build

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.juicemachine.ui.viewmodel.DrinkMenuViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.graphics.BitmapFactory

sealed class Screen(val route: String) {
    object DrinkMenu : Screen("drink_menu")
    object Admin : Screen("admin")
    object EditRecipe : Screen("edit_recipe")
    object Statistics : Screen("statistics")
    object Ads : Screen("ads")
    // 新增：客户管理页
    object Customer : Screen("customer")
}

@Composable
fun AppNavigation(viewModel: DrinkMenuViewModel, onFirstContentReady: () -> Unit) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val adsPrefs = remember(context) { context.getSharedPreferences("ads_prefs", android.content.Context.MODE_PRIVATE) }
    var adsTick by remember { mutableStateOf(0) }
    DisposableEffect(adsPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "ads_image_uris") {
                adsTick++
            }
        }
        adsPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { adsPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // 首帧：记录组合进入日志，帮助定位黑屏是否停在启动背景
    LaunchedEffect(Unit) {
        // 组合开始即通知首内容就绪，释放 Splash（若仍在）
        onFirstContentReady()
        com.example.juicemachine.util.DebugLogger.i("AppNavigation", "进入组合：AppNavigation 组合已开始")
    }

    // 统一计算广告素材列表（持久化 > 内置 > 配方）供前景/背景复用
    val images: List<AdItem> = remember(uiState.recipes, adsTick) {
        val prefs = adsPrefs
        val json = prefs.getString("ads_image_uris", null)
        val persisted: List<AdItem> = try {
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
                        }
                    }
                }
            }
        } catch (_: Exception) { emptyList() }

        // 过滤不可读的持久化路径，避免外部无权限/无效URI导致首屏加载失败
        fun isReadableUri(uri: String): Boolean {
            return try {
                val u = Uri.parse(uri)
                when (u.scheme) {
                    "android.resource" -> {
                        // 能解码出尺寸才认为有效
                        context.contentResolver.openInputStream(u)?.use { input ->
                            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeStream(input, null, opts)
                            opts.outWidth > 0 && opts.outHeight > 0
                        } ?: false
                    }
                    "file" -> java.io.File(u.path ?: "").canRead()
                    "content" -> {
                        context.contentResolver.openInputStream(u)?.use { input ->
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeStream(input, null, opts)
                            opts.outWidth > 0 && opts.outHeight > 0
                        } ?: false
                    }
                    else -> false
                }
            } catch (_: Exception) { false }
        }

        val persistedValid: List<AdItem> = persisted.filter { it.uri.isNotBlank() && isReadableUri(it.uri) }
        // 若持久化存在但全部不可解码，则清理持久化，避免长期占位且显示兜底文本
        if (persisted.isNotEmpty() && persistedValid.isEmpty()) {
            try {
                prefs.edit().remove("ads_image_uris").apply()
            } catch (_: Exception) { /* ignore */ }
        }

        val fromRecipes: List<AdItem> = uiState.recipes
            .map {
                val resId = getDrawableForRecipe(context, it.name)
                val resName = try { context.resources.getResourceEntryName(resId) } catch (_: Exception) { null }
                val defaultUri = if (resName != null) {
                    "android.resource://${context.packageName}/drawable/$resName"
                } else {
                    "android.resource://${context.packageName}/drawable/placeholder"
                }
                AdItem(uri = it.imageUri ?: defaultUri, title = it.name)
            }
            .distinctBy { it.uri }
            .take(5)
        val builtIn: List<AdItem> = getBuiltInAds(context)

        when {
            persistedValid.isNotEmpty() -> persistedValid
            builtIn.isNotEmpty() -> builtIn
            else -> fromRecipes
        }
    }

    // 立即触发硬件连接初始化（真机执行；模拟器跳过，避免无设备环境下的系统服务异常）
    LaunchedEffect(Unit) {
        if (!isRunningOnEmulator()) {
            viewModel.initializeHardwareAfterFirstFrame()
        } else {
            android.util.Log.d("AppNavigation", "检测到模拟器环境：跳过硬件连接初始化")
        }
        // 延后触发：默认配方兜底插入与广告预热（避免冷启动阶段触发Room与图片IO）
        try {
            val app = (context.applicationContext as? com.example.juicemachine.JuiceMachineApplication)
            app?.ensureDefaultRecipesAsync()
            app?.preheatAdsAsync()
        } catch (_: Exception) { /* ignore */ }
    }
    // 全局：无论在哪个页面都显示重量异常弹窗（NavHost 外层），统一使用自定义 WeightChangeDialog
    // 避免重复弹两次，移除简单AlertDialog版本
    // 全局：无论在哪个页面都显示重量异常弹窗
    android.util.Log.d("AppNavigation", "检查弹窗: showWeightChangeDialog=${uiState.showWeightChangeDialog}")
    if (uiState.showWeightChangeDialog) {
        WeightChangeDialog(
            onContinue = viewModel::onContinueRecipe,
            onRestart = viewModel::onRestartRecipe,
            onDismiss = viewModel::onContinueRecipe
        )
    }

    // 新增：全局称重等待与结果提示（适用于后台页面触发称重）
    if (uiState.showWeighWaitingDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = viewModel::onCancelWeighWaiting) {
            androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), elevation = androidx.compose.material3.CardDefaults.cardElevation(12.dp)) {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.padding(20.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                    androidx.compose.material3.Text("正在称重，请稍候…", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    uiState.weighResultMessage?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::onDismissWeighResult,
            title = { androidx.compose.material3.Text("称重结果") },
            text = { androidx.compose.material3.Text(msg) },
            confirmButton = { androidx.compose.material3.Button(onClick = viewModel::onDismissWeighResult) { androidx.compose.material3.Text("知道了") } }
        )
    }

    if (uiState.navigateToAdmin) {
        LaunchedEffect(Unit) {
            navController.navigate(Screen.Admin.route)
            viewModel.onAdminNavigated()
        }
    }
    // 新增：客户管理界面导航
    if (uiState.navigateToCustomer) {
        LaunchedEffect(Unit) {
            navController.navigate(Screen.Customer.route)
            viewModel.onCustomerNavigated()
        }
    }
    
    // 客户清洗界面导航已移除
    if (uiState.navigateToEdit) {
        LaunchedEffect(Unit) {
            navController.navigate(Screen.EditRecipe.route)
            viewModel.onEditNavigated()
        }
    }


    // 背景保持纯白：取消静态广告背景兜底

    // 恢复：统一从广告页启动，确保用户体验一致
    val startRoute = Screen.Ads.route

    // 前景导航栈（保持与原实现一致）
    NavHost(navController = navController, startDestination = startRoute) {
        composable(Screen.Ads.route) {
            AdsScreen(
                images = images,
                intervalMs = 10_000L,
                onNavigateToUser = { navController.navigate(Screen.DrinkMenu.route) },
                // 首图加载成功时回调，确保 Splash 已退出
                onFirstImageReady = onFirstContentReady
            )
        }
        composable(Screen.DrinkMenu.route) {
            // 页面进入：启动配方收集（避免冷启动阶段打开数据库）
            LaunchedEffect(Unit) { viewModel.onDrinkMenuVisible() }
            // 页面退出：停止收集，释放资源
            DisposableEffect(Unit) {
                onDispose { viewModel.onDrinkMenuHidden() }
            }
            DrinkMenuScreen(
                uiState = uiState,
                onRecipeClick = viewModel::onRecipeClick,
                onHeaderLongClick = viewModel::onHeaderLongClick,
                onWaterOnlyToggle = viewModel::onWaterOnlyToggle,
                onEmergencyStop = viewModel::onEmergencyStop,
                onDismissDialog = viewModel::onDismissDialog,
                onConfirmDialog = viewModel::onConfirmDialog,
                onLoginAttempt = viewModel::onLoginAttempt,
                onDismissError = viewModel::clearError,
                onContinueRecipe = viewModel::onContinueRecipe,
                onRestartRecipe = viewModel::onRestartRecipe,
                // 新增：称重结果提示弹窗关闭
                onDismissWeighResult = viewModel::onDismissWeighResult,
                // 新增：称重等待取消（点击弹窗外部取消）
                onCancelWeighWaiting = viewModel::onCancelWeighWaiting,
                // 新增：无操作返回广告页
                onTimeoutToAds = { navController.popBackStack() },
                timeoutMs = 15_000L,
                onResetStock = viewModel::onResetStock
            )
        }
        composable(Screen.Admin.route) {
            AdminScreen(
                recipes = uiState.recipes,
                errorMessage = uiState.errorMessage,
                onAddRecipe = { viewModel.onNavigateToEdit(null) },
                onEditRecipe = { viewModel.onNavigateToEdit(it) },
                onDeleteRecipe = viewModel::deleteRecipe,
                onClean = viewModel::onCleanCommand,
                onStop = viewModel::onStopCommand,
                onTare = viewModel::onTare,
                onWeigh = viewModel::onWeigh,
                onCalibrateStandard = viewModel::onCalibrateStandard,
                onNavigateBack = { navController.popBackStack() },
                onDismissError = viewModel::clearError,
                onRestoreDefaults = viewModel::restoreDefaultRecipes,
                onNavigateToStatistics = { navController.navigate(Screen.Statistics.route) },
                // 传入温度与重量数据显示
                currentTemperature = uiState.currentTemperature,
                currentWeight = uiState.currentWeight,
                // 新增：只出水水速与回调
                waterOnlySpeed = uiState.waterOnlySpeed,
                onWaterOnlySpeedChange = viewModel::onWaterOnlySpeedChange,
                onSaveWaterOnlySpeed = viewModel::saveWaterOnlySpeed
            )
        }
        // 新增：客户管理界面
        composable(Screen.Customer.route) {
            CustomerCleanScreen(
                onClean = viewModel::onCleanCommand,
                onStop = viewModel::onStopCommand,
                onBack = { navController.popBackStack() },
                // 新增：无操作返回广告页（从客户页返回上一页）
                onTimeoutToAds = { navController.popBackStack() },
                timeoutMs = 15_000L
            )
        }
        composable(Screen.EditRecipe.route) {
            val uiState by viewModel.uiState.collectAsState()
            EditRecipeScreen(
                recipe = uiState.recipeToEdit,
                onNameChange = viewModel::onRecipeNameChange,
                onWaterChange = viewModel::onWaterChange,
                onJuiceChange = viewModel::onJuiceChange,
                onPriceChange = viewModel::onPriceChange,
                onStockChange = viewModel::onDefaultRemainWeightChange,
                onCurrentRemainWeightChange = viewModel::onCurrentRemainWeightChange,
                onJuiceTypeChange = viewModel::onJuiceTypeChange,
                onJuiceChannelChange = viewModel::onJuiceChannelChange,
                onHasPulpChange = viewModel::onHasPulpChange,
                onJuiceSpeedChange = viewModel::onJuiceSpeedChange,
                // 新增：果肉补偿参数回调（按饮品独立）
                onPulpTotalCupsChange = viewModel::onPulpTotalCupsChange,
                onPulpDecIntervalChange = viewModel::onPulpDecIntervalChange,
                onPulpDecAmountChange = viewModel::onPulpDecAmountChange,
                onSave = { viewModel.saveRecipe(uiState.recipeToEdit) },
                onNavigateBack = { navController.popBackStack() },
                onImageSelected = viewModel::onImageSelected,
                selectedImageUri = uiState.selectedImageUri
            )
        }
        composable(Screen.Statistics.route) {
            StatisticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        // 客户清洗界面已删除
    }
}

private fun isRunningOnEmulator(): Boolean {
    val fp = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val product = Build.PRODUCT.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    val hardware = Build.HARDWARE.lowercase()
    return (
        fp.contains("generic") || fp.contains("unknown") ||
        model.contains("emulator") || model.contains("android sdk built for x86") ||
        manufacturer.contains("genymotion") ||
        hardware.contains("goldfish") || hardware.contains("ranchu") ||
        product.contains("sdk") || product.contains("google_sdk") || product.contains("vbox")
    )
}

// 旧函数保留为未使用（避免重复定义导致冲突）
// 移除旧版未使用映射，改为仅支持当前三种饮品

// 本地帮助函数：根据配方名返回内置图片资源，供广告页默认展示（已更新映射并兼容旧名称）
private fun getDrawableForRecipe(context: Context, recipeName: String): Int {
    val key = when (recipeName) {
        // 版本B
        "霸气青柠" -> "ba_qi_qing_ning_ad"
        "霸气杨梅" -> "ba_qi_yang_mei_ad"
        "山野栀子" -> "shan_ye_zhi_zi_ad"
        // 版本A（兼容）
        "柳橙百香" -> "liu_cheng_bai_xiang_ad"
        "茉莉雪芽" -> "mo_li_xue_ya_ad"
        "鸭屎香柠檬茶" -> "ya_shi_xiang_ad"
        else -> null
    }
    if (key != null) {
        val id = context.resources.getIdentifier(key, "drawable", context.packageName)
        if (id != 0) return id
    }
    return com.example.juicemachine.R.drawable.placeholder
}

private fun getBuiltInAds(context: Context): List<AdItem> {
    val pkg = context.packageName
    return listOf(
        AdItem(uri = "android.resource://$pkg/drawable/ba_qi_qing_ning_ad", title = "霸气青柠"),
        AdItem(uri = "android.resource://$pkg/drawable/ba_qi_yang_mei_ad", title = "霸气杨梅"),
        AdItem(uri = "android.resource://$pkg/drawable/shan_ye_zhi_zi_ad", title = "山野栀子")
    )
}

// 已固定为三张指定 2:1 广告图，见上方 getBuiltInAds 实现

// 兜底背景：首屏始终显示一张广告图（或纯黑），避免白屏
// 删除静态广告背景兜底，恢复应用主题的纯白背景



