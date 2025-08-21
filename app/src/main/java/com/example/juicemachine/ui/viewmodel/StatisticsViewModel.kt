package com.example.juicemachine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.juicemachine.data.database.DailySalesStats
import com.example.juicemachine.data.database.InventoryConsumptionStats
import com.example.juicemachine.data.database.PopularRecipeStats
import com.example.juicemachine.data.database.RecipeCupStats
import com.example.juicemachine.data.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

data class StatisticsUiState(
    val totalRevenue: Int = 0,
    val totalOrders: Int = 0,
    val todayRevenue: Int = 0,
    val todayOrders: Int = 0,
    val dailySales: List<DailySalesStats> = emptyList(),
    val popularRecipes: List<PopularRecipeStats> = emptyList(),
    val inventoryStats: List<InventoryConsumptionStats> = emptyList(),
    val recipeCupStats: List<RecipeCupStats> = emptyList(),
    val selectedTimePeriod: TimePeriod = TimePeriod.WEEK,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

enum class TimePeriod(val displayName: String, val days: Int) {
    WEEK("本周", 7),
    MONTH("本月", 30),
    QUARTER("本季度", 90)
}

class StatisticsViewModel(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadStatistics()
        // 订阅订单更新事件，自动刷新统计
        viewModelScope.launch {
            try {
                StatisticsRefreshNotifier.events.collect {
                    loadStatistics()
                }
            } catch (e: Exception) {
                android.util.Log.e("StatisticsViewModel", "统计事件订阅失败: ${e.message}", e)
            }
        }
    }

    fun onTimePeriodChanged(timePeriod: TimePeriod) {
        _uiState.update { it.copy(selectedTimePeriod = timePeriod) }
        loadStatistics()
    }

    fun onRefresh() {
        loadStatistics()
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun loadStatistics() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        viewModelScope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                val selectedPeriod = _uiState.value.selectedTimePeriod
                val startTime = currentTime - (selectedPeriod.days * 24L * 60L * 60L * 1000L)
                
                // 获取今天开始时间（零点）
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val todayStartTime = calendar.timeInMillis
                
                // 并行获取所有统计数据
                val totalRevenue = orderRepository.getTotalRevenue()
                val totalOrders = orderRepository.getTotalCompletedOrders()
                val todayRevenue = orderRepository.getTodayRevenue()
                val todayOrders = orderRepository.getTodayCompletedOrders()
                val dailySales = orderRepository.getDailySalesStats(startTime, currentTime)
                val popularRecipes = orderRepository.getPopularRecipeStats(startTime, currentTime, 10)
                val inventoryStats = orderRepository.getInventoryConsumptionStats(startTime, currentTime)
                val recipeCupStats = orderRepository.getRecipeCupStats(startTime, currentTime)
                
                _uiState.update { currentState ->
                    currentState.copy(
                        totalRevenue = totalRevenue,
                        totalOrders = totalOrders,
                        todayRevenue = todayRevenue,
                        todayOrders = todayOrders,
                        dailySales = dailySales,
                        popularRecipes = popularRecipes,
                        inventoryStats = inventoryStats,
                        recipeCupStats = recipeCupStats,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("StatisticsViewModel", "加载统计数据失败: ${e.message}", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "加载统计数据失败: ${e.message}"
                    )
                }
            }
        }
    }
}

class StatisticsViewModelFactory(
    private val orderRepository: OrderRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StatisticsViewModel(orderRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}