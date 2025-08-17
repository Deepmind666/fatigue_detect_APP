package com.example.juicemachine.ui.viewmodel

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.juicemachine.R
import com.example.juicemachine.data.database.CupConfig
import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.data.hardware.HardwareManager
import com.example.juicemachine.data.hardware.WeightAnomalyData
import com.example.juicemachine.data.hardware.WeightAnomalySeverity
import com.example.juicemachine.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

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
        
        // 修复：设置状态监听器
        hardwareManager.setOnStatusListener { msg ->
            Log.d("DrinkMenuViewModel", "连接状态更新: $msg")
            _uiState.update { it.copy(connectionStatus = msg, errorMessage = if (msg.contains("未连接") || msg.contains("失败")) msg else null) }
        }
        
        // 重要：先设置异常监听器，再连接硬件
        android.util.Log.e("DrinkMenuViewModel", "=== 开始设置异常监听器 ===")
        setupSafeAnomalyListener()
        android.util.Log.e("DrinkMenuViewModel", "=== 异常监听器设置完成 ===")
        
        // 修复：立即连接，不延迟
        hardwareManager.connect { status ->
            Log.d("DrinkMenuViewModel", "硬件连接状态: $status")
            _uiState.update { it.copy(connectionStatus = status) }
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
        
        // 修复：只清除选中的配方，不影响异常弹窗状态
        _uiState.update { currentState ->
            currentState.copy(
                selectedRecipe = null,
                // 保持异常弹窗状态不变
                showWeightChangeDialog = currentState.showWeightChangeDialog,
                isInterrupted = currentState.isInterrupted,
                interruptedRecipe = currentState.interruptedRecipe
            )
        }
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
                val pickedUri = _uiState.value.selectedImageUri
                val persistedUri = pickedUri?.let { persistImageToPrivateStorage(it) }?.toString()
                val recipeWithImage = recipe.copy(imageUri = persistedUri)
                
                if (recipeWithImage.id == 0) {
                    recipeRepository.insertRecipe(recipeWithImage)
                    Log.d("DrinkMenuViewModel", "新配方已保存: ${recipeWithImage.name}, 图片: $persistedUri")
                } else {
                    recipeRepository.updateRecipe(recipeWithImage)
                    Log.d("DrinkMenuViewModel", "配方已更新: ${recipeWithImage.name}, 图片: $persistedUri")
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

    private suspend fun persistImageToPrivateStorage(uri: android.net.Uri): android.net.Uri? {
        return try {
            // 修复：使用更稳定的方式获取Context
            val context = getApplicationContext()
            val imagesDir = java.io.File(context.filesDir, "images").apply { if (!exists()) mkdirs() }
            val fileName = "recipe_${System.currentTimeMillis()}.jpg"
            val outFile = java.io.File(imagesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".provider",
                outFile
            )
        } catch (e: Exception) {
            Log.e("DrinkMenuViewModel", "图片持久化失败: ${e.message}", e)
            null
        }
    }

    private fun getApplicationContext(): android.content.Context {
        // 修复：使用更稳定的方式获取Application Context
        return try {
            // 简化：直接使用反射获取Application
            val clazz = Class.forName("android.app.ActivityThread")
            val method = clazz.getMethod("currentApplication")
            val app = method.invoke(null) as android.app.Application
            app.applicationContext
        } catch (e: Exception) {
            Log.e("DrinkMenuViewModel", "获取Application Context失败: ${e.message}", e)
            // 兜底方案，抛出异常让调用方处理
            throw IllegalStateException("无法获取Application Context: ${e.message}")
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

    // 新增：手动测试中断处理功能 - 直接显示有继续制作和重新制作按钮的弹窗
    fun testPopup() {
        android.util.Log.e("DrinkMenuViewModel", "=== 手动测试中断处理 ===")
        
        // 直接显示完整的弹窗，不显示测试成功界面
        _uiState.update { current ->
            current.copy(
                showWeightChangeDialog = true,
                isInterrupted = true,
                interruptedRecipe = current.recipes.firstOrNull(), // 设置一个默认配方
                interruptedCupSize = "中杯",
                interruptedWithIce = true,
                errorMessage = null // 不再使用通用错误弹窗，避免与重量异常弹窗竞争
            )
        }
        
        android.util.Log.e("DrinkMenuViewModel", "=== 中断处理：直接显示继续/重新制作弹窗 ===")
    }
    
        // 新增：显示数据缓冲区 - 恢复正常功能
    fun showDataBuffer() {
        try {
            val bufferContent = hardwareManager.getDataBuffer()
            android.util.Log.e("DrinkMenuViewModel", "=== 显示数据缓冲区 ===")
            android.util.Log.e("DrinkMenuViewModel", "=== 缓冲区内容: '$bufferContent' ===")

            val contentToShow = if (bufferContent.isBlank()) {
                android.util.Log.e("DrinkMenuViewModel", "=== 缓冲区为空或仅空白，添加测试数据 ===")
                hardwareManager.addTestDataToBuffer()
                // 重新获取缓冲区内容
                val newBufferContent = hardwareManager.getDataBuffer()
                android.util.Log.e("DrinkMenuViewModel", "=== 添加测试数据后缓冲区内容: '$newBufferContent' ===")
                newBufferContent
            } else {
                bufferContent
            }

            _uiState.update { current ->
                current.copy(
                    errorMessage = "数据缓冲区内容:\n$contentToShow"
                )
            }
            
            // 增强匹配：对缓冲区做HEX规范化（忽略大小写与分隔符），支持连续触发
            val normalizedHex = contentToShow
                .uppercase(java.util.Locale.ROOT)
                .filter { it in "0123456789ABCDEF" }
            android.util.Log.e("DrinkMenuViewModel", "=== 规范化后缓冲区内容(仅HEX): '$normalizedHex' ===")
            
            if (normalizedHex.contains("FF09")) {
                android.util.Log.e("DrinkMenuViewModel", "=== 检测到FF09(忽略分隔与大小写)，自动触发中断处理弹窗 ===")
                hardwareManager.forceTriggerDialog()
            }
        } catch (e: Exception) {
            android.util.Log.e("DrinkMenuViewModel", "=== 显示数据缓冲区失败: ${e.message} ===", e)
            _uiState.update { current ->
                current.copy(
                    errorMessage = "读取数据缓冲区失败: ${e.message}"
                )
            }
        }
    }
    
    // 新增：清空数据缓冲区
    fun clearDataBuffer() {
        hardwareManager.clearDataBuffer()
        android.util.Log.e("DrinkMenuViewModel", "=== 清空数据缓冲区 ===")
        _uiState.update { current ->
            current.copy(
                errorMessage = "数据缓冲区已清空"
            )
        }
    }
    
    // 新增：检查连接状态
    fun checkConnectionStatus() {
        val status = hardwareManager.isConnected
        android.util.Log.e("DrinkMenuViewModel", "=== 连接状态: $status ===")
        // 删除Toast：连接状态提示，避免重复显示
        _uiState.update { current ->
            current.copy(
                errorMessage = "连接状态: $status"
            )
        }
    }
    
    // 新增：测试数据接收
    fun testDataReceive() {
        android.util.Log.e("DrinkMenuViewModel", "=== 测试数据接收 ===")
        hardwareManager.testDataReceive()
    }
    
    // 新增：测试异常检测逻辑
    fun testAnomalyDetection() {
        android.util.Log.e("DrinkMenuViewModel", "=== 测试异常检测逻辑 ===")
        hardwareManager.testAnomalyDetection()
    }
    
    // 新增：中断处理 - 直接显示完整的弹窗（有继续制作和重新制作按钮）
    fun testListenerCall() {
        android.util.Log.e("DrinkMenuViewModel", "=== 中断处理 ===")
        
        // 直接显示完整的弹窗，不显示测试成功消息
        _uiState.update { current ->
            android.util.Log.e("DrinkMenuViewModel", "=== 更新UI状态前: showWeightChangeDialog=${current.showWeightChangeDialog} ===")
            val newState = current.copy(
                showWeightChangeDialog = true,
                isInterrupted = true,
                interruptedRecipe = current.recipes.firstOrNull(), // 设置一个默认配方
                interruptedCupSize = "中杯",
                interruptedWithIce = true,
                errorMessage = null // 不再使用通用错误弹窗，避免与重量异常弹窗竞争
            )
            android.util.Log.e("DrinkMenuViewModel", "=== 更新UI状态后: showWeightChangeDialog=${newState.showWeightChangeDialog} ===")
            newState
        }
        
        android.util.Log.e("DrinkMenuViewModel", "=== 中断处理弹窗设置完成 ===")
        
        // 在主线程显示确认消息
        viewModelScope.launch(Dispatchers.Main) {
            // 删除Toast：中断处理弹窗已启动
        }
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
            // 模拟模式下也要关闭弹窗
            _uiState.update { 
                it.copy(
                    showWeightChangeDialog = false,
                    isInterrupted = false,
                    interruptedRecipe = null,
                    errorMessage = null // 避免在关闭重量异常弹窗后再弹出通用提示
                ) 
            }
            return
        }
        
        Log.d("DrinkMenuViewModel", "发送继续制作指令")
        hardwareManager.sendContinueCommand()
        
        _uiState.update { 
            it.copy(
                showWeightChangeDialog = false,
                isInterrupted = false,
                interruptedRecipe = null,
                errorMessage = null // 避免在关闭重量异常弹窗后再弹出通用提示
            ) 
        }
    }

    // 新增：重新制作
    fun onRestartRecipe() {
        if (!hardwareManager.isConnected) {
            // 模拟模式下也要关闭弹窗
            _uiState.update { 
                it.copy(
                    showWeightChangeDialog = false,
                    isInterrupted = false,
                    interruptedRecipe = null,
                    errorMessage = null // 避免在关闭重量异常弹窗后再弹出通用提示
                ) 
            }
            return
        }
        
        val currentState = _uiState.value
        val recipe = currentState.interruptedRecipe
        val cupSize = currentState.interruptedCupSize
        val withIce = currentState.interruptedWithIce
        
        if (recipe != null) {
            Log.d("DrinkMenuViewModel", "重新制作: 饮品=${recipe.name}, 杯型=$cupSize, 冰度=${if(withIce) "正常冰" else "去冰"}")
        }
        
        hardwareManager.sendRestartCommand()
        
        _uiState.update { 
            it.copy(
                showWeightChangeDialog = false,
                isInterrupted = false,
                interruptedRecipe = null,
                errorMessage = null // 避免在关闭重量异常弹窗后再弹出通用提示
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
            // 删除所有现有配方
            val existingRecipes = recipeRepository.allRecipes.first()
            existingRecipes.forEach { recipe ->
                recipeRepository.deleteRecipe(recipe)
            }
            
            // 重新插入默认的三种核心饮料
            val defaultRecipes = listOf(
                Recipe(name = "茉莉雪芽", water = 105, juice = 175, price = 8, remainWeight = 1000, juiceChannel = 1, imageUri = null),
                Recipe(name = "柳橙百香", water = 180, juice = 100, price = 9, remainWeight = 1000, juiceChannel = 2, imageUri = null),
                Recipe(name = "满杯桑葚", water = 130, juice = 150, price = 10, remainWeight = 1000, juiceChannel = 3, imageUri = null)
            )
            
            defaultRecipes.forEach { recipe ->
                recipeRepository.insertRecipe(recipe)
            }
            
            _uiState.update { it.copy(errorMessage = "默认配方已恢复，新增配方已删除") }
        }
    }

    private fun setupSafeAnomalyListener() {
        android.util.Log.e("DrinkMenuViewModel", "=== setupSafeAnomalyListener被调用 ===")
        hardwareManager.setOnWeightAnomalyListener { anomalyData ->
            android.util.Log.e("DrinkMenuViewModel", "=== ViewModel中的异常监听器被调用 ===")
            
            // 在主线程中更新UI状态
            viewModelScope.launch(Dispatchers.Main) {
                try {
                    android.util.Log.e("DrinkMenuViewModel", "=== 开始更新UI状态 ===")
                    _uiState.update { current ->
                        android.util.Log.e("DrinkMenuViewModel", "=== 当前状态: showWeightChangeDialog=${current.showWeightChangeDialog} ===")
                        val newState = current.copy(
                            showWeightChangeDialog = true,
                            isInterrupted = true,
                            interruptedRecipe = current.recipes.firstOrNull(), // 设置一个默认配方
                            interruptedCupSize = "中杯",
                            interruptedWithIce = true,
                            errorMessage = "检测到重量异常: 当前${anomalyData.currentWeight}g 预期${anomalyData.expectedWeight}g"
                        )
                        android.util.Log.e("DrinkMenuViewModel", "=== 更新后状态: showWeightChangeDialog=${newState.showWeightChangeDialog} ===")
                        newState
                    }
                    android.util.Log.e("DrinkMenuViewModel", "=== UI状态更新完成 ===")
                    
                    // 显示确认消息
                    // 删除Toast：异常监听器触发弹窗
                } catch (e: Exception) { 
                    android.util.Log.e("DrinkMenuViewModel", "=== UI状态更新失败: ${e.message} ===")
                    // 删除Toast：UI更新失败，保留日志
                }
            }
        }
        android.util.Log.e("DrinkMenuViewModel", "=== setupSafeAnomalyListener设置完成 ===")
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