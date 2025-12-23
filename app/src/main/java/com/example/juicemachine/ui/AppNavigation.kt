package com.example.juicemachine.ui

import android.content.SharedPreferences
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.juicemachine.JuiceMachineApplication
import com.example.juicemachine.ui.viewmodel.DrinkMenuViewModel
import com.example.juicemachine.ui.viewmodel.DrinkMenuViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen(val route: String) {
    object Ads : Screen("ads")
    object DrinkMenu : Screen("drink_menu")
    object Admin : Screen("admin")
    object EditRecipe : Screen("edit_recipe")
    object Statistics : Screen("statistics")
    object Customer : Screen("customer")
}

@Composable
fun AppNavigation(onFirstContentReady: () -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val app = activity?.application as? JuiceMachineApplication
    val viewModelState = remember { mutableStateOf<DrinkMenuViewModel?>(null) }
    val prewarmedRef = remember { BooleanArray(1) }
    val splashReleasedRef = remember { BooleanArray(1) }

    fun requireViewModel(): DrinkMenuViewModel {
        val existing = viewModelState.value
        if (existing != null) return existing

        val safeActivity = checkNotNull(activity)
        val safeApp = checkNotNull(app)
        val prefs = safeActivity.getSharedPreferences("juice_prefs", Context.MODE_PRIVATE)
        val factory = DrinkMenuViewModelFactory(
            repository = safeApp.repository,
            hardwareManager = safeApp.hardwareManager,
            orderRepository = safeApp.orderRepository,
            sharedPreferences = prefs,
            appContext = safeActivity.applicationContext
        )
        val vm = ViewModelProvider(safeActivity, factory)[DrinkMenuViewModel::class.java]
        viewModelState.value = vm
        return vm
    }

    fun releaseSplashOnce() {
        if (!splashReleasedRef[0]) {
            splashReleasedRef[0] = true
            onFirstContentReady()
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        val start = SystemClock.uptimeMillis()
        while (!splashReleasedRef[0] && SystemClock.uptimeMillis() - start < 2_500L) {
            delay(50)
        }
        if (!splashReleasedRef[0]) releaseSplashOnce()
    }

    val vm = viewModelState.value
    LaunchedEffect(vm) {
        if (vm != null && !isRunningOnEmulator()) {
            vm.initializeHardwareAfterFirstFrame()
        }
    }

    val prefs = remember(context) { context.getSharedPreferences("ads_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    suspend fun computeAds(): List<AdItem> = withContext(Dispatchers.IO) {
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
        return@withContext when {
            persistedValid.isNotEmpty() -> persistedValid
            else -> builtIn
        }
    }

    val imagesState = remember { mutableStateOf(getBuiltInAds(context)) }
    LaunchedEffect(Unit) { imagesState.value = computeAds() }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "ads_image_uris") {
                scope.launch { imagesState.value = computeAds() }
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
                pageHoldMs = 10_000L,
                scrollDurationMs = 4_000,
                onNavigateToUser = {
                    navController.navigate(Screen.DrinkMenu.route)
                },
                onFirstImageReady = {
                    releaseSplashOnce()
                    if (!prewarmedRef[0]) {
                        prewarmedRef[0] = true
                        scope.launch {
                            val t0 = SystemClock.uptimeMillis()
                            while (SystemClock.uptimeMillis() - t0 < 600L) delay(50)
                            runCatching { app?.preheatAdsAsync() }
                            runCatching { app?.prewarmCoreDependenciesAsync() }
                            runCatching { requireViewModel() }
                        }
                    }
                }
            )
        }
        composable(Screen.DrinkMenu.route) {
            val viewModel = requireViewModel()
            val uiState by viewModel.uiState.collectAsState()
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
            val viewModel = requireViewModel()
            val uiState by viewModel.uiState.collectAsState()
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
            val viewModel = requireViewModel()
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
            val viewModel = requireViewModel()
            CustomerCleanScreen(onClean = viewModel::onCleanCommand, onStop = viewModel::onStopCommand, onBack = { navController.popBackStack() }, onTimeoutToAds = { navController.popBackStack() }, timeoutMs = 15_000L)
        }
    }

    if (vm != null) {
        val navigateToAdminFlow = remember(vm) { vm.uiState.map { it.navigateToAdmin }.distinctUntilChanged() }
        val navigateToCustomerFlow = remember(vm) { vm.uiState.map { it.navigateToCustomer }.distinctUntilChanged() }
        val navigateToEditFlow = remember(vm) { vm.uiState.map { it.navigateToEdit }.distinctUntilChanged() }

        val navigateToAdmin by navigateToAdminFlow.collectAsState(initial = false)
        val navigateToCustomer by navigateToCustomerFlow.collectAsState(initial = false)
        val navigateToEdit by navigateToEditFlow.collectAsState(initial = false)

        LaunchedEffect(navigateToAdmin) {
            if (navigateToAdmin) {
                navController.navigate(Screen.Admin.route)
                vm.onAdminNavigated()
            }
        }
        LaunchedEffect(navigateToCustomer) {
            if (navigateToCustomer) {
                navController.navigate(Screen.Customer.route)
                vm.onCustomerNavigated()
            }
        }
        LaunchedEffect(navigateToEdit) {
            if (navigateToEdit) {
                navController.navigate(Screen.EditRecipe.route)
                vm.onEditNavigated()
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
