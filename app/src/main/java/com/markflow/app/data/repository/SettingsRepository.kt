package com.markflow.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AUTO_CAPTURE = booleanPreferencesKey("auto_capture")
        val HIGH_RES_CAPTURE = booleanPreferencesKey("high_res_capture")
        val MAX_MARKS = stringPreferencesKey("max_marks")
        val PASS_THRESHOLD = stringPreferencesKey("pass_threshold")
        val MARK_SENSITIVITY = stringPreferencesKey("mark_sensitivity")
        val ANSWER_SHEET_ORIENTATION = stringPreferencesKey("answer_sheet_orientation")
        val DEFAULT_QUESTION_MARKS = stringPreferencesKey("default_question_marks")
        val MARK_RECOGNITION_LIMIT_MIN = stringPreferencesKey("mark_recognition_limit_min")
        val MARK_RECOGNITION_LIMIT_MAX = stringPreferencesKey("mark_recognition_limit_max")
        val AUTO_CROP = booleanPreferencesKey("auto_crop")
        val SHOW_ANNOTATIONS = booleanPreferencesKey("show_annotations")
    }

    val darkThemeFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.DARK_THEME] ?: false
        }

    val autoCaptureFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_CAPTURE] ?: true
        }

    val highResCaptureFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.HIGH_RES_CAPTURE] ?: false
        }

    val maxMarksFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MAX_MARKS] ?: "100"
        }

    val passThresholdFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.PASS_THRESHOLD] ?: "33"
        }

    val markSensitivityFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MARK_SENSITIVITY] ?: "50"
        }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_THEME] = enabled
        }
    }

    suspend fun setAutoCapture(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CAPTURE] = enabled
        }
    }

    suspend fun setHighResCapture(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIGH_RES_CAPTURE] = enabled
        }
    }

    suspend fun setMaxMarks(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_MARKS] = value
        }
    }

    suspend fun setPassThreshold(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PASS_THRESHOLD] = value
        }
    }

    suspend fun setMarkSensitivity(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MARK_SENSITIVITY] = value
        }
    }

    val answerSheetOrientationFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.ANSWER_SHEET_ORIENTATION] ?: "portrait"
        }

    val isOrientationSetFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences.contains(PreferencesKeys.ANSWER_SHEET_ORIENTATION)
        }

    suspend fun setAnswerSheetOrientation(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ANSWER_SHEET_ORIENTATION] = value
        }
    }

    val defaultQuestionMarksFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_QUESTION_MARKS] ?: "5.0"
        }

    val markRecognitionLimitMinFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MARK_RECOGNITION_LIMIT_MIN] ?: "0.0"
        }

    val markRecognitionLimitMaxFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.MARK_RECOGNITION_LIMIT_MAX] ?: "10.0"
        }

    val autoCropFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_CROP] ?: true
        }

    val showAnnotationsFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.SHOW_ANNOTATIONS] ?: true
        }

    suspend fun setDefaultQuestionMarks(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_QUESTION_MARKS] = value
        }
    }

    suspend fun setMarkRecognitionLimitMin(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MARK_RECOGNITION_LIMIT_MIN] = value
        }
    }

    suspend fun setMarkRecognitionLimitMax(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MARK_RECOGNITION_LIMIT_MAX] = value
        }
    }

    suspend fun setAutoCrop(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CROP] = enabled
        }
    }

    suspend fun setShowAnnotations(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_ANNOTATIONS] = enabled
        }
    }
}
