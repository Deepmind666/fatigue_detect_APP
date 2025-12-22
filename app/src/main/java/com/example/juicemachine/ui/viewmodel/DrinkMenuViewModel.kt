package com.example.juicemachine.ui.viewmodel

import android.util.Log
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
import com.example.juicemachine.ui.model.IceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import java.util.ArrayDeque
import com.example.juicemachine.util.DebugLogger
import kotlinx.coroutines.delay
// 移除小数格式化：重量显示统一为整数

data class DrinkMenuUiState(
    val recipes: List<Recipe> = emptyList(),
    val connectionStatus: String = "未连接",
    val selectedRecipe: Recipe? = null,
    val recipeToEdit: Recipe = Recipe(id = 0, name = "", water = 0, juice = 0, price = 0, defaultRemainingWeight = 0, currentRemainingWeight = 0, juiceChannel = 1, waterSpeed = 60, juiceSpeed = 60),
    val showLoginDialog: Boolean = false,
    val loginError: Boolean = false,
    val navigateToAdmin: Boolean = false,
    val navigateToEdit: Boolean = false,
    val errorMessage: String? = null,
    val showWeightChangeDialog: Boolean = false,
    val isInterrupted: Boolean = false,
    val interruptedRecipe: Recipe? = null,
    val interruptedCupSize: String = "",
    val interruptedWithIce: Boolean = false,
    val selectedImageUri: android.net.Uri? = null,
    // 新增：客户管理导航标志
    val navigateToCustomer: Boolean = false,
    // 新增：只出水状态，用于切换按钮高亮
    val isWaterOnlyActive: Boolean = false,
    // 新增：只出水水速（0-255），用于构建加水开始指令中的速度参数
    val waterOnlySpeed: Int = 60,
    // 新增：称重测试等待弹窗与结果提示
    val showWeighWaitingDialog: Boolean = false,
    val weighResultMessage: String? = null,
    // 新增：温度与重量上报（用于用户页展示）
    val currentTemperature: Int? = null,
    val currentWeight: Int? = null,
    val adsResetTick: Int = 0,
    val adsCountdownPaused: Boolean = false,
    val adsCountdownResumeDeadline: Long? = null,
    val isMaking: Boolean = false
)

