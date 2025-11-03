package com.example.juicemachine.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("juice_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_PERSONALIZATION_ENABLED = "personalization_enabled"
        const val KEY_PERSONALIZATION_WEIGHT = "personalization_weight" // 0.0..0.4
        const val KEY_CRI_STATE = "cri_state" // normal|strict
        const val KEY_CALIB_MU_SIGMA_JSON = "calib_mu_sigma_json" // 仅存统计量
    }

    var personalizationEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERSONALIZATION_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PERSONALIZATION_ENABLED, value).apply()
        }

    var personalizationWeight: Float
        get() = prefs.getFloat(KEY_PERSONALIZATION_WEIGHT, 0.0f)
        set(value) {
            val clamped = value.coerceIn(0.0f, 0.4f)
            prefs.edit().putFloat(KEY_PERSONALIZATION_WEIGHT, clamped).apply()
        }

    var criState: String
        get() = prefs.getString(KEY_CRI_STATE, "normal") ?: "normal"
        set(value) {
            prefs.edit().putString(KEY_CRI_STATE, value).apply()
        }

    var calibMuSigmaJson: String
        get() = prefs.getString(KEY_CALIB_MU_SIGMA_JSON, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_CALIB_MU_SIGMA_JSON, value).apply()
        }

    fun observe(listener: (key: String) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null) listener(key)
        }
        prefs.registerOnSharedPreferenceChangeListener(l)
        return l
    }

    fun removeObserver(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(l)
    }

    fun resetToDefaults() {
        prefs.edit()
            .putBoolean(KEY_PERSONALIZATION_ENABLED, false)
            .putFloat(KEY_PERSONALIZATION_WEIGHT, 0.0f)
            .putString(KEY_CRI_STATE, "normal")
            .putString(KEY_CALIB_MU_SIGMA_JSON, "")
            .apply()
    }
}