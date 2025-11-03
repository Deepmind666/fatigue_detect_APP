package com.example.juicemachine.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesRepositoryTest {

    @Test
    fun testSetAndGetBasicPrefs() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = PreferencesRepository(context)

        repo.resetToDefaults()
        repo.setPersonalizationEnabled(true)
        repo.setPersonalizationWeight(0.33f)
        repo.setCriState(CriState.STRICT)

        assertEquals(true, repo.personalizationEnabledFlow.first())
        assertEquals(0.33f, repo.personalizationWeightFlow.first())
        assertEquals(CriState.STRICT, repo.criStateFlow.first())
    }

    @Test
    fun testCalibJsonEncryptionRoundTrip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = PreferencesRepository(context)

        val json = "{\"mu_blink_dur\":120.0,\"sigma_blink_dur\":15.5}"
        repo.setCalibMuSigmaJson(json)

        val readBack = repo.calibMuSigmaJsonFlow.first()
        assertEquals(json, readBack)
    }

    @Test
    fun testSharedPrefsMigration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sp = context.getSharedPreferences("juice_prefs", android.content.Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean("personalization_enabled", false)
            .putFloat("personalization_weight", 0.21f)
            .putString("cri_state", "normal")
            .putString("calib_mu_sigma_json", "{\"mu\":1.0}")
            .apply()

        val repo = PreferencesRepository(context)
        assertEquals(false, repo.personalizationEnabledFlow.first())
        assertEquals(0.21f, repo.personalizationWeightFlow.first())
        assertEquals(CriState.NORMAL, repo.criStateFlow.first())
        assertEquals("{\"mu\":1.0}", repo.calibMuSigmaJsonFlow.first())
    }
}