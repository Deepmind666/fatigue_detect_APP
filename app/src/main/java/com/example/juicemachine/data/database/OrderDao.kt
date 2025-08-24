package com.example.juicemachine.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 订单数据访问对象
 * 提供订单的增删改查和统计查询功能
 */
@Dao
interface OrderDao {

    // 基础 CRUD 操作
    @Insert
    suspend fun insertOrder(order: Order): Long

    @Update
    suspend fun updateOrder(order: Order)

    @Delete
    suspend fun deleteOrder(order: Order)

    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderById(orderId: Long): Order?

    @Query("SELECT * FROM orders ORDER BY orderTime DESC")
    fun getAllOrders(): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE orderTime BETWEEN :startTime AND :endTime ORDER BY orderTime DESC")
    fun getOrdersByDateRange(startTime: Long, endTime: Long): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE status = :status ORDER BY orderTime DESC")
    fun getOrdersByStatus(status: String): Flow<List<Order>>

    @Query("SELECT * FROM orders ORDER BY orderTime DESC LIMIT :limit")
    fun getRecentOrders(limit: Int = 10): Flow<List<Order>>

    // 统计查询
    @Query(
        """
        SELECT 
            date(orderTime/1000, 'unixepoch', 'localtime') as date,
            COUNT(*) as totalOrders,
            SUM(CASE WHEN status = 'COMPLETED' THEN totalAmount ELSE 0 END) as totalRevenue,
            SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completedOrders,
            SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failedOrders
        FROM orders 
        WHERE orderTime BETWEEN :startTime AND :endTime
        GROUP BY date(orderTime/1000, 'unixepoch', 'localtime')
        ORDER BY date DESC
    """
    )
    suspend fun getDailySalesStats(startTime: Long, endTime: Long): List<DailySalesStats>

    @Query(
        """
        SELECT 
            recipeId,
            recipeName,
            SUM(quantity) as totalQuantity,
            SUM(totalAmount) as totalRevenue,
            AVG(unitPrice) as avgPrice,
            MAX(orderTime) as lastOrderTime
        FROM orders 
        WHERE status = 'COMPLETED' AND orderTime BETWEEN :startTime AND :endTime
        GROUP BY recipeId, recipeName
        ORDER BY totalQuantity DESC
        LIMIT :limit
    """
    )
    suspend fun getPopularRecipeStats(startTime: Long, endTime: Long, limit: Int = 10): List<PopularRecipeStats>

    @Query(
        """
        SELECT 
            recipeId,
            recipeName,
            SUM(actualJuiceConsumption) as totalJuiceConsumed,
            SUM(actualWaterConsumption) as totalWaterConsumed,
            AVG(actualJuiceConsumption) as avgJuicePerOrder,
            AVG(actualWaterConsumption) as avgWaterPerOrder
        FROM orders 
        WHERE status = 'COMPLETED' AND orderTime BETWEEN :startTime AND :endTime
        GROUP BY recipeId, recipeName
        ORDER BY totalJuiceConsumed DESC
    """
    )
    suspend fun getInventoryConsumptionStats(startTime: Long, endTime: Long): List<InventoryConsumptionStats>

    // 新增：按日期+饮品+杯型聚合的趋势统计
    @Query(
        """
        SELECT 
            date(orderTime/1000, 'unixepoch', 'localtime') as date,
            recipeId,
            recipeName,
            cupSize,
            SUM(quantity) as totalQuantity,
            SUM(totalAmount) as totalRevenue
        FROM orders 
        WHERE status = 'COMPLETED' AND orderTime BETWEEN :startTime AND :endTime
        GROUP BY date(orderTime/1000, 'unixepoch', 'localtime'), recipeId, recipeName, cupSize
        ORDER BY date ASC, recipeName ASC
    """
    )
    suspend fun getRecipeDailyTrendStats(startTime: Long, endTime: Long): List<RecipeDailyTrendStats>

    // 新增：本日按小时+饮品聚合（杯型汇总）趋势统计，复用 RecipeDailyTrendStats 数据结构，hour 写入到 date 字段
    @Query(
        """
        SELECT 
            strftime('%H', orderTime/1000, 'unixepoch', 'localtime') as date,
            recipeId,
            recipeName,
            '汇总' as cupSize,
            SUM(quantity) as totalQuantity,
            SUM(totalAmount) as totalRevenue
        FROM orders 
        WHERE status = 'COMPLETED' AND orderTime BETWEEN :startTime AND :endTime
        GROUP BY strftime('%H', orderTime/1000, 'unixepoch', 'localtime'), recipeId, recipeName
        ORDER BY date ASC, recipeName ASC
    """
    )
    suspend fun getRecipeHourlyTrendStats(startTime: Long, endTime: Long): List<RecipeDailyTrendStats>

    // 新增：按饮品+杯型统计
    @Query(
        """
        SELECT 
            recipeId,
            recipeName,
            cupSize,
            SUM(quantity) as totalQuantity,
            SUM(totalAmount) as totalRevenue
        FROM orders 
        WHERE status = 'COMPLETED' AND orderTime BETWEEN :startTime AND :endTime
        GROUP BY recipeId, recipeName, cupSize
        ORDER BY recipeName, cupSize
    """
    )
    suspend fun getRecipeCupStats(startTime: Long, endTime: Long): List<RecipeCupStats>

    // 汇总统计
    @Query("SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED'")
    suspend fun getTotalCompletedOrders(): Int

    @Query("SELECT IFNULL(SUM(totalAmount), 0) FROM orders WHERE status = 'COMPLETED'")
    suspend fun getTotalRevenue(): Int

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED' AND date(orderTime/1000, 'unixepoch', 'localtime') = date('now', 'localtime')")
    suspend fun getTodayCompletedOrders(): Int

    @Query("SELECT IFNULL(SUM(totalAmount), 0) FROM orders WHERE status = 'COMPLETED' AND date(orderTime/1000, 'unixepoch', 'localtime') = date('now', 'localtime')")
    suspend fun getTodayRevenue(): Int

    // 清理操作
    @Query("DELETE FROM orders WHERE orderTime < :cutoffTime")
    suspend fun deleteOrdersOlderThan(cutoffTime: Long)

    @Query("DELETE FROM orders")
    suspend fun deleteAllOrders()
}