package com.bene.jump.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jump_settings")

data class Settings(
    val musicEnabled: Boolean = true,
    val tiltSensitivity: Float = 1f,
    val darkTheme: Boolean = true,
    val multiplayerEnabled: Boolean = false,
    val wsUrl: String = DEFAULT_WS_URL,
    val inputBatchEnabled: Boolean = true,
    val interpolationDelayMs: Int = DEFAULT_INTERPOLATION_DELAY_MS,
) {
    companion object {
        const val DEFAULT_WS_URL: String = "wss://rt.localhost.example.com/v1/ws"
        const val DEFAULT_INTERPOLATION_DELAY_MS: Int = 100
    }
}

class SettingsStore(private val context: Context) {
    private val musicKey = booleanPreferencesKey("music")
    private val tiltKey = floatPreferencesKey("tilt")
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val multiplayerKey = booleanPreferencesKey("mp_enabled")
    private val wsUrlKey = stringPreferencesKey("mp_ws_url")
    private val inputBatchKey = booleanPreferencesKey("mp_input_batch")
    private val interpolationDelayKey = intPreferencesKey("mp_interp_delay")

    val settings: Flow<Settings> =
        context.settingsDataStore.data.map { prefs ->
            Settings(
                musicEnabled = prefs[musicKey] ?: true,
                tiltSensitivity = prefs[tiltKey] ?: 1f,
                darkTheme = prefs[darkThemeKey] ?: true,
                multiplayerEnabled = prefs[multiplayerKey] ?: false,
                wsUrl = prefs[wsUrlKey] ?: Settings.DEFAULT_WS_URL,
                inputBatchEnabled = prefs[inputBatchKey] ?: true,
                interpolationDelayMs =
                    prefs[interpolationDelayKey] ?: Settings.DEFAULT_INTERPOLATION_DELAY_MS,
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

    suspend fun setMultiplayerEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[multiplayerKey] = enabled
        }
    }

    suspend fun setWsUrl(url: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[wsUrlKey] = url
        }
    }

    suspend fun setInputBatchEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[inputBatchKey] = enabled
        }
    }

    suspend fun setInterpolationDelay(ms: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[interpolationDelayKey] = ms
        }
    }
}
