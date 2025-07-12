package com.example.juicemachine.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.juicemachine.R
import com.example.juicemachine.data.database.CupConfig
import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.data.hardware.HardwareManager
import com.example.juicemachine.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DrinkMenuUiState(
    val recipes: List<Recipe> = emptyList(),
    val connectionStatus: String = "未连接",
    val temperature: String = "--℃",
    val selectedRecipe: Recipe? = null,
    val recipeToEdit: Recipe = Recipe(id = 0, name = "", water = 0, juice = 0, price = 0, remainWeight = 0, juiceChannel = 1),
    val showLoginDialog: Boolean = false,
    val loginError: Boolean = false,
    val navigateToAdmin: Boolean = false,
    val navigateToEdit: Boolean = false,
    val errorMessage: String? = null // 新增错误提示
)

class DrinkMenuViewModel(
    private val recipeRepository: RecipeRepository,
    private val hardwareManager: HardwareManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrinkMenuUiState())
    val uiState: StateFlow<DrinkMenuUiState> = _uiState.asStateFlow()

    init {
        loadRecipes()
        // 仅插入三种核心饮料，避免重复插入
        viewModelScope.launch {
            val all = recipeRepository.allRecipes.first() // 这里all就是List<Recipe>
            if (all.none { it.name == "茉莉雪芽" }) {
                recipeRepository.insertRecipe(
                    Recipe(name = "茉莉雪芽", water = 105, juice = 175, price = 8, remainWeight = 1000, juiceChannel = 1)
                )
            }
            if (all.none { it.name == "柳橙百香" }) {
                recipeRepository.insertRecipe(
                    Recipe(name = "柳橙百香", water = 180, juice = 100, price = 9, remainWeight = 1000, juiceChannel = 2)
                )
            }
            if (all.none { it.name == "满杯桑葚" }) {
                recipeRepository.insertRecipe(
                    Recipe(name = "满杯桑葚", water = 130, juice = 150, price = 10, remainWeight = 1000, juiceChannel = 3)
                )
            }
        }
        hardwareManager.setOnStatusListener { msg ->
            _uiState.update { it.copy(connectionStatus = msg, errorMessage = if (msg.contains("未连接") || msg.contains("失败")) msg else null) }
        }
        hardwareManager.connect { status ->
            _uiState.update { it.copy(temperature = status) }
        }
    }

    private fun loadRecipes() {
        viewModelScope.launch {
            recipeRepository.allRecipes.collect { recipes ->
                Log.d("DrinkMenuViewModel", "当前数据库饮料数量: ${recipes.size}")
                _uiState.update { it.copy(recipes = recipes) }
            }
        }
    }

    fun onRecipeClick(recipe: Recipe) {
        _uiState.update { it.copy(selectedRecipe = recipe) }
    }

    fun onDismissDialog() {
        _uiState.update { it.copy(selectedRecipe = null, showLoginDialog = false, loginError = false) }
    }

    fun onConfirmDialog(recipe: Recipe, cupSize: String, withIce: Boolean) {
        if (!hardwareManager.isConnected) {
            _uiState.update { it.copy(errorMessage = "串口未连接，无法下单") }
            return
        }
        
        // 添加调试信息
        Log.d("DrinkMenuViewModel", "确认下单: 饮品=${recipe.name}, 杯型=$cupSize, 冰度=${if(withIce) "正常冰" else "去冰"}")
        
        hardwareManager.makeJuice(recipe, cupSize, withIce)
        
        // 下单后自动扣减剩余重量（根据杯型计算实际消耗）
        viewModelScope.launch {
            val actualJuiceConsumption = when (cupSize) {
                "大杯" -> (recipe.juice * 1.3).toInt()
                else -> recipe.juice
            }
            val newRemain = (recipe.remainWeight - actualJuiceConsumption).coerceAtLeast(0)
            recipeRepository.updateRemainWeight(recipe.id, newRemain)
        }
        _uiState.update { it.copy(selectedRecipe = null) }
    }

    fun onClean() {
        // 添加调试信息
        _uiState.update { it.copy(errorMessage = "发送清洗开始指令") }
        hardwareManager.sendAdminCommand(0x02)
    }

    fun onAddWater() {
        // 添加调试信息
        _uiState.update { it.copy(errorMessage = "发送清洗结束指令") }
        hardwareManager.sendAdminCommand(0x03)
    }

    fun onTestTemp() {
        // 添加调试信息
        _uiState.update { it.copy(errorMessage = "发送去皮指令") }
        hardwareManager.sendAdminCommand(0x04)
    }

    fun onAdminMakeJuice() {
        // 添加调试信息
        _uiState.update { it.copy(errorMessage = "发送称重指令") }
        hardwareManager.sendAdminCommand(0x05)
    }

    fun onHeaderLongClick() {
        _uiState.update { it.copy(showLoginDialog = true) }
    }

    fun onLoginAttempt(password: String) {
        if (password == "666666") {
            _uiState.update { it.copy(showLoginDialog = false, navigateToAdmin = true) }
        } else {
            _uiState.update { it.copy(loginError = true) }
        }
    }

    fun onAdminNavigated() {
        _uiState.update { it.copy(navigateToAdmin = false) }
    }

    fun onNavigateToEdit(recipe: Recipe?) {
        val recipeToEdit = recipe ?: Recipe(id = 0, name = "", water = 0, juice = 0, price = 0, remainWeight = 0, juiceChannel = 1)
        _uiState.update { it.copy(recipeToEdit = recipeToEdit, navigateToEdit = true) }
    }

    fun onEditNavigated() {
        _uiState.update { it.copy(navigateToEdit = false) }
                        }

    fun onRecipeNameChange(name: String) {
        _uiState.update { currentState ->
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(name = name)
            )
        }
    }

    fun onRemainWeightChange(remainWeight: String) {
        _uiState.update { currentState ->
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(remainWeight = remainWeight.toIntOrNull() ?: 0)
            )
        }
    }

    fun onJuiceChannelChange(channel: String) {
        _uiState.update { currentState ->
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(juiceChannel = channel.toIntOrNull() ?: 1)
            )
        }
    }

    fun onWaterChange(water: String) {
        _uiState.update { currentState ->
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(water = water.toIntOrNull() ?: 0)
            )
        }
    }

    fun onJuiceChange(juice: String) {
        _uiState.update { currentState ->
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(juice = juice.toIntOrNull() ?: 0)
            )
        }
    }

    fun onPriceChange(price: String) {
        _uiState.update { currentState ->
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(price = price.toIntOrNull() ?: 0)
            )
        }
    }

    fun saveRecipe(recipe: Recipe) {
        viewModelScope.launch {
            if (recipe.id == 0) {
                recipeRepository.insertRecipe(recipe)
            } else {
                recipeRepository.updateRecipe(recipe)
            }
        }
    }

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
            recipeRepository.deleteRecipe(recipe)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // 添加恢复默认数据的函数
    fun restoreDefaultRecipes() {
        viewModelScope.launch {
            // 强制插入或更新三种核心饮料
            val coreRecipes = listOf(
                Recipe(name = "茉莉雪芽", water = 105, juice = 175, price = 8, remainWeight = 1000, juiceChannel = 1),
                Recipe(name = "柳橙百香", water = 180, juice = 100, price = 9, remainWeight = 1000, juiceChannel = 2),
                Recipe(name = "满杯桑葚", water = 130, juice = 150, price = 10, remainWeight = 1000, juiceChannel = 3)
            )
            
            val existingRecipes = recipeRepository.allRecipes.first()
            
            coreRecipes.forEach { coreRecipe ->
                val existing = existingRecipes.find { it.name == coreRecipe.name }
                if (existing == null) {
                    // 如果不存在，直接插入
                    recipeRepository.insertRecipe(coreRecipe)
                } else {
                    // 如果存在，更新参数但保持库存
                    val updatedRecipe = existing.copy(
                        water = coreRecipe.water,
                        juice = coreRecipe.juice,
                        price = coreRecipe.price,
                        juiceChannel = coreRecipe.juiceChannel
                    )
                    recipeRepository.updateRecipe(updatedRecipe)
                }
            }
            
            _uiState.update { it.copy(errorMessage = "默认配方已恢复") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        hardwareManager.disconnect()
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