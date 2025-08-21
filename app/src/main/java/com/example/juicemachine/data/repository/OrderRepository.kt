package com.example.juicemachine.data.repository

import android.content.Context
import com.example.juicemachine.data.database.AppDatabase
import com.example.juicemachine.data.database.DailySalesStats
import com.example.juicemachine.data.database.InventoryConsumptionStats
import com.example.juicemachine.data.database.Order
import com.example.juicemachine.data.database.OrderDao
import com.example.juicemachine.data.database.PopularRecipeStats
import com.example.juicemachine.data.database.RecipeCupStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    // CRUD
    suspend fun insertOrder(order: Order): Long
    suspend fun updateOrder(order: Order)
    suspend fun deleteOrder(order: Order)
    suspend fun getOrderById(orderId: Long): Order?

    // 查询
    fun getAllOrders(): Flow<List<Order>>
    fun getOrdersByDateRange(startTime: Long, endTime: Long): Flow<List<Order>>
    fun getOrdersByStatus(status: String): Flow<List<Order>>
    fun getRecentOrders(limit: Int = 10): Flow<List<Order>>

    // 统计
    suspend fun getDailySalesStats(startTime: Long, endTime: Long): List<DailySalesStats>
    suspend fun getPopularRecipeStats(startTime: Long, endTime: Long, limit: Int = 10): List<PopularRecipeStats>
    suspend fun getInventoryConsumptionStats(startTime: Long, endTime: Long): List<InventoryConsumptionStats>
    suspend fun getRecipeCupStats(startTime: Long, endTime: Long): List<RecipeCupStats>

    // 汇总
    suspend fun getTotalCompletedOrders(): Int
    suspend fun getTotalRevenue(): Int
    suspend fun getTodayCompletedOrders(): Int
    suspend fun getTodayRevenue(): Int

    // 清理
    suspend fun deleteOrdersOlderThan(cutoffTime: Long)
    suspend fun deleteAllOrders()
}

class OrderRepositoryImpl(private val orderDao: OrderDao) : OrderRepository {
    override suspend fun insertOrder(order: Order): Long = orderDao.insertOrder(order)
    override suspend fun updateOrder(order: Order) = orderDao.updateOrder(order)
    override suspend fun deleteOrder(order: Order) = orderDao.deleteOrder(order)
    override suspend fun getOrderById(orderId: Long): Order? = orderDao.getOrderById(orderId)

    override fun getAllOrders(): Flow<List<Order>> = orderDao.getAllOrders()
    override fun getOrdersByDateRange(startTime: Long, endTime: Long): Flow<List<Order>> = orderDao.getOrdersByDateRange(startTime, endTime)
    override fun getOrdersByStatus(status: String): Flow<List<Order>> = orderDao.getOrdersByStatus(status)
    override fun getRecentOrders(limit: Int): Flow<List<Order>> = orderDao.getRecentOrders(limit)

    override suspend fun getDailySalesStats(startTime: Long, endTime: Long): List<DailySalesStats> = orderDao.getDailySalesStats(startTime, endTime)
    override suspend fun getPopularRecipeStats(startTime: Long, endTime: Long, limit: Int): List<PopularRecipeStats> = orderDao.getPopularRecipeStats(startTime, endTime, limit)
    override suspend fun getInventoryConsumptionStats(startTime: Long, endTime: Long): List<InventoryConsumptionStats> = orderDao.getInventoryConsumptionStats(startTime, endTime)
    override suspend fun getRecipeCupStats(startTime: Long, endTime: Long): List<RecipeCupStats> = orderDao.getRecipeCupStats(startTime, endTime)

    override suspend fun getTotalCompletedOrders(): Int = orderDao.getTotalCompletedOrders()
    override suspend fun getTotalRevenue(): Int = orderDao.getTotalRevenue()
    override suspend fun getTodayCompletedOrders(): Int = orderDao.getTodayCompletedOrders()
    override suspend fun getTodayRevenue(): Int = orderDao.getTodayRevenue()

    override suspend fun deleteOrdersOlderThan(cutoffTime: Long) = orderDao.deleteOrdersOlderThan(cutoffTime)
    override suspend fun deleteAllOrders() = orderDao.deleteAllOrders()
}

// 一个简单的 ServiceLocator，便于在没有 DI 框架时获取 Repository
object ServiceLocator {
    fun provideOrderRepository(context: Context, scope: CoroutineScope): OrderRepository {
        val db = AppDatabase.getDatabase(context, scope)
        return OrderRepositoryImpl(db.orderDao())
    }
}
