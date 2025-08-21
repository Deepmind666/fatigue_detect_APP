package com.example.juicemachine.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recipeId: Int,
    val recipeName: String,
    val quantity: Int,
    val unitPrice: Int,
    val totalAmount: Int,
    val cupSize: String,
    val withIce: Boolean,
    val status: String,
    val orderTime: Long = System.currentTimeMillis(),
    val actualJuiceConsumption: Int,
    val actualWaterConsumption: Int,
    val notes: String? = null
)

// 日销售统计
data class DailySalesStats(
    val date: String,
    val totalOrders: Int,
    val totalRevenue: Int,
    val completedOrders: Int,
    val failedOrders: Int
)

// 热销商品统计
data class PopularRecipeStats(
    val recipeId: Int,
    val recipeName: String,
    val totalQuantity: Int,
    val totalRevenue: Int,
    val avgPrice: Double,
    val lastOrderTime: Long
)

// 库存消耗统计
data class InventoryConsumptionStats(
    val recipeId: Int,
    val recipeName: String,
    val totalJuiceConsumed: Int,
    val totalWaterConsumed: Int,
    val avgJuicePerOrder: Double,
    val avgWaterPerOrder: Double
)

// 新增：按饮品+杯型统计
data class RecipeCupStats(
    val recipeId: Int,
    val recipeName: String,
    val cupSize: String,
    val totalQuantity: Int,
    val totalRevenue: Int
)