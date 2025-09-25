package com.bene.jump.net

import com.bene.jump.data.NetPrefs
import com.bene.jump.data.NetPrefsStore
import com.bene.jump.net.api.CreateRoomRequest
import com.bene.jump.net.api.CreateRoomResponse
import com.bene.jump.net.api.JoinRoomRequest
import com.bene.jump.net.api.JoinRoomResponse
import com.bene.jump.net.api.ReadyRequest
import com.bene.jump.net.api.RoomsApi
import com.bene.jump.net.api.StartRoomRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class NetRepository(
    private val roomsApi: RoomsApi,
    private val controller: NetController,
    private val prefsStore: NetPrefsStore,
    private val scope: CoroutineScope,
) {
    data class ConnectionConfig(
        val playerName: String,
        val clientVersion: String,
        val useInputBatch: Boolean,
        val interpolationDelayMs: Int,
    )

    val state: StateFlow<NetState> = controller.state

    @Volatile
    private var latestPrefs: NetPrefs = NetPrefs()

    private var activeRoomId: String? = null
    private var activeWsUrl: String? = null
    private var activeWsToken: String? = null
    private var lastConnectionConfig: ConnectionConfig? = null

    init {
        scope.launch {
            prefsStore.prefs.collect { prefs -> latestPrefs = prefs }
        }
    }

    suspend fun createRoom(
        region: String?,
        maxPlayers: Int?,
        mode: String?,
        connection: ConnectionConfig,
    ): CreateRoomResponse {
        val response =
            roomsApi.createRoom(
                CreateRoomRequest(
                    name = connection.playerName,
                    region = region,
                    maxPlayers = maxPlayers,
                    mode = mode,
                ),
            )
        activeRoomId = response.roomId
        activeWsUrl = response.wsUrl
        activeWsToken = response.wsToken
        lastConnectionConfig = connection
        startSocket(response.roomId, response.wsUrl, response.wsToken, connection)
        return response
    }

    suspend fun joinRoom(
        roomId: String,
        connection: ConnectionConfig,
    ): JoinRoomResponse {
        val response = roomsApi.joinRoom(roomId, JoinRoomRequest(connection.playerName))
        activeRoomId = response.roomId
        activeWsUrl = response.wsUrl
        activeWsToken = response.wsToken
        lastConnectionConfig = connection
        startSocket(response.roomId, response.wsUrl, response.wsToken, connection)
        return response
    }

    suspend fun leaveRoom(clearCredentials: Boolean = true) {
        val room = activeRoomId ?: return
        runCatching { roomsApi.leaveRoom(room) }
        controller.stop()
        if (clearCredentials) {
            prefsStore.clear()
        }
        activeRoomId = null
        activeWsUrl = null
        activeWsToken = null
    }

    suspend fun setReady(ready: Boolean) {
        val room = activeRoomId ?: return
        roomsApi.setReady(room, ReadyRequest(ready))
    }

    suspend fun startRoom(countdownSec: Int?) {
        val room = activeRoomId ?: return
        roomsApi.startRoom(room, StartRoomRequest(countdownSec))
    }

    suspend fun refreshSocket(): Boolean {
        val room = activeRoomId ?: return false
        val config = lastConnectionConfig ?: return false
        val response = roomsApi.joinRoom(room, JoinRoomRequest(config.playerName))
        activeWsUrl = response.wsUrl
        activeWsToken = response.wsToken
        startSocket(response.roomId, response.wsUrl, response.wsToken, config)
        return true
    }

    fun stop() {
        controller.stop()
    }

    private fun startSocket(
        roomId: String,
        wsUrl: String,
        wsToken: String,
        connection: ConnectionConfig,
    ) {
        val finalUrl = buildSocketUrl(wsUrl, wsToken)
        val prefsSnapshot = latestPrefs
        controller.start(
            NetController.Config(
                roomId = roomId,
                wsUrl = finalUrl,
                playerName = connection.playerName,
                clientVersion = connection.clientVersion,
                resumeToken = prefsSnapshot.resumeToken,
                playerId = prefsSnapshot.playerId,
                lastAckTick = prefsSnapshot.lastAckTick,
                useInputBatch = connection.useInputBatch,
                interpolationDelayMs = connection.interpolationDelayMs,
            ),
        )
    }

    private fun buildSocketUrl(base: String, token: String): String {
        if (base.contains("token=")) return base
        val separator = if (base.contains('?')) '&' else '?'
        val encoded = URLEncoder.encode(token, StandardCharsets.UTF_8.name())
        return buildString {
            append(base)
            append(separator)
            append("token=")
            append(encoded)
        }
    }
}
