package com.bene.jump.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jump_settings")

data class Settings(
    val musicEnabled: Boolean = true,
    val tiltSensitivity: Float = 1f,
    val darkTheme: Boolean = true,
)

class SettingsStore(private val context: Context) {
    private val musicKey = booleanPreferencesKey("music")
    private val tiltKey = floatPreferencesKey("tilt")
    private val darkThemeKey = booleanPreferencesKey("dark_theme")

    val settings: Flow<Settings> =
        context.settingsDataStore.data.map { prefs ->
            Settings(
                musicEnabled = prefs[musicKey] ?: true,
                tiltSensitivity = prefs[tiltKey] ?: 1f,
                darkTheme = prefs[darkThemeKey] ?: true,
            )
        }

    suspend fun setMusicEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[musicKey] = enabled
        }
    }

    suspend fun setTiltSensitivity(sensitivity: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[tiltKey] = sensitivity
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[darkThemeKey] = enabled
        }
    }
}
