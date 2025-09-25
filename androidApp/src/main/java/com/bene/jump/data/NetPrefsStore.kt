package com.bene.jump.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.netPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jump_net_prefs")

data class NetPrefs(
    val playerId: String? = null,
    val resumeToken: String? = null,
    val lastAckTick: Int? = null,
)

class NetPrefsStore(context: Context) {
    private val dataStore = context.netPrefsDataStore
    private val playerIdKey = stringPreferencesKey("player_id")
    private val resumeTokenKey = stringPreferencesKey("resume_token")
    private val lastAckTickKey = intPreferencesKey("last_ack_tick")

    val prefs: Flow<NetPrefs> =
        dataStore.data.map { prefs ->
            NetPrefs(
                playerId = prefs[playerIdKey],
                resumeToken = prefs[resumeTokenKey],
                lastAckTick = prefs[lastAckTickKey],
            )
        }

    suspend fun persistCredentials(
        playerId: String,
        resumeToken: String,
    ) {
        dataStore.edit { prefs ->
            prefs[playerIdKey] = playerId
            prefs[resumeTokenKey] = resumeToken
        }
    }

    suspend fun updateLastAckTick(tick: Int?) {
        dataStore.edit { prefs ->
            if (tick == null) {
                prefs.remove(lastAckTickKey)
            } else {
                prefs[lastAckTickKey] = tick
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(playerIdKey)
            prefs.remove(resumeTokenKey)
            prefs.remove(lastAckTickKey)
        }
    }
}
