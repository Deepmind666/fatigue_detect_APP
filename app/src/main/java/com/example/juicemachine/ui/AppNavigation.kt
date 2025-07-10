package com.example.juicemachine.ui

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

sealed class Screen(val route: String) {
    object DrinkMenu : Screen("drink_menu")
    object Admin : Screen("admin")
    object EditRecipe : Screen("edit_recipe")
}

@Composable
fun AppNavigation(viewModel: DrinkMenuViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

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
                onConfirmDialog = viewModel::onConfirmDialog,
                onDismissDialog = viewModel::onDismissDialog,
                onHeaderLongClick = viewModel::onHeaderLongClick,
                onLoginAttempt = viewModel::onLoginAttempt
            )
        }
        composable(Screen.Admin.route) {
            AdminScreen(
                recipes = uiState.recipes,
                onAddRecipe = { viewModel.onNavigateToEdit(null) },
                onEditRecipe = viewModel::onNavigateToEdit,
                onDeleteRecipe = viewModel::deleteRecipe,
                onClean = viewModel::onClean,
                onAddWater = viewModel::onAddWater,
                onTestTemp = viewModel::onTestTemp,
                onConnect = viewModel::onAdminMakeJuice,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.EditRecipe.route) {
            EditRecipeScreen(
                recipe = uiState.recipeToEdit,
                onNameChange = viewModel::onRecipeNameChange,
                onWaterChange = viewModel::onWaterChange,
                onJuiceChange = viewModel::onJuiceChange,
                onPriceChange = viewModel::onPriceChange,
                onStockChange = viewModel::onStockChange,
                onJuiceChannelChange = viewModel::onJuiceChannelChange,
                onSave = {
                    viewModel.saveRecipe(uiState.recipeToEdit)
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
} 