package com.example.juicemachine.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.juicemachine.data.database.CupConfig
import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.data.hardware.HardwareManager
import com.example.juicemachine.data.repository.RecipeRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DrinkMenuUiState(
    val recipes: List<Recipe> = emptyList(),
    val isMachineConnected: Boolean = false,
    val selectedRecipe: Recipe? = null,
    val recipeToEdit: Recipe = Recipe(name = "", imageUri = null, imageResId = com.example.juicemachine.R.drawable.tao_ni_huan_xin), // Blank recipe for editing/creating
    val showLoginDialog: Boolean = false,
    val loginError: Boolean = false // This can be removed if login is simplified/removed
)

class DrinkMenuViewModel(
    private val recipeRepository: RecipeRepository,
    private val hardwareManager: HardwareManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrinkMenuUiState())
    val uiState: StateFlow<DrinkMenuUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            recipeRepository.allRecipes.collect { recipes ->
                _uiState.update { it.copy(recipes = recipes) }
            }
        }
        viewModelScope.launch {
            recipeRepository.isMachineConnected.collect { isConnected ->
                _uiState.update { it.copy(isMachineConnected = isConnected) }
            }
        }
    }


    // --- Dialog and Selection Functions ---
    fun onRecipeSelected(recipe: Recipe) {
        _uiState.update { it.copy(selectedRecipe = recipe) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(selectedRecipe = null) }
    }

    fun confirmCustomization(recipe: Recipe, cupSize: String) {
        val config = when (cupSize) {
            "小杯" -> recipe.small
            "中杯" -> recipe.medium
            else -> recipe.large
        }
        hardwareManager.sendMakeJuiceCommand(
            ice = config.ice,
            juice = config.juice,
            water = config.water
        )
        dismissDialog()
    }

    fun cleanMachine() {
        hardwareManager.sendCleanCommand()
    }

    fun connectToHardware() {
        hardwareManager.connect()
    }

    // --- Admin Functions ---
    fun onAdminLoginRequested() {
        // Reset error on new login attempt
        _uiState.update { it.copy(showLoginDialog = true, loginError = false) }
    }

    fun dismissLoginDialog() {
        _uiState.update { it.copy(showLoginDialog = false) }
    }

    fun onLoginAttempt(password: String, onLoginSuccess: () -> Unit) {
        if (password == "123456") {
            onLoginSuccess()
            dismissLoginDialog()
        } else {
            _uiState.update { it.copy(loginError = true) }
        }
    }

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
            recipeRepository.deleteRecipe(recipe)
        }
    }

    // --- Edit Recipe Functions ---
    fun loadRecipeForEdit(recipeId: Long) {
        viewModelScope.launch {
            if (recipeId == -1L) {
                // It's a new recipe
                _uiState.update { it.copy(recipeToEdit = Recipe(name = "", imageResId = com.example.juicemachine.R.drawable.tao_ni_huan_xin)) }
            } else {
                // Fetch existing recipe from database
                try {
                    val recipe = recipeRepository.getRecipeById(recipeId)
                    if (recipe != null) {
                        _uiState.update { it.copy(recipeToEdit = recipe) }
                    } else {
                        // Fallback to searching in current recipes list
                        val fallbackRecipe = uiState.value.recipes.find { it.id == recipeId }
                        if (fallbackRecipe != null) {
                            _uiState.update { it.copy(recipeToEdit = fallbackRecipe) }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DrinkMenuViewModel", "Error loading recipe for edit: ${e.message}")
                    // Fallback to searching in current recipes list
                    val fallbackRecipe = uiState.value.recipes.find { it.id == recipeId }
                    if (fallbackRecipe != null) {
                        _uiState.update { it.copy(recipeToEdit = fallbackRecipe) }
                    }
                }
            }
        }
    }

    fun onRecipeNameChange(name: String) {
        _uiState.update { it.copy(recipeToEdit = it.recipeToEdit.copy(name = name)) }
    }

    fun onImageUriChange(uri: String?) {
        _uiState.update {
            it.copy(recipeToEdit = it.recipeToEdit.copy(imageUri = uri, imageResId = null))
        }
    }

    fun onCupConfigChange(size: String, field: String, value: String) {
        val intValue = value.toIntOrNull() ?: 0
        _uiState.update {
            val recipe = it.recipeToEdit
            val updatedRecipe = when (size) {
                "小杯" -> recipe.copy(small = recipe.small.updateField(field, intValue))
                "中杯" -> recipe.copy(medium = recipe.medium.updateField(field, intValue))
                "大杯" -> recipe.copy(large = recipe.large.updateField(field, intValue))
                else -> recipe
            }
            it.copy(recipeToEdit = updatedRecipe)
        }
    }

    private fun CupConfig.updateField(field: String, value: Int): CupConfig {
        return when (field) {
            "冰" -> this.copy(ice = value)
            "果汁" -> this.copy(juice = value)
            "水" -> this.copy(water = value)
            else -> this
        }
    }

    fun saveRecipe(recipe: Recipe) {
        viewModelScope.launch {
            if (recipe.id == 0L) { // New recipe
                recipeRepository.insertRecipe(recipe)
            } else { // Existing recipe
                recipeRepository.updateRecipe(recipe)
            }
        }
    }
}

class DrinkMenuViewModelFactory(
    private val repository: RecipeRepository,
    private val hardwareManager: HardwareManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DrinkMenuViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DrinkMenuViewModel(repository, hardwareManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 