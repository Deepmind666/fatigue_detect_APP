package com.example.juicemachine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.juicemachine.data.database.DailySalesStats
import com.example.juicemachine.data.database.InventoryConsumptionStats
import com.example.juicemachine.data.database.PopularRecipeStats
import com.example.juicemachine.data.database.RecipeCupStats
import com.example.juicemachine.data.database.RecipeDailyTrendStats
import com.example.juicemachine.data.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

// 新的 UI 数据模型：为图表提供按饮品聚合后的趋势（杯型汇总）
data class RecipeTrendByDrink(
    val label: String, // 饮品名称
    val values: List<Int> // 与 dates 对齐的值（营收或销量）
)

data class StatisticsUiState(
    val totalRevenue: Int = 0,
    val totalOrders: Int = 0,
    val todayRevenue: Int = 0,
    val todayOrders: Int = 0,
    val dailySales: List<DailySalesStats> = emptyList(),
    val popularRecipes: List<PopularRecipeStats> = emptyList(),
    val inventoryStats: List<InventoryConsumptionStats> = emptyList(),
    val recipeCupStats: List<RecipeCupStats> = emptyList(),
    val recipeDailyTrends: List<RecipeDailyTrendStats> = emptyList(),
    val chartDates: List<String> = emptyList(),
    val chartSeries: List<RecipeTrendByDrink> = emptyList(),
    val selectedTimePeriod: TimePeriod = TimePeriod.TODAY,
    val customStartTime: Long? = null,
    val customEndTime: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

enum class TimePeriod(val displayName: String, val days: Int) {
    TODAY("本日", 1),
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
        _uiState.update { it.copy(selectedTimePeriod = timePeriod, customStartTime = null, customEndTime = null) }
        loadStatistics()
    }

    fun onRefresh() { loadStatistics() }

    fun onDismissError() { _uiState.update { it.copy(errorMessage = null) } }

    fun applyCustomRange(startMillis: Long, endMillis: Long) {
        val start = startOfDay(startMillis)
        val end = endOfDay(endMillis)
        _uiState.update { it.copy(customStartTime = start, customEndTime = end) }
        loadStatistics()
    }

    fun clearCustomRange() {
        _uiState.update { it.copy(customStartTime = null, customEndTime = null) }
        loadStatistics()
    }

    private fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun endOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    private fun loadStatistics() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()

                // 计算时间范围：优先使用自定义范围，否则按选中周期
                val (startTime, endTime, dates, useHourly) = _uiState.value.let { state ->
                    val customStart = state.customStartTime
                    val customEnd = state.customEndTime
                    if (customStart != null && customEnd != null) {
                        val d = daysBetween(customStart, customEnd)
                        val dateKeys = buildDateKeys(customStart, customEnd)
                        Quad(customStart, customEnd, dateKeys, false)
                    } else {
                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val todayStartTime = calendar.timeInMillis
                        if (state.selectedTimePeriod == TimePeriod.TODAY) {
                            val hours = (0..23).map { String.format("%02d", it) }
                            Quad(todayStartTime, now, hours, true)
                        } else {
                            val start = now - (state.selectedTimePeriod.days * 24L * 60L * 60L * 1000L)
                            val dateKeys = buildDateKeys(start, now)
                            Quad(start, now, dateKeys, false)
                        }
                    }
                }

                // 并行获取所有统计数据
                val totalRevenue = orderRepository.getTotalRevenue()
                val totalOrders = orderRepository.getTotalCompletedOrders()
                val todayRevenue = orderRepository.getTodayRevenue()
                val todayOrders = orderRepository.getTodayCompletedOrders()
                val dailySales = orderRepository.getDailySalesStats(startTime, endTime)
                val popularRecipes = orderRepository.getPopularRecipeStats(startTime, endTime, 10)
                val inventoryStats = orderRepository.getInventoryConsumptionStats(startTime, endTime)
                val recipeCupStats = orderRepository.getRecipeCupStats(startTime, endTime)
                val trendsRaw = if (useHourly) orderRepository.getRecipeHourlyTrendStats(startTime, endTime) else orderRepository.getRecipeDailyTrendStats(startTime, endTime)

                // 将趋势数据按饮品合并（杯型一起统计）并补齐缺失
                val grouped = trendsRaw.groupBy { it.recipeName }
                val series = grouped.map { (name, list) ->
                    val map = list.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.totalRevenue } }
                    val values = dates.map { d -> map[d] ?: 0 }
                    RecipeTrendByDrink(label = name, values = values)
                }

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
                        recipeDailyTrends = trendsRaw,
                        chartDates = dates,
                        chartSeries = series,
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

    private fun buildDateKeys(start: Long, end: Long): List<String> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val res = mutableListOf<String>()
        while (cal.timeInMillis <= end) {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)
            res.add(String.format("%04d-%02d-%02d", y, m, d))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return res
    }

    private fun daysBetween(start: Long, end: Long): Int {
        val diff = end - start
        return (diff / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(1)
    }

    // 为了返回四元组
    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
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
