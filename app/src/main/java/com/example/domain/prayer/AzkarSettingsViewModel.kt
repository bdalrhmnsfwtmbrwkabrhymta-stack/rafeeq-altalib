package com.example.domain.prayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
            val current = prefsRepo.userSettingsFlow.first()
            prefsRepo.updateAzkarSettings(
                morningEnabled = enabled,
                morningHour = current.morningAzkarHour,
                morningMinute = current.morningAzkarMinute,
                eveningEnabled = current.isEveningAzkarEnabled,
                eveningHour = current.eveningAzkarHour,
                eveningMinute = current.eveningAzkarMinute
            )
            AzkarScheduler.rescheduleAzkarSuspending(getApplication())
        }
    }

    fun toggleEveningAzkar(enabled: Boolean) {
        viewModelScope.launch {
            val current = prefsRepo.userSettingsFlow.first()
            prefsRepo.updateAzkarSettings(
                morningEnabled = current.isMorningAzkarEnabled,
                morningHour = current.morningAzkarHour,
                morningMinute = current.morningAzkarMinute,
                eveningEnabled = enabled,
                eveningHour = current.eveningAzkarHour,
                eveningMinute = current.eveningAzkarMinute
            )
            AzkarScheduler.rescheduleAzkarSuspending(getApplication())
        }
    }
}
