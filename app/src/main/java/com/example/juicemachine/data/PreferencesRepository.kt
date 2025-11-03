package com.example.juicemachine.data

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.juicemachine.util.DebugLogger

/**
 * PreferencesRepository backed by DataStore with a migration from SharedPreferences.
 * Sensitive field `calib_mu_sigma_json` is encrypted transparently.
 */
class PreferencesRepository(private val context: Context) {
    companion object Keys {
        val PERSONALIZATION_ENABLED = booleanPreferencesKey("personalization_enabled")
        val PERSONALIZATION_WEIGHT = floatPreferencesKey("personalization_weight")
        val CRI_STATE = stringPreferencesKey("cri_state") // normal | strict
        val CALIB_MU_SIGMA_JSON = stringPreferencesKey("calib_mu_sigma_json") // encrypted base64

        private const val DS_NAME = "juice_prefs_datastore"
        private const val SHARED_PREFS_NAME = "juice_prefs"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataStore = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(DS_NAME) },
        migrations = listOf(
            // Copies existing SharedPreferences (juice_prefs) into DataStore
            SharedPreferencesMigration(context, SHARED_PREFS_NAME)
        )
    )

    init {
        // One-time upgrade: if calib json exists in plain text, re-encrypt it
        scope.launch {
            try {
                val prefs = dataStore.data.first()
                val v = prefs[CALIB_MU_SIGMA_JSON]
                if (!v.isNullOrBlank() && !v.startsWith("enc:")) {
                    dataStore.edit { store ->
                        store[CALIB_MU_SIGMA_JSON] = "enc:${PreferenceCrypto.encryptToBase64(v)}"
                    }
                }
            } catch (_: Exception) { /* ignore */ }
        }
    }

    // Flows
    val personalizationEnabledFlow: Flow<Boolean> = dataStore.data.map { it[PERSONALIZATION_ENABLED] ?: false }
    val personalizationWeightFlow: Flow<Float> = dataStore.data.map { (it[PERSONALIZATION_WEIGHT] ?: 0.0f).coerceIn(0.0f, 0.4f) }
    val criStateFlow: Flow<CriState> = dataStore.data.map { if ((it[CRI_STATE] ?: "normal") == "strict") CriState.STRICT else CriState.NORMAL }
    val calibMuSigmaJsonFlow: Flow<String> = dataStore.data.map { v ->
        val payload = v[CALIB_MU_SIGMA_JSON] ?: ""
        when {
            payload.isBlank() -> ""
            payload.startsWith("enc:") -> PreferenceCrypto.decryptFromBase64(payload.removePrefix("enc:"))
            else -> payload
        }
    }

    // Setters
    suspend fun setPersonalizationEnabled(value: Boolean) = dataStore.edit {
        val old = it[PERSONALIZATION_ENABLED] ?: false
        it[PERSONALIZATION_ENABLED] = value
        DebugLogger.i("Preferences", "personalization_enabled: $old -> $value", showToast = false)
    }
    suspend fun setPersonalizationWeight(value: Float) = dataStore.edit {
        val old = it[PERSONALIZATION_WEIGHT] ?: 0.0f
        val clamped = value.coerceIn(0.0f, 0.4f)
        it[PERSONALIZATION_WEIGHT] = clamped
        DebugLogger.i("Preferences", "personalization_weight: $old -> $clamped", showToast = false)
    }
    suspend fun setCriState(value: CriState) = dataStore.edit {
        val old = it[CRI_STATE] ?: "normal"
        val new = if (value == CriState.STRICT) "strict" else "normal"
        it[CRI_STATE] = new
        DebugLogger.i("Preferences", "cri_state: $old -> $new", showToast = false)
    }
    suspend fun setCalibMuSigmaJson(plainJson: String) = dataStore.edit {
        val had = !((it[CALIB_MU_SIGMA_JSON] ?: "").isBlank())
        val newEnc = if (plainJson.isBlank()) "" else "enc:${PreferenceCrypto.encryptToBase64(plainJson)}"
        it[CALIB_MU_SIGMA_JSON] = newEnc
        DebugLogger.i("Preferences", "calib_mu_sigma_json ${if (had) "updated" else "set"}", showToast = false)
    }

    suspend fun resetToDefaults() = dataStore.edit {
        it[PERSONALIZATION_ENABLED] = false
        it[PERSONALIZATION_WEIGHT] = 0.0f
        it[CRI_STATE] = "normal"
        it[CALIB_MU_SIGMA_JSON] = ""
    }
}

enum class CriState { NORMAL, STRICT }