class DrinkMenuViewModel(
    private val recipeRepository: RecipeRepository,
    private val orderRepository: OrderRepository,
    private val hardwareManager: HardwareManager,
    private val sharedPreferences: android.content.SharedPreferences,
    private val appContext: android.content.Context
) : ViewModel() {
    
    // 标记：是否将当前这次完成回调排除在统计之外（用于“重新制作不算”）
    private var excludeCurrentOrderFromStats: Boolean = false
    
    // 添加一个队列，保存待更新的订单ID
    private val pendingOrderIds: ArrayDeque<Long> = ArrayDeque()
    private val _uiState = MutableStateFlow(DrinkMenuUiState())
    val uiState: StateFlow<DrinkMenuUiState> = _uiState.asStateFlow()

    private var recipesCollectionJob: Job? = null
    private var adsPauseJob: Job? = null

    init {
        // 延后配方收集：不在冷启动阶段立即打开数据库与流
        // 硬件连接改为在首帧绘制后再触发，避免冷启动阶段做USB枚举与广播注册
        // 在 AppNavigation 的 LaunchedEffect 中调用 initializeHardwareAfterFirstFrame()
        // 设置监听器
        setupOrderCompletionListener()
        setupSafeAnomalyListener()
        setupTelemetryListeners()
        // Load persisted waterOnlySpeed；若为0或负数，强制回落到60并回写
        val persistedSpeed = sharedPreferences.getInt("water_only_speed", 60)
        val normalizedSpeed = if (persistedSpeed <= 0) 60 else persistedSpeed.coerceIn(1, 255)
        if (persistedSpeed <= 0) {
            try {
                sharedPreferences.edit().putInt("water_only_speed", normalizedSpeed).apply()
            } catch (e: Exception) {
                Log.w("DrinkMenuViewModel", "回写默认水速失败: ${e.message}", e)
            }
        }
        _uiState.update { it.copy(waterOnlySpeed = normalizedSpeed) }
    }

    // 页面可见时启动配方收集；重复调用将被忽略
    fun onDrinkMenuVisible() {
        if (recipesCollectionJob != null) return
        recipesCollectionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                recipeRepository.allRecipes.collect { list ->
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(recipes = list) }
                    }
                }
            } catch (e: Exception) {
                Log.e("DrinkMenuViewModel", "加载配方失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "加载配方失败: ${e.message}") }
                }
            }
        }
    }

    // 页面隐藏时停止配方收集，释放资源
    fun onDrinkMenuHidden() {
        recipesCollectionJob?.cancel()
        recipesCollectionJob = null
    }

    fun onRecipeClick(recipe: Recipe) {
        _uiState.update { it.copy(selectedRecipe = recipe) }
    }

    fun onDismissDialog() {
        _uiState.update { it.copy(selectedRecipe = null, showLoginDialog = false, loginError = false) }
    }

    fun onConfirmDialog(recipe: Recipe, cupSize: String, iceMode: IceMode) {
        // 新下单按正常统计
        excludeCurrentOrderFromStats = false
        if (!hardwareManager.isConnected) {
            tryConnect()
            _uiState.update { it.copy(errorMessage = "设备未连接，已尝试重连，请连接后再下单") }
            DebugLogger.w("DrinkMenuViewModel", "未连接：阻止下单，已触发重连", showToast = false)
            return
        }
        if (_uiState.value.isMaking || pendingOrderIds.isNotEmpty()) {
            _uiState.update { it.copy(errorMessage = "正在制作，请稍候") }
            return
        }
        val modeText = when (iceMode) { IceMode.NORMAL -> "正常冰"; IceMode.NO_ICE -> "去冰"; IceMode.HOT -> "热饮" }
        Log.d("DrinkMenuViewModel", "确认下单: 饮品=${recipe.name}, 杯型=$cupSize, 冰度=$modeText")
        DebugLogger.i("DrinkMenuViewModel", "确认下单: ${recipe.name}/$cupSize/$modeText", showToast = false)

        // 修复：将发送和数据库操作放到IO线程，避免主线程阻塞
        viewModelScope.launch(Dispatchers.IO) {
            // 连接已确认，否则上面已return
            // 入库订单与库存扣减（IO线程），UI更新切回主线程
            val qty = 1
            // 确保使用数据库中最新保存的配方，避免“保存后发送仍旧”的问题
            val currentRecipe = recipeRepository.getRecipeById(recipe.id.toLong()) ?: recipe
            val unitPrice = currentRecipe.price
            val totalAmount = qty * unitPrice
            // 动态果汁量：基于剩余重量 + 果肉补偿策略
            val stateForCalc = _uiState.value
            val actualJuice = computeDynamicJuiceAmount(currentRecipe, cupSize, stateForCalc)
            // 水量：热饮强制为0；其他保持原有杯型缩放
            val actualWater = when (iceMode) {
                IceMode.HOT -> 0
                else -> when (cupSize) { "大杯" -> (currentRecipe.water * 1.3).toInt(); else -> currentRecipe.water }
            }
            val order = Order(
                recipeId = currentRecipe.id,
                recipeName = currentRecipe.name,
                quantity = qty,
                unitPrice = unitPrice,
                totalAmount = totalAmount,
                cupSize = cupSize,
                withIce = (iceMode == IceMode.NORMAL),
                status = "PENDING",
                actualJuiceConsumption = actualJuice,
                actualWaterConsumption = actualWater,
                notes = null
            )
            try {
                // 先入库并加入待完成队列，确保硬件回调到来时有可更新的订单ID
                val newId = orderRepository.insertOrder(order)
                withContext(Dispatchers.Main) {
                    pendingOrderIds.addLast(newId)
                }

                var sentOkFlag = true
                try {
                    // 按后端新协议，直接使用计算后的实际用量（调用端已处理杯型缩放）
                    val latestBase = recipeRepository.getRecipeById(currentRecipe.id.toLong())
                        ?: _uiState.value.recipes.find { it.id == currentRecipe.id }
                        ?: currentRecipe
                    // 制作时水速采用全局设置（waterOnlySpeed），不使用配方内水速
                    val globalWaterSpeed = _uiState.value.waterOnlySpeed
                    val recipeToSend = latestBase.copy(
                        juice = actualJuice,
                        water = actualWater,
                        waterSpeed = globalWaterSpeed
                    )
                    DebugLogger.i(
                        "SendCheck",
                        "order=${newId} name=${recipeToSend.name} water=${recipeToSend.water}g juice=${recipeToSend.juice}g wSpd=${recipeToSend.waterSpeed} jSpd=${recipeToSend.juiceSpeed} ch=${recipeToSend.juiceChannel}",
                        showToast = false
                    )
                    sentOkFlag = when (iceMode) {
                        IceMode.HOT -> {
                            hardwareManager.makeHotDrink(recipeToSend, cupSize)
                        }
                        IceMode.NORMAL -> {
                            if (recipe.hasPulp) {
                                hardwareManager.makeJuiceWithCompensation(
                                    recipe = recipeToSend,
                                    cupSize = cupSize,
                                    withIce = true
                                )
                            } else {
                                hardwareManager.makeJuice(recipeToSend, cupSize, true)
                            }
                        }
                        IceMode.NO_ICE -> {
                            if (recipe.hasPulp) {
                                hardwareManager.makeJuiceWithCompensation(
                                    recipe = recipeToSend,
                                    cupSize = cupSize,
                                    withIce = false
                                )
                            } else {
                                hardwareManager.makeJuice(recipeToSend, cupSize, false)
                            }
                        }
                    }
                    DebugLogger.i("DrinkMenuViewModel", "makeJuice/makeHotDrink 已调用(新协议), result=$sentOkFlag")
                } catch (e: Exception) {
                    Log.e("DrinkMenuViewModel", "调用makeJuice发生异常: ${e.message}", e)
                    DebugLogger.e("DrinkMenuViewModel", "调用makeJuice异常: ${e.message}", e, showToast = false)
                    // 发送失败：将刚刚入库的订单标记为失败并通知统计刷新
                    val dbOrder = orderRepository.getOrderById(newId)
                    if (dbOrder != null) {
                        val updated = dbOrder.copy(status = "FAILED")
                        orderRepository.updateOrder(updated)
                        StatisticsRefreshNotifier.notifyOrderUpdated()
                    }
                    withContext(Dispatchers.Main) {
                        pendingOrderIds.remove(newId)
                        _uiState.update { it.copy(errorMessage = "发送指令失败：${e.message ?: "未知错误"}") }
                    }
                    return@launch
                }
                if (!sentOkFlag) {
                    // 发送返回失败：同样标记订单失败并通知统计刷新
                    val dbOrder = orderRepository.getOrderById(newId)
                    if (dbOrder != null) {
                        val updated = dbOrder.copy(status = "FAILED")
                        orderRepository.updateOrder(updated)
                        StatisticsRefreshNotifier.notifyOrderUpdated()
                    }
                    withContext(Dispatchers.Main) {
                        pendingOrderIds.remove(newId)
                        _uiState.update { it.copy(errorMessage = "发送下单指令失败，请检查连接") }
                    }
                    return@launch
                }
                if (sentOkFlag) {
                    withContext(Dispatchers.Main) { pauseAdsCountdownForPendingCompletion(30_000) }
                    withContext(Dispatchers.Main) { _uiState.update { it.copy(isMaking = true) } }
                }

                // 兜底：仅在确认已成功发送硬件指令后，长时间未收到硬件回调时，自动完成以保证统计可用
                if (sentOkFlag) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            delay(20000)
                            val dbOrder = orderRepository.getOrderById(newId)
                            if (dbOrder != null && dbOrder.status == "PENDING") {
                                val updated = dbOrder.copy(status = "COMPLETED")
                                orderRepository.updateOrder(updated)
                                Log.w("DrinkMenuViewModel", "超时未收到硬件回调，自动将订单($newId)标记为 COMPLETED 计入统计")
                                StatisticsRefreshNotifier.notifyOrderUpdated()
                                withContext(Dispatchers.Main) {
                                    pendingOrderIds.remove(newId)
                                    _uiState.update { it.copy(isMaking = false) }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DrinkMenuViewModel", "自动完成兜底失败: ${e.message}", e)
                        }
                    }
                }

                // 新增：下单后扣减配方果料库存并清理UI选择（按动态果汁量扣减）
                val actualJuiceConsumption = actualJuice
                val newRemain = (recipe.currentRemainingWeight - actualJuiceConsumption).coerceAtLeast(0)
                try {
                    recipeRepository.updateCurrentRemainingWeight(recipe.id, newRemain)
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
            } catch (e: Exception) {
                Log.e("DrinkMenuViewModel", "写入订单失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "下单入库失败: ${e.message}", isMaking = false) }
                }
            }
        }
    }

    // 统一的设备重连方法：在后台线程执行，避免冷启动阻塞主线程
    // 改为公开方法，供首帧后调用
    fun tryConnect() {
        viewModelScope.launch(Dispatchers.IO) {
            hardwareManager.connect { status ->
                // 回调可能在非主线程触发，使用主线程协程安全更新 UI
                viewModelScope.launch(Dispatchers.Main) {
                    Log.d("DrinkMenuViewModel", "连接状态: $status")
                    _uiState.update { it.copy(connectionStatus = status) }
                }
            }
        }
    }

    // 首帧后初始化硬件连接入口（由 UI 调用）
    fun initializeHardwareAfterFirstFrame() {
        tryConnect()
    }

    // 修改清洗命令方法名，与CustomerCleanScreen中调用一致
    fun onCleanCommand() {
        // 后台发送管理指令，避免阻塞主线程
        viewModelScope.launch(Dispatchers.IO) {
            val ok = hardwareManager.sendCleanStart()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(errorMessage = if (ok) "已发送清洗指令" else "发送清洗指令失败") }
            }
        }
    }

    // 修改停止命令方法名，与CustomerCleanScreen中调用一致
    fun onStopCommand() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ok = hardwareManager.sendCleanStop()
                // 移除成功提示：仅在失败时提示错误
                _uiState.update { it.copy(errorMessage = if (ok) null else "发送停止指令失败") }
            } catch (e: Exception) {
                Log.e("DrinkMenuViewModel", "发送停止指令异常", e)
                _uiState.update { it.copy(errorMessage = "发送停止指令失败") }
            }
        }
    }

    fun onTare() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = hardwareManager.sendHX711Tare()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(errorMessage = if (ok) "已发送去皮指令" else "发送去皮指令失败") }
            }
        }
    }

    fun onWeigh() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = hardwareManager.sendHX711Weigh()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(
                    errorMessage = if (ok) null else "发送称重指令失败",
                    showWeighWaitingDialog = ok,
                    weighResultMessage = null
                ) }
            }
        }
    }

    // 新增：称重等待取消（点击弹窗外部即可触发）
    fun onCancelWeighWaiting() {
        _uiState.update { it.copy(showWeighWaitingDialog = false) }
    }

    // 新增：1000g 标准校准（后台页面）
    fun onCalibrateStandard() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try { hardwareManager.sendHX711Calibrate() } catch (e: Exception) {
                android.util.Log.e("DrinkMenuViewModel", "标准校准发送异常: ${e.message}", e)
                false
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(errorMessage = if (ok) "已发送标准校准指令" else "发送标准校准指令失败") }
            }
        }
    }

    // 新增：只出水开/关切换
    fun onWaterOnlyToggle() {
        viewModelScope.launch(Dispatchers.IO) {
            val active = _uiState.value.isWaterOnlyActive
            if (!active) {
                // 启动只出水：文档对齐，开始帧不携带速度；先预设速度再发送开始
                val speedRaw = _uiState.value.waterOnlySpeed
                val speed = if (speedRaw <= 0) 60 else speedRaw.coerceIn(1, 255)
                // 优先：一体化下发（预设+开始），失败则回退到两步法
                var ok = try { hardwareManager.sendWaterOnlyStart(speed) } catch (e: Exception) {
                    Log.e("DrinkMenuViewModel", "只出水启动异常(携速): ${e.message}", e)
                    false
                }
                if (!ok) {
                    try { hardwareManager.primeWaterTopSpeed(speed) } catch (e: Exception) {
                        Log.w("DrinkMenuViewModel", "预设水速失败（忽略回退）：${e.message}")
                    }
                    ok = hardwareManager.sendWaterOnlyStart()
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isWaterOnlyActive = ok, errorMessage = if (!ok) "只出水启动失败" else null) }
                }
            } else {
                // 停止：发送 11 字节（FF + 9 数据位 + FE）
                val ok = hardwareManager.sendWaterOnlyStop()
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isWaterOnlyActive = false, errorMessage = if (!ok) "停止出水失败" else null) }
                }
            }
        }
    }

    // 新增：急停
    fun onEmergencyStop() {
        viewModelScope.launch(Dispatchers.IO) {
            // 使用独立急停指令 CMD=0x09，立即刹停所有设备
            val ok = hardwareManager.sendEmergencyStop()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isWaterOnlyActive = false, isMaking = false, errorMessage = if (ok) "已急停" else "急停失败") }
            }
        }
    }

    // 新增：手动重连设备入口（后台管理页）
    fun onReconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            hardwareManager.connect { status ->
                Log.d("DrinkMenuViewModel", "手动重连：$status")
                _uiState.update { it.copy(connectionStatus = status, errorMessage = if (status.contains("未连接") || status.contains("失败")) status else null) }
            }
        }
    }

    fun onHeaderLongClick() {
        _uiState.update { it.copy(showLoginDialog = true) }
    }

    fun onLoginAttempt(password: String) {
        when (password) {
            "6" -> _uiState.update { it.copy(showLoginDialog = false, navigateToAdmin = true, loginError = false) }
            "0" -> _uiState.update { it.copy(showLoginDialog = false, navigateToCustomer = true, loginError = false) }
            else -> _uiState.update { it.copy(loginError = true) }
        }
    }

    // 新增：后台页导航完成后复位标志，避免重复导航
    fun onAdminNavigated() {
        _uiState.update { it.copy(navigateToAdmin = false) }
    }

    // 新增：客户管理页导航完成后复位标志
    fun onCustomerNavigated() {
        _uiState.update { it.copy(navigateToCustomer = false) }
    }

    fun onNavigateToEdit(recipe: Recipe?) {
        val recipeToEdit = recipe ?: Recipe(id = 0, name = "", water = 0, juice = 0, price = 0, defaultRemainingWeight = 0, currentRemainingWeight = 0, juiceChannel = 1, waterSpeed = 60, juiceSpeed = 60)
        _uiState.update { it.copy(recipeToEdit = recipeToEdit, navigateToEdit = true) }
    }

    fun onDefaultRemainWeightChange(remainWeight: String) {
        _uiState.update { currentState ->
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(defaultRemainingWeight = remainWeight.toIntOrNull() ?: 0)
            )
        }
    }

    // 新增：当前剩余重量（配方编辑页）手动设定
    fun onCurrentRemainWeightChange(remainWeight: String) {
        _uiState.update { currentState ->
            val value = remainWeight.toIntOrNull() ?: 0
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(currentRemainingWeight = value.coerceAtLeast(0))
            )
        }
    }

    // 鲜料重置：将当前剩余重量重置为配方的默认库存值
    fun onResetStock(recipe: Recipe) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recipeRepository.updateCurrentRemainingWeight(recipe.id, recipe.defaultRemainingWeight)
            } catch (e: Exception) {
                Log.e("DrinkMenuViewModel", "鲜料重置失败: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = "鲜料重置失败，请稍后重试") }
            }
        }
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

    fun onHasPulpChange(hasPulp: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(hasPulp = hasPulp)
            )
        }
    }

    // 新增：后台页只出水水速设置（0-255），用于构建只出水开始指令速度参数
    fun onWaterOnlySpeedChange(speed: String) {
        _uiState.update { currentState ->
            val normalized = java.text.Normalizer.normalize(speed, java.text.Normalizer.Form.NFKC)
            val digits = normalized.filter { it.isDigit() }
            val value = digits.toIntOrNull() ?: 0
            val clamped = value.coerceIn(0, 255)
            currentState.copy(
                waterOnlySpeed = clamped
            )
        }
    }

    fun saveWaterOnlySpeed() {
        val speed = _uiState.value.waterOnlySpeed
        val toSave = if (speed <= 0) 60 else speed.coerceIn(1, 255)
        sharedPreferences.edit().putInt("water_only_speed", toSave).apply()
    }

    fun onWaterSpeedChange(speed: String) {
        _uiState.update { currentState ->
            val normalized = java.text.Normalizer.normalize(speed, java.text.Normalizer.Form.NFKC)
            val digits = normalized.filter { it.isDigit() }
            val value = digits.toIntOrNull() ?: 0
            val clamped = value.coerceIn(0, 255)
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(waterSpeed = clamped)
            )
        }
    }

    fun onJuiceSpeedChange(speed: String) {
        _uiState.update { currentState ->
            val normalized = java.text.Normalizer.normalize(speed, java.text.Normalizer.Form.NFKC)
            val digits = normalized.filter { it.isDigit() }
            val value = digits.toIntOrNull() ?: 0
            val clamped = value.coerceIn(0, 255)
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(juiceSpeed = clamped)
            )
        }
    }

    // 新增：含果肉补偿参数输入（总杯数/递减间隔/递减量）——按饮品独立，编辑页直接写入 recipeToEdit
    fun onPulpTotalCupsChange(input: String) {
        _uiState.update { currentState ->
            val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC)
            val digits = normalized.filter { it.isDigit() }
            val value = digits.toIntOrNull() ?: currentState.recipeToEdit.pulpTotalCups
            val clamped = value.coerceIn(1, 255)
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(pulpTotalCups = clamped)
            )
        }
    }

    fun onPulpDecIntervalChange(input: String) {
        _uiState.update { currentState ->
            val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC)
            val digits = normalized.filter { it.isDigit() }
            val value = digits.toIntOrNull() ?: currentState.recipeToEdit.pulpDecInterval
            val clamped = value.coerceIn(0, 255)
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(pulpDecInterval = clamped)
            )
        }
    }

    fun onPulpDecAmountChange(input: String) {
        _uiState.update { currentState ->
            val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC)
            val digits = normalized.filter { it.isDigit() }
            val value = digits.toIntOrNull() ?: currentState.recipeToEdit.pulpDecAmount
            val clamped = value.coerceIn(0, 255)
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(pulpDecAmount = clamped)
            )
        }
    }

    // 新增：果汁类型修改
    fun onJuiceTypeChange(type: String) {
        _uiState.update { currentState ->
            currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(juiceType = type.trim())
            )
        }
    }

    fun onJuiceChannelChange(channel: String) {
        val currentState = _uiState.value
        val current = currentState.recipeToEdit.juiceChannel
        val parsed = channel.toIntOrNull()
        val safe = parsed?.takeIf { it in 1..3 }
        if (safe != null) {
            _uiState.value = currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(juiceChannel = safe)
            )
        } else {
            // 非法输入：保持现值不变，避免自动回落/跳变
            _uiState.value = currentState.copy(
                recipeToEdit = currentState.recipeToEdit.copy(juiceChannel = current)
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
                // 获取当前选中的图片URI，并在IO线程执行复制持久化，避免主线程阻塞
                val pickedUri = _uiState.value.selectedImageUri
                val persistedUri = pickedUri?.let { persistImageToPrivateStorage(it) }?.toString()
                // 修复：持久化失败时回退到所选URI；未选择则保留原来的 imageUri
                val effectiveImageUri = persistedUri ?: pickedUri?.toString() ?: recipe.imageUri
                val recipeWithImage = recipe.copy(imageUri = effectiveImageUri)

                // 仅当新建或“默认库存”发生变化时，同步 currentRemainingWeight；否则保留编辑页设置的当前库存
                val existing = _uiState.value.recipes.find { it.id == recipeWithImage.id }
                val adjusted = if (recipeWithImage.id == 0 || existing == null) {
                    // 新建：若编辑页已输入当前库存则保留，否则用默认库存初始化
                    val initCurrent = if (recipeWithImage.currentRemainingWeight > 0) recipeWithImage.currentRemainingWeight else recipeWithImage.defaultRemainingWeight
                    recipeWithImage.copy(currentRemainingWeight = initCurrent)
                } else if (existing.defaultRemainingWeight != recipeWithImage.defaultRemainingWeight) {
                    recipeWithImage.copy(currentRemainingWeight = recipeWithImage.defaultRemainingWeight)
                } else {
                    // 保留用户在编辑页设置的当前库存（若未设置则沿用现值）
                    val keepCurrent = if (recipeWithImage.currentRemainingWeight >= 0) recipeWithImage.currentRemainingWeight else existing.currentRemainingWeight
                    recipeWithImage.copy(currentRemainingWeight = keepCurrent)
                }

                // 在IO线程执行数据库写入，提升可靠性，避免与UI竞争
                val saved: Recipe = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (adjusted.id == 0) {
                        val newId = recipeRepository.insertRecipe(adjusted).toInt()
                        adjusted.copy(id = newId)
                    } else {
                        recipeRepository.updateRecipeFields(adjusted)
                        adjusted
                    }
                }

                // 写入后强校验：再次从数据库读取最新值，确保UI与发送路径都使用落库后的数据
                val fresh = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // 保障速度为0的配方统一修正为60（避免设备不出液）
                    recipeRepository.updateJuiceSpeedDefaultIfZero()
                    recipeRepository.getRecipeById(saved.id.toLong())
                } ?: saved

                Log.d(
                    "DrinkMenuViewModel",
                    "配方保存成功: ${saved.name}, id=${saved.id}, juiceSpeed=${saved.juiceSpeed}, waterSpeed=${saved.waterSpeed}, imageUri=$persistedUri, 当前库存=${saved.currentRemainingWeight}"
                )

                // 若当前选中正是被编辑的配方，刷新 selectedRecipe，避免保存后仍用旧对象下单
                _uiState.update { state ->
                    val replaced = state.recipes.map { if (it.id == fresh.id) fresh else it }
                    state.copy(
                        recipes = replaced,
                        selectedRecipe = if (state.selectedRecipe?.id == fresh.id) fresh else state.selectedRecipe,
                        recipeToEdit = fresh,
                        errorMessage = "配方保存成功"
                    )
                }

                // 清除选中的图片（不影响已保存的 imageUri）
                clearSelectedImage()
            } catch (e: Exception) {
                Log.e("DrinkMenuViewModel", "保存配方失败: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = "保存失败: ${e.message}") }
            }
        }
    }

    // 动态果汁量计算：
    // - 按配方的固定间隔/固定步长递减逻辑（默认每两杯减2ml）
    // - 不再使用“库存比例线性递减”，以避免非预期跳变
    // - 结果在 0..255 且不超过当前剩余库存
    private fun computeDynamicJuiceAmount(recipe: Recipe, cupSize: String, state: DrinkMenuUiState): Int {
        val scale = if (cupSize == "大杯") 1.3 else 1.0
        val baseScaled = (recipe.juice * scale).toInt()
        val defaultRemain = recipe.defaultRemainingWeight.takeIf { it > 0 } ?: baseScaled
        val currentRemain = recipe.currentRemainingWeight.coerceAtLeast(0)
        var adjusted = baseScaled

        // 果肉补偿：按已制作杯数与策略进行额外递减
        if (recipe.hasPulp) {
            val total = recipe.pulpTotalCups.coerceIn(1, 255)
            val interval = recipe.pulpDecInterval.coerceIn(0, 255)
            val decAmt = recipe.pulpDecAmount.coerceIn(0, 255)
            // 估算已做杯数：默认库存消耗量 / 单杯果汁量（按当前杯型）
            val perCup = baseScaled.coerceAtLeast(1)
            val madeCups = ((defaultRemain - currentRemain).toDouble() / perCup.toDouble()).toInt().coerceAtLeast(0)
            val steps = if (interval <= 0) 0 else (madeCups / interval).coerceAtMost(total)
            val compensationReduce = steps * decAmt
            adjusted = (adjusted - compensationReduce).coerceAtLeast(0)
        }

        // 不超过库存与帧限制
        adjusted = adjusted.coerceAtMost(currentRemain)
        return adjusted.coerceIn(0, 255)
    }

    private suspend fun persistImageToPrivateStorage(uri: android.net.Uri): android.net.Uri? {
        return try {
            val context = appContext
            val outFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // 目录不存在时自动创建；若创建失败则依次回退到 cacheDir / externalFilesDir
                val baseCandidates = listOf(context.filesDir, context.cacheDir, context.getExternalFilesDir(null))
                var imagesDir: java.io.File? = null
                for (base in baseCandidates) {
                    if (base == null) continue
                    val dir = java.io.File(base, "images")
                    if (dir.exists() || dir.mkdirs()) { imagesDir = dir; break }
                }
                val imagesDirFinal = imagesDir ?: throw java.io.IOException("无法创建图片目录")
                // 根据 MIME 类型决定扩展名，避免后续解码异常；默认 jpg
                val mime = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
                val ext = when (mime?.lowercase()) {
                    "image/png" -> ".png"
                    "image/webp" -> ".webp"
                    else -> ".jpg"
                }
                val fileName = "recipe_${System.currentTimeMillis()}${ext}"
                val target = java.io.File(imagesDirFinal, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
                target
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

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recipeRepository.deleteRecipe(recipe)
            } catch (e: Exception) {
                Log.e("DrinkMenuViewModel", "删除配方失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "删除配方失败: ${e.message}") }
                }
            }
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
        // 继续制作：按新规发送独立帧 FF 07 00 00 00 00 FE
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
        android.util.Log.d("DrinkMenuViewModel", "发送继续制作指令(新规)")
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
        // 重新制作：按新规发送独立帧 FF 08 00 00 00 00 FE，且本次不计入统计
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
            android.util.Log.d("DrinkMenuViewModel", "重新制作(新规): 饮品=${recipe.name}, 杯型=$cupSize, 冰度=${if(withIce) "正常冰" else "去冰"}")
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
                        // 若处于称重测试等待态，则将本次回调作为称重测试结果
                        val waiting = _uiState.value.showWeighWaitingDialog
                        if (waiting) {
                            _uiState.update { it.copy(showWeighWaitingDialog = false, weighResultMessage = if (success) "称重测试成功" else "称重测试失败") }
                            // 不走订单路径，直接返回
                            return@launch
                        }
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
                    resumeAdsCountdown()
                    _uiState.update { it.copy(
                        errorMessage = if (success) "制作完成" else "制作失败",
                        adsResetTick = it.adsResetTick + 1,
                        isMaking = false
                    ) }
                } catch (e: Exception) {
                    Log.e("DrinkMenuViewModel", "更新订单状态失败: ${e.message}", e)
                    _uiState.update { it.copy(errorMessage = "更新订单状态失败: ${e.message}") }
                }
            }
        }
    }

    // 新增：称重结果提示弹窗关闭
    fun onDismissWeighResult() {
        _uiState.update { it.copy(weighResultMessage = null) }
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existingRecipes = recipeRepository.allRecipes.first()
                existingRecipes.forEach { recipe ->
                    recipeRepository.deleteRecipe(recipe)
                }
                val defaultRecipes = listOf(
                    Recipe(name = "霸气青柠", water = 105, juice = 175, price = 8, defaultRemainingWeight = 1000, currentRemainingWeight = 1000, juiceChannel = 1, imageUri = null, juiceType = "青柠特调", waterSpeed = 60, juiceSpeed = 60),
                    Recipe(name = "霸气杨梅", water = 180, juice = 100, price = 9, defaultRemainingWeight = 1000, currentRemainingWeight = 1000, juiceChannel = 2, imageUri = null, juiceType = "杨梅果饮", waterSpeed = 60, juiceSpeed = 60),
                    Recipe(name = "山野栀子", water = 130, juice = 150, price = 10, defaultRemainingWeight = 1000, currentRemainingWeight = 1000, juiceChannel = 3, imageUri = null, juiceType = "栀子花茶", waterSpeed = 60, juiceSpeed = 60)
                )
                defaultRecipes.forEach { recipe -> recipeRepository.insertRecipe(recipe) }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "默认配方已恢复") }
                }
            } catch (e: Exception) {
                Log.e("DrinkMenuViewModel", "恢复默认配方失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "恢复默认配方失败: ${e.message}") }
                }
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

    // 新增：温度/重量上报与中性完成事件监听注册
    private fun setupTelemetryListeners() {
        hardwareManager.setOnTemperatureReportListener { temp ->
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.update { it.copy(currentTemperature = temp) }
            }
        }
        hardwareManager.setOnWeightReportListener { weight ->
            viewModelScope.launch(Dispatchers.Main) {
                // 更新用户页显示重量
                _uiState.update { it.copy(currentWeight = weight) }
                // 若当前在称重等待弹窗，则以数值结束等待并展示结果
                if (_uiState.value.showWeighWaitingDialog) {
                    _uiState.update { it.copy(showWeighWaitingDialog = false, weighResultMessage = "当前重量：${weight} g") }
                }
            }
        }
        hardwareManager.setOnNeutralCompletionListener {
            viewModelScope.launch(Dispatchers.Main) {
                resumeAdsCountdown()
                _uiState.update { curr -> curr.copy(adsResetTick = curr.adsResetTick + 1) }
            }
        }
    }

    private fun pauseAdsCountdownForPendingCompletion(timeoutMs: Long = 30_000) {
        adsPauseJob?.cancel()
        val resumeAt = System.currentTimeMillis() + timeoutMs
        _uiState.update { it.copy(adsCountdownPaused = true, adsCountdownResumeDeadline = resumeAt, adsResetTick = it.adsResetTick + 1) }
        adsPauseJob = viewModelScope.launch {
            try {
                delay(timeoutMs)
                val state = _uiState.value
                val deadline = state.adsCountdownResumeDeadline
                if (state.adsCountdownPaused && deadline != null && System.currentTimeMillis() >= deadline) {
                    _uiState.update { s -> s.copy(adsCountdownPaused = false, adsCountdownResumeDeadline = null, adsResetTick = s.adsResetTick + 1) }
                }
            } catch (_: Exception) {}
        }
    }

    private fun resumeAdsCountdown() {
        adsPauseJob?.cancel()
        adsPauseJob = null
        _uiState.update { it.copy(adsCountdownPaused = false, adsCountdownResumeDeadline = null, adsResetTick = it.adsResetTick + 1) }
    }
}

class DrinkMenuViewModelFactory(
    private val repository: RecipeRepository,
    private val hardwareManager: HardwareManager,
    private val orderRepository: OrderRepository,
    private val sharedPreferences: android.content.SharedPreferences,
    private val appContext: android.content.Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DrinkMenuViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DrinkMenuViewModel(repository, orderRepository, hardwareManager, sharedPreferences, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
