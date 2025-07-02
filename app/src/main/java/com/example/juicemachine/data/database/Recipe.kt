package com.example.juicemachine.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val imageResId: Int? = null,
    val imageUri: String? = null,
    val small: CupConfig = CupConfig(ice = 100, juice = 60, water = 100),
    val medium: CupConfig = CupConfig(ice = 130, juice = 80, water = 120),
    val large: CupConfig = CupConfig(ice = 160, juice = 100, water = 140)
)