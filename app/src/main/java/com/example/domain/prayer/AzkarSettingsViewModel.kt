package com.example.domain.prayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AzkarSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = UserPreferencesRepository(application)
    
    val isMorningAzkarEnabled: StateFlow<Boolean> = prefsRepo.userSettingsFlow
        .map { it.isMorningAzkarEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val isEveningAzkarEnabled: StateFlow<Boolean> = prefsRepo.userSettingsFlow
        .map { it.isEveningAzkarEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun toggleMorningAzkar(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAzkarSettings(
                morningEnabled = enabled,
                eveningEnabled = isEveningAzkarEnabled.value
            )
            updateScheduling(enabled, isEveningAzkarEnabled.value)
        }
    }

    fun toggleEveningAzkar(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAzkarSettings(
                morningEnabled = isMorningAzkarEnabled.value,
                eveningEnabled = enabled
            )
            updateScheduling(isMorningAzkarEnabled.value, enabled)
        }
    }
    
    private fun updateScheduling(morningEnabled: Boolean, eveningEnabled: Boolean) {
        PrayerAlarmScheduler.scheduleAzkarAlarms(
            context = getApplication(),
            morningEnabled = morningEnabled,
            eveningEnabled = eveningEnabled
        )
    }
}
