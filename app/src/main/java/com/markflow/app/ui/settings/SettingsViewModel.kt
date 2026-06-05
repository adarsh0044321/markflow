package com.markflow.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markflow.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val darkTheme: StateFlow<Boolean> = settingsRepository.darkThemeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val autoCapture: StateFlow<Boolean> = settingsRepository.autoCaptureFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val highResCapture: StateFlow<Boolean> = settingsRepository.highResCaptureFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val maxMarks: StateFlow<String> = settingsRepository.maxMarksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "100"
        )

    val passThreshold: StateFlow<String> = settingsRepository.passThresholdFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "33"
        )

    val markSensitivity: StateFlow<String> = settingsRepository.markSensitivityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "50"
        )

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDarkTheme(enabled)
        }
    }

    fun setAutoCapture(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoCapture(enabled)
        }
    }

    fun setHighResCapture(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHighResCapture(enabled)
        }
    }

    fun setMaxMarks(value: String) {
        viewModelScope.launch {
            settingsRepository.setMaxMarks(value)
        }
    }

    fun setPassThreshold(value: String) {
        viewModelScope.launch {
            settingsRepository.setPassThreshold(value)
        }
    }

    fun setMarkSensitivity(value: String) {
        viewModelScope.launch {
            settingsRepository.setMarkSensitivity(value)
        }
    }
}
