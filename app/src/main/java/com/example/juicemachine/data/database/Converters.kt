package com.example.juicemachine.data.database

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromCupConfig(cupConfig: CupConfig): String {
        return Json.encodeToString(cupConfig)
    }

    @TypeConverter
    fun toCupConfig(cupConfigString: String): CupConfig {
        return Json.decodeFromString(cupConfigString)
    }
} 