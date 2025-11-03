package com.example.juicemachine.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.juicemachine.data.PreferencesRepository
import com.example.juicemachine.data.CriState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PreferencesViewModel(private val repo: PreferencesRepository) : ViewModel() {
    val personalizationEnabled: StateFlow<Boolean> = repo.personalizationEnabledFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val personalizationWeight: StateFlow<Float> = repo.personalizationWeightFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0f
    )
    val criState: StateFlow<CriState> = repo.criStateFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), CriState.NORMAL
    )
    val calibMuSigmaJson: StateFlow<String> = repo.calibMuSigmaJsonFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    fun setPersonalizationEnabled(v: Boolean) = viewModelScope.launch { repo.setPersonalizationEnabled(v) }
    fun setPersonalizationWeight(v: Float) = viewModelScope.launch { repo.setPersonalizationWeight(v) }
    fun setCriState(v: CriState) = viewModelScope.launch { repo.setCriState(v) }
    fun setCalibMuSigmaJson(json: String) = viewModelScope.launch { repo.setCalibMuSigmaJson(json) }
    fun resetToDefaults() = viewModelScope.launch { repo.resetToDefaults() }
}

class PreferencesViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PreferencesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PreferencesViewModel(PreferencesRepository(context.applicationContext)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.simpleName}")
    }
}