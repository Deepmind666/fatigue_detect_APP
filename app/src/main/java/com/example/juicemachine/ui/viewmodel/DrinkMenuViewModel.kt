package com.example.juicemachine.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.juicemachine.R
import com.example.juicemachine.data.database.CupConfig
import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.data.hardware.HardwareManager
import com.example.juicemachine.data.hardware.WeightAnomalyData
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
    val errorMessage: String? = null, // 新增错误提示
    val showWeightChangeDialog: Boolean = false, // 新增：重量变化弹窗
    val isInterrupted: Boolean = false, // 新增：是否中断状态
    val interruptedRecipe: Recipe? = null, // 新增：中断时的配方
    val interruptedCupSize: String = "", // 新增：中断时的杯型
    val interruptedWithIce: Boolean = false, // 新增：中断时的冰度
    val selectedImageUri: android.net.Uri? = null // 新增：选中的图片URI
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
        hardwareManager.setOnWeightAnomalyListener { anomalyData ->
            _uiState.update { 
                it.copy(
                    showWeightChangeDialog = true,
                    isInterrupted = true,
                    errorMessage = "检测到重量异常: 当前${anomalyData.currentWeight}g, 预期${anomalyData.expectedWeight}g"
                ) 
            }
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
        _uiState.update { it.copy(errorMessage = "发送清洗指令(0x03)") }
        hardwareManager.sendAdminCommand(0x03)
    }

    fun onStopAction() {
        _uiState.update { it.copy(errorMessage = "发送停止指令(0x04)") }
        hardwareManager.sendAdminCommand(0x04)
    }

    fun onTare() {
        _uiState.update { it.copy(errorMessage = "发送去皮指令(0x05)") }
        hardwareManager.sendAdminCommand(0x05)
    }

    fun onWeigh() {
        _uiState.update { it.copy(errorMessage = "发送称重指令(0x06)") }
        hardwareManager.sendAdminCommand(0x06)
    }

    fun onHeaderLongClick() {
        _uiState.update { it.copy(showLoginDialog = true) }
    }

    fun onLoginAttempt(password: String) {
        if (password == "6") {
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
            try {
                // 获取当前选中的图片URI
                val currentImageUri = _uiState.value.selectedImageUri?.toString()
                val recipeWithImage = recipe.copy(imageUri = currentImageUri)
                
                if (recipeWithImage.id == 0) {
                    recipeRepository.insertRecipe(recipeWithImage)
                    Log.d("DrinkMenuViewModel", "新配方已保存: ${recipeWithImage.name}, 图片: $currentImageUri")
                } else {
                    recipeRepository.updateRecipe(recipeWithImage)
                    Log.d("DrinkMenuViewModel", "配方已更新: ${recipeWithImage.name}, 图片: $currentImageUri")
                }
                
                // 清除选中的图片
                clearSelectedImage()
                
                _uiState.update { it.copy(errorMessage = "配方保存成功") }
            } catch (e: Exception) {
                Log.e("DrinkMenuViewModel", "保存配方失败: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = "保存失败: ${e.message}") }
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

    // 新增：模拟重量变化检测
    fun onSimulateWeightChange() {
        _uiState.update { 
            it.copy(
                showWeightChangeDialog = true,
                isInterrupted = true,
                interruptedRecipe = it.selectedRecipe,
                interruptedCupSize = "中杯", // 默认值
                interruptedWithIce = true    // 默认值
            ) 
        }
    }

    // 新增：继续制作
    fun onContinueRecipe() {
        if (!hardwareManager.isConnected) {
            _uiState.update { 
                it.copy(
                    showWeightChangeDialog = false,
                    isInterrupted = false,
                    interruptedRecipe = null,
                    errorMessage = "串口未连接，无法发送继续制作指令"
                ) 
            }
            return
        }
        
        // 发送继续制作指令 (0x0A)
        hardwareManager.sendContinueCommand()
        
        _uiState.update { 
            it.copy(
                showWeightChangeDialog = false,
                isInterrupted = false,
                interruptedRecipe = null,
                errorMessage = "已发送继续制作指令"
            ) 
        }
    }

    // 新增：重新制作
    fun onRestartRecipe() {
        val currentState = _uiState.value
        val recipe = currentState.interruptedRecipe
        val cupSize = currentState.interruptedCupSize
        val withIce = currentState.interruptedWithIce
        
        if (!hardwareManager.isConnected) {
            _uiState.update { 
                it.copy(
                    showWeightChangeDialog = false,
                    isInterrupted = false,
                    interruptedRecipe = null,
                    errorMessage = "串口未连接，无法重新制作"
                ) 
            }
            return
        }
        
        if (recipe == null) {
            _uiState.update { 
                it.copy(
                    showWeightChangeDialog = false,
                    isInterrupted = false,
                    errorMessage = "没有找到中断的配方信息"
                ) 
            }
            return
        }
        
        // 重新发送上一次的制作指令
        Log.d("DrinkMenuViewModel", "重新制作: 饮品=${recipe.name}, 杯型=$cupSize, 冰度=${if(withIce) "正常冰" else "去冰"}")
        hardwareManager.makeJuice(recipe, cupSize, withIce)
        
        _uiState.update { 
            it.copy(
                showWeightChangeDialog = false,
                isInterrupted = false,
                interruptedRecipe = null,
                errorMessage = "已重新开始制作${recipe.name}，请倒掉之前的饮品并重新放置杯子！"
            ) 
        }
    }

    // 新增：图片选择处理
    fun onImageSelected(uri: android.net.Uri?) {
        try {
            _uiState.update { it.copy(selectedImageUri = uri) }
            Log.d("DrinkMenuViewModel", "图片已选择: $uri")
        } catch (e: Exception) {
            Log.e("DrinkMenuViewModel", "图片选择处理失败: ${e.message}", e)
            _uiState.update { 
                it.copy(
                    selectedImageUri = null,
                    errorMessage = "图片处理失败，请重试"
                )
            }
        }
    }

    // 新增：清除选中的图片
    fun clearSelectedImage() {
        _uiState.update { it.copy(selectedImageUri = null) }
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