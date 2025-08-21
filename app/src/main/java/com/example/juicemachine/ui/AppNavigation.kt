package com.example.juicemachine.ui
import android.util.Log

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.juicemachine.ui.viewmodel.DrinkMenuViewModel
import com.example.juicemachine.ui.WeightChangeDialog

sealed class Screen(val route: String) {
    object DrinkMenu : Screen("drink_menu")
    object Admin : Screen("admin")
    object EditRecipe : Screen("edit_recipe")
    object Statistics : Screen("statistics")
}

@Composable
fun AppNavigation(viewModel: DrinkMenuViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    // 全局：无论在哪个页面都显示重量异常弹窗（NavHost 外层），统一使用自定义 WeightChangeDialog
    // 避免重复弹两次，移除简单AlertDialog版本
// 全局：无论在哪个页面都显示重量异常弹窗
android.util.Log.e("AppNavigation", "=== 检查全局弹窗状态: showWeightChangeDialog=${uiState.showWeightChangeDialog} ===")
if (uiState.showWeightChangeDialog) {
    android.util.Log.e("AppNavigation", "=== 全局弹窗条件命中，显示完整WeightChangeDialog ===")
    WeightChangeDialog(
        onContinue = viewModel::onContinueRecipe,
        onRestart = viewModel::onRestartRecipe,
        onDismiss = viewModel::onContinueRecipe
    )
}

    if (uiState.navigateToAdmin) {
        LaunchedEffect(Unit) {
            navController.navigate(Screen.Admin.route)
            viewModel.onAdminNavigated()
        }
    }
    if (uiState.navigateToEdit) {
        LaunchedEffect(Unit) {
            navController.navigate(Screen.EditRecipe.route)
            viewModel.onEditNavigated()
        }
    }


    NavHost(navController = navController, startDestination = Screen.DrinkMenu.route) {
        composable(Screen.DrinkMenu.route) {
            DrinkMenuScreen(
                uiState = uiState,
                onRecipeClick = viewModel::onRecipeClick,
                onHeaderLongClick = viewModel::onHeaderLongClick,
                onDismissDialog = viewModel::onDismissDialog,
                onConfirmDialog = viewModel::onConfirmDialog,
                onLoginAttempt = viewModel::onLoginAttempt,
                onDismissError = viewModel::clearError,
                onContinueRecipe = viewModel::onContinueRecipe,
                onRestartRecipe = viewModel::onRestartRecipe
            )
        }
        composable(Screen.Admin.route) {
            AdminScreen(
                recipes = uiState.recipes,
                errorMessage = uiState.errorMessage,
                onAddRecipe = { viewModel.onNavigateToEdit(null) },
                onEditRecipe = viewModel::onNavigateToEdit,
                onDeleteRecipe = viewModel::deleteRecipe,
                onClean = viewModel::onClean,
                onStop = viewModel::onStopAction,
                onNavigateBack = { navController.popBackStack() },
                onDismissError = viewModel::clearError,
                onRestoreDefaults = viewModel::restoreDefaultRecipes,
                onNavigateToStatistics = { navController.navigate(Screen.Statistics.route) }
            )
        }
        composable(Screen.EditRecipe.route) {
            EditRecipeScreen(
                recipe = uiState.recipeToEdit,
                onNameChange = viewModel::onRecipeNameChange,
                onWaterChange = viewModel::onWaterChange,
                onJuiceChange = viewModel::onJuiceChange,
                onPriceChange = viewModel::onPriceChange,
                onStockChange = viewModel::onRemainWeightChange,
                onJuiceChannelChange = viewModel::onJuiceChannelChange,
                onSave = {
                    viewModel.saveRecipe(uiState.recipeToEdit)
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onImageSelected = viewModel::onImageSelected,
                selectedImageUri = uiState.selectedImageUri
            )
        }
        composable(Screen.Statistics.route) {
            StatisticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}



