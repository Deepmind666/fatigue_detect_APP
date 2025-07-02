package com.example.juicemachine.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.juicemachine.ui.viewmodel.DrinkMenuViewModel

@Composable
fun JuiceMachineApp(viewModel: DrinkMenuViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    NavHost(navController = navController, startDestination = "drink_menu") {
        composable("drink_menu") {
            DrinkMenuScreen(
                uiState = uiState,
                onRecipeSelected = viewModel::onRecipeSelected,
                onHeaderLongClick = viewModel::onAdminLoginRequested,
                onDismissDialog = {
                    viewModel.dismissDialog()
                    viewModel.dismissLoginDialog()
                },
                onConfirmDialog = { recipe, cupSize ->
                    viewModel.confirmCustomization(recipe, cupSize)
                    viewModel.dismissDialog()
                },
                onMakeJuice = { /* Logic might be needed here or in VM */ },
                onClean = viewModel::cleanMachine,
                onAddWater = { /* TODO */ },
                onTestTemp = { /* TODO */ },
                onConnectClick = viewModel::connectToHardware,
                onLoginAttempt = { password ->
                    viewModel.onLoginAttempt(password) {
                        navController.navigate("admin")
                    }
                }
            )
        }
        composable("admin") {
            AdminScreen(
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() },
                onAddClick = {
                    // Navigate to edit screen with no ID for creation
                    navController.navigate("edit_recipe/-1")
                },
                onEditClick = { recipe ->
                    navController.navigate("edit_recipe/${recipe.id}")
                },
                onDeleteClick = { recipe ->
                    viewModel.deleteRecipe(recipe)
                },
                onCleanClick = {
                    viewModel.cleanMachine()
                }
            )
        }
        composable(
            "edit_recipe/{recipeId}",
            arguments = listOf(navArgument("recipeId") { type = NavType.LongType })
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getLong("recipeId")
            EditRecipeScreen(
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() },
                onSave = { recipe ->
                    viewModel.saveRecipe(recipe)
                    navController.popBackStack()
                },
                onNameChange = { viewModel.onRecipeNameChange(it) },
                onImageUriChange = { viewModel.onImageUriChange(it) },
                onCupConfigChange = { size, field, value ->
                    viewModel.onCupConfigChange(size, field, value)
                },
                recipeId = recipeId ?: -1,
                loadRecipeForEdit = { id -> viewModel.loadRecipeForEdit(id) }
            )
        }
    }
} 