package com.example.juicemachine.ui

import android.content.Context
import android.net.Uri
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.juicemachine.ui.viewmodel.DrinkMenuViewModel

sealed class Screen(val route: String) {
    object Ads : Screen("ads")
    object DrinkMenu : Screen("drink_menu")
    object Admin : Screen("admin")
    object EditRecipe : Screen("edit_recipe")
    object Statistics : Screen("statistics")
    object Customer : Screen("customer")
}

@Composable
fun AppNavigation(viewModel: DrinkMenuViewModel, onFirstContentReady: () -> Unit) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { onFirstContentReady() }
    LaunchedEffect(Unit) {
        if (!isRunningOnEmulator()) {
            viewModel.initializeHardwareAfterFirstFrame()
        }
    }

    val prefs = remember(context) { context.getSharedPreferences("ads_prefs", Context.MODE_PRIVATE) }

    fun computeAds(): List<AdItem> {
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

        fun isReadableUri(uri: String): Boolean {
            if (uri.isBlank()) return false
            return try {
                val u = Uri.parse(uri)
                when (u.scheme) {
                    "android.resource" -> {
                        val name = u.lastPathSegment ?: return false
                        context.resources.getIdentifier(name, "drawable", context.packageName) != 0
                    }
                    "file" -> java.io.File(u.path ?: "").canRead()
                    "content" -> runCatching {
                        context.contentResolver.openInputStream(u)?.use { } != null
                    }.getOrElse { false }
                    else -> false
                }
            } catch (_: Exception) { false }
        }

        val persistedValid = persisted.filter { isReadableUri(it.uri) }
        if (persisted.isNotEmpty() && persistedValid.isEmpty()) {
            try { prefs.edit().remove("ads_image_uris").apply() } catch (_: Exception) {}
        }
        val builtIn = getBuiltInAds(context)
        return when {
            persistedValid.isNotEmpty() -> persistedValid
            else -> builtIn
        }
    }

    val imagesState = remember { mutableStateOf<List<AdItem>>(emptyList()) }
    LaunchedEffect(Unit) { imagesState.value = computeAds() }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "ads_image_uris") {
                imagesState.value = computeAds()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val images: List<AdItem> = imagesState.value

    val startRoute = Screen.Ads.route
    NavHost(navController = navController, startDestination = startRoute) {
        composable(Screen.Ads.route) {
            AdsScreen(
                images = images,
                intervalMs = 10_000L,
                onNavigateToUser = { navController.navigate(Screen.DrinkMenu.route) },
                onFirstImageReady = onFirstContentReady
            )
        }
        composable(Screen.DrinkMenu.route) {
            LaunchedEffect(Unit) { viewModel.onDrinkMenuVisible() }
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
                onDismissWeighResult = viewModel::onDismissWeighResult,
                onCancelWeighWaiting = viewModel::onCancelWeighWaiting,
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
                currentTemperature = uiState.currentTemperature,
                currentWeight = uiState.currentWeight,
                waterOnlySpeed = uiState.waterOnlySpeed,
                onWaterOnlySpeedChange = viewModel::onWaterOnlySpeedChange,
                onSaveWaterOnlySpeed = viewModel::saveWaterOnlySpeed
            )
        }
        composable(Screen.EditRecipe.route) {
            val s by viewModel.uiState.collectAsState()
            EditRecipeScreen(
                recipe = s.recipeToEdit,
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
                onPulpTotalCupsChange = viewModel::onPulpTotalCupsChange,
                onPulpDecIntervalChange = viewModel::onPulpDecIntervalChange,
                onPulpDecAmountChange = viewModel::onPulpDecAmountChange,
                onSave = { viewModel.saveRecipe(s.recipeToEdit) },
                onNavigateBack = { navController.popBackStack() },
                onImageSelected = viewModel::onImageSelected,
                selectedImageUri = s.selectedImageUri
            )
        }
        composable(Screen.Statistics.route) {
            StatisticsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Customer.route) {
            CustomerCleanScreen(onClean = viewModel::onCleanCommand, onStop = viewModel::onStopCommand, onBack = { navController.popBackStack() }, onTimeoutToAds = { navController.popBackStack() }, timeoutMs = 15_000L)
        }
    }

    if (uiState.navigateToAdmin) {
        LaunchedEffect(uiState.navigateToAdmin) {
            if (uiState.navigateToAdmin) {
                navController.navigate(Screen.Admin.route)
                viewModel.onAdminNavigated()
            }
        }
    }
    if (uiState.navigateToCustomer) {
        LaunchedEffect(uiState.navigateToCustomer) {
            if (uiState.navigateToCustomer) {
                navController.navigate(Screen.Customer.route)
                viewModel.onCustomerNavigated()
            }
        }
    }
    if (uiState.navigateToEdit) {
        LaunchedEffect(uiState.navigateToEdit) {
            if (uiState.navigateToEdit) {
                navController.navigate(Screen.EditRecipe.route)
                viewModel.onEditNavigated()
            }
        }
    }
}

private fun isRunningOnEmulator(): Boolean {
    val fp = android.os.Build.FINGERPRINT.lowercase()
    val model = android.os.Build.MODEL.lowercase()
    val product = android.os.Build.PRODUCT.lowercase()
    val manufacturer = android.os.Build.MANUFACTURER.lowercase()
    val hardware = android.os.Build.HARDWARE.lowercase()
    return (
        fp.contains("generic") || fp.contains("unknown") ||
        model.contains("emulator") || model.contains("android sdk built for x86") ||
        manufacturer.contains("genymotion") ||
        hardware.contains("goldfish") || hardware.contains("ranchu") ||
        product.contains("sdk") || product.contains("google_sdk") || product.contains("vbox")
    )
}

private fun getBuiltInAds(context: Context): List<AdItem> {
    val pkg = context.packageName
    return listOf(
        AdItem(uri = "android.resource://$pkg/drawable/ba_qi_qing_ning_ad", title = "霸气青柠"),
        AdItem(uri = "android.resource://$pkg/drawable/ba_qi_yang_mei_ad", title = "霸气杨梅"),
        AdItem(uri = "android.resource://$pkg/drawable/ya_shi_xiang_ad", title = "鸭屎香柠檬茶")
    )
}
