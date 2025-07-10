package com.example.juicemachine.data.database

import kotlinx.serialization.Serializable

@Serializable
data class CupConfig(
    val ice: Int = 0,
    val juice: Int = 0,
    val water: Int = 0
) 