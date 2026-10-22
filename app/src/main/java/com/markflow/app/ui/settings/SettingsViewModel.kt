package com.markflow.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markflow.app.data.local.MarkFlowDatabase
import com.markflow.app.data.repository.SettingsRepository
import com.markflow.app.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val database: MarkFlowDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ── Database flows ──
    val darkTheme: StateFlow<Boolean> = settingsRepository.darkThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoCapture: StateFlow<Boolean> = settingsRepository.autoCaptureFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val highResCapture: StateFlow<Boolean> = settingsRepository.highResCaptureFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val maxMarksVal: StateFlow<String> = settingsRepository.maxMarksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "100")

    val passThresholdVal: StateFlow<String> = settingsRepository.passThresholdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "33")

    val markSensitivityVal: StateFlow<String> = settingsRepository.markSensitivityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "50")

    val answerSheetOrientation: StateFlow<String> = settingsRepository.answerSheetOrientationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "portrait")

    val defaultQuestionMarksVal: StateFlow<String> = settingsRepository.defaultQuestionMarksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "5.0")

    val markRecognitionLimitMinVal: StateFlow<String> = settingsRepository.markRecognitionLimitMinFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0.0")

    val markRecognitionLimitMaxVal: StateFlow<String> = settingsRepository.markRecognitionLimitMaxFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "10.0")

    val autoCrop: StateFlow<Boolean> = settingsRepository.autoCropFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showAnnotations: StateFlow<Boolean> = settingsRepository.showAnnotationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ── Local mutable UI states for manual configuration (Save Settings Button) ──
    val uiMaxMarks = MutableStateFlow("")
    val uiDefaultQuestionMarks = MutableStateFlow("")
    val uiMarkSensitivity = MutableStateFlow("")
    val uiPassThreshold = MutableStateFlow("")
    val uiMarkRecognitionLimitMin = MutableStateFlow("")
    val uiMarkRecognitionLimitMax = MutableStateFlow("")

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.maxMarksFlow,
                settingsRepository.defaultQuestionMarksFlow,
                settingsRepository.markSensitivityFlow,
                settingsRepository.passThresholdFlow,
                settingsRepository.markRecognitionLimitMinFlow,
                settingsRepository.markRecognitionLimitMaxFlow
            ) { args ->
                val max = args[0]
                val qMax = args[1]
                val sens = args[2]
                val pass = args[3]
                val minL = args[4]
                val maxL = args[5]
                if (uiMaxMarks.value.isEmpty()) uiMaxMarks.value = max
                if (uiDefaultQuestionMarks.value.isEmpty()) uiDefaultQuestionMarks.value = qMax
                if (uiMarkSensitivity.value.isEmpty()) uiMarkSensitivity.value = sens
                if (uiPassThreshold.value.isEmpty()) uiPassThreshold.value = pass
                if (uiMarkRecognitionLimitMin.value.isEmpty()) uiMarkRecognitionLimitMin.value = minL
                if (uiMarkRecognitionLimitMax.value.isEmpty()) uiMarkRecognitionLimitMax.value = maxL
            }.collect()
        }
    }

    // ── Dirty check flow ──
    val hasUnsavedChanges: StateFlow<Boolean> = combine(
        uiMaxMarks, uiDefaultQuestionMarks, uiMarkSensitivity, uiPassThreshold,
        uiMarkRecognitionLimitMin, uiMarkRecognitionLimitMax,
        maxMarksVal, defaultQuestionMarksVal, markSensitivityVal, passThresholdVal,
        markRecognitionLimitMinVal, markRecognitionLimitMaxVal
    ) { args ->
        args[0] != args[6] || args[1] != args[7] || args[2] != args[8] ||
        args[3] != args[9] || args[4] != args[10] || args[5] != args[11]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ── Immediate Auto-saving setters ──
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

    fun setAnswerSheetOrientation(value: String) {
        viewModelScope.launch {
            settingsRepository.setAnswerSheetOrientation(value)
        }
    }

    fun setAutoCrop(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoCrop(enabled)
        }
    }

    fun setShowAnnotations(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowAnnotations(enabled)
        }
    }

    // ── Pending UI setters ──
    fun updateMaxMarks(value: String) {
        uiMaxMarks.value = value
    }

    fun updateDefaultQuestionMarks(value: String) {
        uiDefaultQuestionMarks.value = value
    }

    fun updateMarkSensitivity(value: String) {
        uiMarkSensitivity.value = value
    }

    fun updatePassThreshold(value: String) {
        uiPassThreshold.value = value
    }

    fun updateMarkRecognitionLimitMin(value: String) {
        uiMarkRecognitionLimitMin.value = value
    }

    fun updateMarkRecognitionLimitMax(value: String) {
        uiMarkRecognitionLimitMax.value = value
    }

    // ── Batch save / discard operations ──
    fun saveSettings(onSuccess: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setMaxMarks(uiMaxMarks.value)
            settingsRepository.setDefaultQuestionMarks(uiDefaultQuestionMarks.value)
            settingsRepository.setMarkSensitivity(uiMarkSensitivity.value)
            settingsRepository.setPassThreshold(uiPassThreshold.value)
            settingsRepository.setMarkRecognitionLimitMin(uiMarkRecognitionLimitMin.value)
            settingsRepository.setMarkRecognitionLimitMax(uiMarkRecognitionLimitMax.value)
            onSuccess()
        }
    }

    fun discardChanges() {
        uiMaxMarks.value = maxMarksVal.value
        uiDefaultQuestionMarks.value = defaultQuestionMarksVal.value
        uiMarkSensitivity.value = markSensitivityVal.value
        uiPassThreshold.value = passThresholdVal.value
        uiMarkRecognitionLimitMin.value = markRecognitionLimitMinVal.value
        uiMarkRecognitionLimitMax.value = markRecognitionLimitMaxVal.value
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                // Clear SQLite Database tables
                database.clearAllTables()
                // Recursively delete all saved bitmap assets
                FileUtils.getAppStorageDir(context).deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onComplete()
        }
    }
}
