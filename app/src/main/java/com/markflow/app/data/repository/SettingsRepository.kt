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
}
