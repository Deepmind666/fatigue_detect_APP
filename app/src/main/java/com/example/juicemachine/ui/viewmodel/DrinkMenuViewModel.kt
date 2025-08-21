package com.example.juicemachine.ui.viewmodel

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.juicemachine.R
import com.example.juicemachine.data.database.CupConfig
import com.example.juicemachine.data.database.Recipe
import com.example.juicemachine.data.database.Order
import com.example.juicemachine.data.hardware.HardwareManager
import com.example.juicemachine.data.hardware.WeightAnomalyData
import com.example.juicemachine.data.hardware.WeightAnomalySeverity
import com.example.juicemachine.data.repository.RecipeRepository
import com.example.juicemachine.data.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import com.example.juicemachine.util.DebugLogger

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
    private val orderRepository: OrderRepository,
    private val hardwareManager: HardwareManager
) : ViewModel() {
    
    // 标记：是否将当前这次完成回调排除在统计之外（用于“重新制作不算”）
    private var excludeCurrentOrderFromStats: Boolean = false
    
    // 添加一个队列，保存待更新的订单ID
    private val pendingOrderIds: ArrayDeque<Long> = ArrayDeque()
    private val _uiState = MutableStateFlow(DrinkMenuUiState())
    val uiState: StateFlow<DrinkMenuUiState> = _uiState.asStateFlow()

    // 新增：待更新订单队列（按下单顺序）
    // private val pendingOrderIds: ArrayDeque<Long> = ArrayDeque()

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
        
        // 修复：设置状态监听器（不再把“未连接/失败”直接塞到错误弹窗，避免频繁挡屏）
        hardwareManager.setOnStatusListener { msg ->
            Log.d("DrinkMenuViewModel", "连接状态更新: $msg")
            _uiState.update { it.copy(connectionStatus = msg, errorMessage = it.errorMessage) }
        }
        
        // 重要：先设置异常监听器，再连接硬件
        setupSafeAnomalyListener()

        // 新增：设置制作完成/失败回调监听
        setupOrderCompletionListener()
        
        // 启动即尝试连接，等待权限回调（不弹出错误）
        tryConnect()
    }

    private fun tryConnect() {
        DebugLogger.i("DrinkMenuViewModel", "开始发起连接", showToast = true)
        hardwareManager.connect { status ->
            Log.d("DrinkMenuViewModel", "硬件连接状态: $status")
            DebugLogger.i("DrinkMenuViewModel", "硬件连接状态: $status", showToast = true)
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
        // 新下单按正常统计
        excludeCurrentOrderFromStats = false
        if (!hardwareManager.isConnected) {
            tryConnect()
            _uiState.update { it.copy(errorMessage = "正在连接设备，请稍候再试") }
            DebugLogger.w("DrinkMenuViewModel", "未连接直接下单，已触发重连", showToast = true)
            return
        }
        Log.d("DrinkMenuViewModel", "确认下单: 饮品=${recipe.name}, 杯型=$cupSize, 冰度=${if(withIce) "正常冰" else "去冰"}")
        DebugLogger.i("DrinkMenuViewModel", "确认下单: ${recipe.name}/$cupSize/${if(withIce) "冰" else "去冰"}", showToast = true)

        // 修复：将发送和数据库操作放到IO线程，避免主线程阻塞
        viewModelScope.launch(Dispatchers.IO) {
            var sentOk = false
            try {
                sentOk = hardwareManager.makeJuice(recipe, cupSize, withIce)
                DebugLogger.i("DrinkMenuViewModel", "makeJuice 已调用, result=$sentOk")
            } catch (e: Exception) {
                Log.e("DrinkMenuViewModel", "调用makeJuice发生异常: ${e.message}", e)
                DebugLogger.e("DrinkMenuViewModel", "调用makeJuice异常: ${e.message}", e, showToast = true)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "发送指令失败：${e.message ?: "未知错误"}") }
                }
                return@launch
            }
            if (!sentOk) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "发送下单指令失败，请检查连接") }
                }
                return@launch
            }

            // 入库订单与库存扣减（IO线程），UI更新切回主线程
            val qty = 1
            val unitPrice = recipe.price
            val totalAmount = qty * unitPrice
            val actualJuice = when (cupSize) { "大杯" -> (recipe.juice * 1.3).toInt(); else -> recipe.juice }
            val actualWater = when (cupSize) { "大杯" -> (recipe.water * 1.3).toInt(); else -> recipe.water }
            val order = Order(
                recipeId = recipe.id,
                recipeName = recipe.name,
                quantity = qty,
                unitPrice = unitPrice,
                totalAmount = totalAmount,
                cupSize = cupSize,
                withIce = withIce,
                status = "PENDING",
                actualJuiceConsumption = actualJuice,
                actualWaterConsumption = actualWater,
                notes = null
            )
            try {
                val newId = orderRepository.insertOrder(order)
                withContext(Dispatchers.Main) {
                    pendingOrderIds.addLast(newId)
                }
            } catch (e: Exception) {
                Log.e("DrinkMenuViewModel", "写入订单失败: ${e.message}", e)
            }
            val actualJuiceConsumption = when (cupSize) {
                "大杯" -> (recipe.juice * 1.3).toInt()
                else -> recipe.juice
            }
            val newRemain = (recipe.remainWeight - actualJuiceConsumption).coerceAtLeast(0)
            try {
                recipeRepository.updateRemainWeight(recipe.id, newRemain)
            } catch (e: Exception) {
                Log.e("DrinkMenuViewModel", "更新库存失败: ${e.message}", e)
            }
            withContext(Dispatchers.Main) {
                _uiState.update { currentState ->
                    currentState.copy(
                        selectedRecipe = null,
                        showWeightChangeDialog = currentState.showWeightChangeDialog,
                        isInterrupted = currentState.isInterrupted,
                        interruptedRecipe = currentState.interruptedRecipe
                    )
                }
            }
        }
    }

    fun onClean() {
        // 后台发送管理指令，避免阻塞主线程
        viewModelScope.launch(Dispatchers.IO) {
            val ok = hardwareManager.sendAdminCommand(0x03)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(errorMessage = if (ok) "已发送清洗指令(0x03)" else "发送清洗指令失败") }
            }
        }
    }

    fun onStopAction() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = hardwareManager.sendAdminCommand(0x04)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(errorMessage = if (ok) "已发送停止指令(0x04)" else "发送停止指令失败") }
            }
        }
    }

    fun onTare() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = hardwareManager.sendAdminCommand(0x05)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(errorMessage = if (ok) "已发送去皮指令(0x05)" else "发送去皮指令失败") }
            }
        }
    }

    fun onWeigh() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = hardwareManager.sendAdminCommand(0x06)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(errorMessage = if (ok) "已发送称重指令(0x06)" else "发送称重指令失败") }
            }
        }
    }

    // 新增：手动重连设备入口（后台管理页）
    fun onReconnect() {
        hardwareManager.connect { status ->
            Log.d("DrinkMenuViewModel", "手动重连：$status")
            _uiState.update { it.copy(connectionStatus = status, errorMessage = if (status.contains("未连接") || status.contains("失败")) status else null) }
        }
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

    // 删除：手动测试与调试相关的方法（testPopup/showDataBuffer/clearDataBuffer/checkConnectionStatus/testDataReceive/testAnomalyDetection/testListenerCall）
    // 这些方法会干扰真实串口触发路径，现统一移除。

    // 新增：模拟重量变化检测（保留供无设备环境演示，可按需移除）
    fun onSimulateWeightChange() {
        _uiState.update { 
            it.copy(
                showWeightChangeDialog = true,
                isInterrupted = true,
                interruptedRecipe = it.selectedRecipe,
                interruptedCupSize = "中杯",
                interruptedWithIce = true
            ) 
        }
    }

    // 新增：继续制作
    fun onContinueRecipe() {
        // 继续制作：不插入新订单，保持正常统计
        excludeCurrentOrderFromStats = false
        if (!hardwareManager.isConnected) {
            _uiState.update { 
                it.copy(
                    // 保持弹窗与中断状态，提示用户检查连接
                    showWeightChangeDialog = true,
                    isInterrupted = true,
                    errorMessage = "设备未连接，无法继续，请检查连接"
                ) 
            }
            return
        }
        Log.d("DrinkMenuViewModel", "发送继续制作指令")
        val ok = hardwareManager.sendContinueCommand()
        if (ok) {
            _uiState.update { 
                it.copy(
                    showWeightChangeDialog = false,
                    isInterrupted = false,
                    interruptedRecipe = null,
                    errorMessage = null
                ) 
            }
        } else {
            _uiState.update { 
                it.copy(
                    // 保持弹窗，以便用户可再次选择
                    showWeightChangeDialog = true,
                    isInterrupted = true,
                    errorMessage = "发送继续指令失败"
                ) 
            }
        }
    }

    // 新增：重新制作
    fun onRestartRecipe() {
        // 重新制作：不插入新订单，并将当前回调从统计中排除
        excludeCurrentOrderFromStats = true
        if (!hardwareManager.isConnected) {
            _uiState.update { 
                it.copy(
                    // 保持弹窗与中断状态，提示用户检查连接
                    showWeightChangeDialog = true,
                    isInterrupted = true,
                    errorMessage = "设备未连接，无法重新制作，请检查连接"
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
        val ok = hardwareManager.sendRestartCommand()
        if (ok) {
            _uiState.update { 
                it.copy(
                    showWeightChangeDialog = false,
                    isInterrupted = false,
                    interruptedRecipe = null,
                    errorMessage = null
                ) 
            }
        } else {
            _uiState.update { 
                it.copy(
                    showWeightChangeDialog = true,
                    isInterrupted = true,
                    errorMessage = "发送重新制作指令失败"
                ) 
            }
        }
    }

    // 新增：制作完成/失败回调监听，更新订单状态
    private fun setupOrderCompletionListener() {
        hardwareManager.setOnOrderCompletionListener { success ->
            viewModelScope.launch {
                try {
                    // 出队一个待更新的订单ID（在主线程执行队列操作）
                    val orderId: Long? = withContext(Dispatchers.Main) {
                        if (pendingOrderIds.isEmpty()) null else pendingOrderIds.removeFirst()
                    }
                    if (orderId == null) {
                        Log.w("DrinkMenuViewModel", "没有待更新的订单ID，忽略完成回调: success=$success")
                    } else {
                        val order = orderRepository.getOrderById(orderId)
                        if (order != null) {
                            val updatedStatus = if (excludeCurrentOrderFromStats) {
                                // 重新制作：将本次订单标记为取消，从而不计入任何统计
                                "CANCELLED"
                            } else {
                                if (success) "COMPLETED" else "FAILED"
                            }
                            val updated = order.copy(status = updatedStatus)
                            orderRepository.updateOrder(updated)
                            Log.i("DrinkMenuViewModel", "订单状态已更新: id=$orderId, status=${updated.status}, exclude=$excludeCurrentOrderFromStats")
                            
                            // >>> 新增：通知统计页面刷新 <<<
                            StatisticsRefreshNotifier.notifyOrderUpdated()
                        } else {
                            Log.w("DrinkMenuViewModel", "未找到订单(id=$orderId)，无法更新状态")
                        }
                    }
                    // 重置排除标志，避免影响后续订单
                    excludeCurrentOrderFromStats = false
                    // 反馈到UI
                    _uiState.update { it.copy(errorMessage = if (success) "制作完成" else "制作失败") }
                } catch (e: Exception) {
                    Log.e("DrinkMenuViewModel", "更新订单状态失败: ${e.message}", e)
                    _uiState.update { it.copy(errorMessage = "更新订单状态失败: ${e.message}") }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        hardwareManager.disconnect()
    }

    // 供图片选择回调使用
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

    // 清除已选择的图片
    fun clearSelectedImage() {
        _uiState.update { it.copy(selectedImageUri = null) }
    }

    // 恢复默认配方
    fun restoreDefaultRecipes() {
        viewModelScope.launch {
            try {
                val existingRecipes = recipeRepository.allRecipes.first()
                existingRecipes.forEach { recipe ->
                    recipeRepository.deleteRecipe(recipe)
                }
                val defaultRecipes = listOf(
                    Recipe(name = "茉莉雪芽", water = 105, juice = 175, price = 8, remainWeight = 1000, juiceChannel = 1, imageUri = null),
                    Recipe(name = "柳橙百香", water = 180, juice = 100, price = 9, remainWeight = 1000, juiceChannel = 2, imageUri = null),
                    Recipe(name = "满杯桑葚", water = 130, juice = 150, price = 10, remainWeight = 1000, juiceChannel = 3, imageUri = null)
                )
                defaultRecipes.forEach { recipe -> recipeRepository.insertRecipe(recipe) }
                _uiState.update { it.copy(errorMessage = "默认配方已恢复") }
            } catch (e: Exception) {
                Log.e("DrinkMenuViewModel", "恢复默认配方失败: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = "恢复默认配方失败: ${e.message}") }
            }
        }
    }

    // 安全设置异常监听器
    private fun setupSafeAnomalyListener() {
        hardwareManager.setOnWeightAnomalyListener { anomalyData: WeightAnomalyData ->
            viewModelScope.launch(Dispatchers.Main) {
                try {
                    _uiState.update { current ->
                        current.copy(
                            showWeightChangeDialog = true,
                            isInterrupted = true,
                            interruptedRecipe = current.selectedRecipe ?: current.recipes.firstOrNull(),
                            interruptedCupSize = "中杯",
                            interruptedWithIce = true,
                            errorMessage = "检测到重量异常: 当前${anomalyData.currentWeight}g 预期${anomalyData.expectedWeight}g"
                        )
                    }
                } catch (e: Exception) {
                    Log.e("DrinkMenuViewModel", "异常处理更新UI失败: ${e.message}")
                }
            }
        }
    }
}

class DrinkMenuViewModelFactory(
    private val repository: RecipeRepository,
    private val hardwareManager: HardwareManager,
    private val orderRepository: OrderRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DrinkMenuViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DrinkMenuViewModel(repository, orderRepository, hardwareManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}