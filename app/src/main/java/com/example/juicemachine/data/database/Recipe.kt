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
    // 果汁剩余重量（g）
    val remainWeight: Int,
    val juiceChannel: Int
)