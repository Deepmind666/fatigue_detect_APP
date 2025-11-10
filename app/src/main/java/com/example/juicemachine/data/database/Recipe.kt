package com.example.juicemachine.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded
import com.example.juicemachine.R
import androidx.room.ColumnInfo
import androidx.annotation.DrawableRes

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val water: Int,
    val juice: Int,
    val price: Int,
    // 默认剩余重量（g），用于“鲜料重置”恢复基准
    @ColumnInfo(defaultValue = "0") val defaultRemainingWeight: Int,
    // 当前剩余重量（g），参与售罄/下单扣减判断
    @ColumnInfo(defaultValue = "0") val currentRemainingWeight: Int,
    val juiceChannel: Int,
    // 图片URI字符串
    val imageUri: String? = null,
    // 新增：果汁类型（例如 橙汁/百香果/柠檬茶等），用于后端绑定与统计
    @ColumnInfo(defaultValue = "") val juiceType: String = "",
    // 新增：是否含果肉（后端流速与通道控制依据）
    @ColumnInfo(defaultValue = "0") val hasPulp: Boolean = false,
    // 新增：含果肉补偿参数（每种饮品独立）：总杯数/递减间隔/递减量（ml）
    @ColumnInfo(defaultValue = "8") val pulpTotalCups: Int = 8,
    @ColumnInfo(defaultValue = "2") val pulpDecInterval: Int = 2,
    @ColumnInfo(defaultValue = "2") val pulpDecAmount: Int = 2,
    // 新增：只出水流速（0 表示采用设备默认）
    @ColumnInfo(defaultValue = "60") val waterSpeed: Int = 60,
    // 新增：果汁流速（默认 60，避免初始为 0 导致不出液）
    @ColumnInfo(defaultValue = "60") val juiceSpeed: Int = 60
)