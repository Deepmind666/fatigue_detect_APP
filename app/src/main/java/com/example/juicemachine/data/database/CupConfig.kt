package com.example.juicemachine.data.database

import kotlinx.serialization.Serializable

@Serializable
data class CupConfig(
    val ice: Int,
    val juice: Int,
    val water: Int
) 