package com.bene.jump.vm

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bene.jump.analytics.AnalyticsRegistry
import com.bene.jump.core.engine.FixedTimestepLoop
import com.bene.jump.core.model.GameConfig
import com.bene.jump.core.model.GameInput
import com.bene.jump.core.model.SessionPhase
import com.bene.jump.core.sim.GameSession
import com.bene.jump.core.net.RoomState
import com.bene.jump.data.NetPrefsStore
import com.bene.jump.data.Settings
import com.bene.jump.data.SettingsStore
import com.bene.jump.input.TiltInput
import com.bene.jump.input.TouchInput
import com.bene.jump.net.ConnectionPhase
import com.bene.jump.net.NetController
import com.bene.jump.net.NetRepository
import com.bene.jump.net.NetState
import com.bene.jump.net.RtSocket
import com.bene.jump.net.api.RoomsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GameViewModel(
    private val settingsStore: SettingsStore,
    private val tiltInput: TiltInput,
    private val touchInput: TouchInput,
    private val roomsApi: RoomsApi,
    private val netPrefsStore: NetPrefsStore,
    seed: Long = 0L,
) : ViewModel() {
    private val config = GameConfig()
    private val session = GameSession(config, seed)
    private val loop = FixedTimestepLoop()
    private val input = GameInput()
    private val platformScratch = ArrayList<PlatformUi>(config.platformBuffer)
    private val remotePlayerScratch = ArrayList<PlayerUi>(4)
    private val netController =
        NetController(
            session = session,
            socket = RtSocket(),
            scope = viewModelScope,
            prefsStore = netPrefsStore,
        )
    private val netRepository = NetRepository(roomsApi, netController, netPrefsStore, viewModelScope)

    private var lastSettings: Settings? = null
    private var latestNetState: NetState = NetState()
    private var currentPlayerName: String = DEFAULT_PLAYER_NAME
    private var currentRoomId: String? = null
    private var desiredCharacterId: String = LobbyUiState.DEFAULT_CHARACTERS.first()

    private val mutableState = MutableStateFlow(buildState())
    val state: StateFlow<GameUiState> = mutableState.asStateFlow()

    val netState: StateFlow<NetState> = netRepository.state

    private val lobbyStateFlow = MutableStateFlow(LobbyUiState())
    val lobbyState: StateFlow<LobbyUiState> = lobbyStateFlow.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collectLatest { settings ->
                applySettings(settings)
            }
        }
        viewModelScope.launch {
            touchInput.pressed.collectLatest { pressed ->
                input.touchDown = pressed
            }
        }
        viewModelScope.launch { runLoop() }
        viewModelScope.launch { collectNetState() }
    }

    private fun buildState(): GameUiState {
        val world = session.world
        return GameUiState(
            phase = session.phase,
            score = world.score,
            worldWidth = config.worldWidth,
            visibleHeight = config.worldHeightVisible,
            cameraY = world.camera.position.y,
            player =
                PlayerUi(
                    x = world.player.position.x,
                    y = world.player.position.y,
                    width = world.player.bounds.halfWidth * 2f,
                    height = world.player.bounds.halfHeight * 2f,
                ),
            platforms = emptyList(),
            remotePlayers = emptyList(),
        )
    }

    private suspend fun CoroutineScope.runLoop() {
        var lastTime = SystemClock.elapsedRealtimeNanos()
        while (isActive) {
            val now = SystemClock.elapsedRealtimeNanos()
            val delta = ((now - lastTime).coerceAtMost(50_000_000L)) / 1_000_000_000f
            lastTime = now
            loop.advance(delta) { dt ->
                input.tilt = tiltInput.tilt.value
                netController.step(input, dt)
                if (latestNetState.connectionPhase != ConnectionPhase.Running) {
                    session.step(input, dt)
                }
                input.pauseRequested = false
            }
            emitState()
            delay(16L)
        }
    }

    private suspend fun collectNetState() {
        netState.collectLatest { state ->
            latestNetState = state
            val localPlayer = state.playerId?.let { id -> state.lobby.firstOrNull { it.id == id } }
            localPlayer?.characterId?.let { desiredCharacterId = it }
            if (state.roomId != null) {
                currentRoomId = state.roomId
            }
            val status =
                when {
                    state.roomId != null && state.connected -> LobbyStatus.InRoom
                    state.connectionPhase == ConnectionPhase.Finished || state.roomState == RoomState.FINISHED -> LobbyStatus.Idle
                    state.connectionPhase == ConnectionPhase.Idle && state.roomId == null -> LobbyStatus.Idle
                    else -> lobbyStateFlow.value.status
                }
            lobbyStateFlow.update { previous ->
                previous.copy(
                    status = status,
                    roomId = state.roomId ?: currentRoomId,
                    playerName = currentPlayerName,
                    role = state.role,
                    roomState = state.roomState,
                    players = state.lobby,
                    maxPlayers = when {
                        state.lobbyMaxPlayers > 0 -> state.lobbyMaxPlayers
                        previous.maxPlayers > 0 -> previous.maxPlayers
                        else -> 0
                    },
                    countdown = state.countdown,
                    selectedCharacter = localPlayer?.characterId ?: previous.selectedCharacter,
                    ready = localPlayer?.ready ?: previous.ready,
                    errorMessage = state.lastError ?: previous.errorMessage,
                )
            }
        }
    }

    private fun applySettings(settings: Settings) {
        tiltInput.setSensitivity(settings.tiltSensitivity)
        if (settings.musicEnabled && session.phase == SessionPhase.Running) {
            AnalyticsRegistry.service.track("music_enabled")
        }
        lastSettings = settings
    }

    private fun emitState() {
        val world = session.world
        platformScratch.clear()
        for (platform in world.activePlatforms) {
            platformScratch.add(
                PlatformUi(
                    x = platform.position.x,
                    y = platform.position.y,
                    width = platform.bounds.halfWidth * 2f,
                    height = platform.bounds.halfHeight * 2f,
                ),
            )
        }
        remotePlayerScratch.clear()
        netController.sampleRemotePlayers(SystemClock.elapsedRealtime(), remotePlayerScratch)
        mutableState.value =
            GameUiState(
                phase = session.phase,
                score = world.score,
                worldWidth = config.worldWidth,
                visibleHeight = config.worldHeightVisible,
                cameraY = world.camera.position.y,
                player =
                    PlayerUi(
                        x = world.player.position.x,
                        y = world.player.position.y,
                        width = world.player.bounds.halfWidth * 2f,
                        height = world.player.bounds.halfHeight * 2f,
                    ),
                platforms = platformScratch.toList(),
                remotePlayers = remotePlayerScratch.toList(),
            )
        if (session.phase == SessionPhase.GameOver) {
            AnalyticsRegistry.service.track("game_over", mapOf("score" to world.score))
        }
    }

    fun togglePause() {
        input.pauseRequested = true
    }

    fun restart(seed: Long = session.world.seed) {
        session.restart(seed)
        AnalyticsRegistry.service.track("restart", mapOf("seed" to seed))
    }

    fun createRoom(
        name: String,
        region: String?,
        maxPlayers: Int?,
        mode: String?,
    ) {
        val finalName = name.ifBlank { DEFAULT_PLAYER_NAME }
        currentPlayerName = finalName
        lobbyStateFlow.update {
            it.copy(
                status = LobbyStatus.Creating,
                loading = true,
                playerName = finalName,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            try {
                val connection = buildConnectionConfig(finalName)
                val response = netRepository.createRoom(region, maxPlayers, mode, connection)
                currentRoomId = response.roomId
                lobbyStateFlow.update {
                    it.copy(
                        status = LobbyStatus.InRoom,
                        loading = false,
                        roomId = response.roomId,
                        maxPlayers = response.maxPlayers,
                        errorMessage = null,
                    )
                }
                submitCharacterSelection(desiredCharacterId)
            } catch (t: Throwable) {
                lobbyStateFlow.update {
                    it.copy(
                        status = LobbyStatus.Error,
                        loading = false,
                        errorMessage = t.message ?: "Unable to create room",
                    )
                }
            }
        }
    }

    fun joinRoom(
        roomId: String,
        name: String,
    ) {
        val finalName = name.ifBlank { DEFAULT_PLAYER_NAME }
        currentPlayerName = finalName
        lobbyStateFlow.update {
            it.copy(
                status = LobbyStatus.Joining,
                loading = true,
                playerName = finalName,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            try {
                val connection = buildConnectionConfig(finalName)
                val response = netRepository.joinRoom(roomId, connection)
                currentRoomId = response.roomId
                lobbyStateFlow.update {
                    it.copy(
                        status = LobbyStatus.InRoom,
                        loading = false,
                        roomId = response.roomId,
                        errorMessage = null,
                    )
                }
                submitCharacterSelection(desiredCharacterId)
            } catch (t: Throwable) {
                lobbyStateFlow.update {
                    it.copy(
                        status = LobbyStatus.Error,
                        loading = false,
                        errorMessage = t.message ?: "Unable to join room",
                    )
                }
            }
        }
    }

    fun leaveLobby() {
        lobbyStateFlow.update {
            LobbyUiState(
                status = LobbyStatus.Idle,
                playerName = currentPlayerName,
                selectedCharacter = desiredCharacterId,
            )
        }
        currentRoomId = null
        latestNetState = NetState()
        viewModelScope.launch { netRepository.leaveRoom(clearCredentials = false) }
    }

    fun selectCharacter(characterId: String) {
        desiredCharacterId = characterId
        lobbyStateFlow.update { it.copy(selectedCharacter = characterId, errorMessage = null) }
        viewModelScope.launch { submitCharacterSelection(characterId) }
    }

    fun setReady(ready: Boolean) {
        lobbyStateFlow.update { it.copy(ready = ready, errorMessage = null) }
        viewModelScope.launch {
            runCatching { netRepository.setReady(ready) }.onFailure { throwable ->
                lobbyStateFlow.update { it.copy(errorMessage = throwable.message ?: "Failed to update ready state") }
            }
        }
    }

    fun requestStart(countdownSec: Int?) {
        viewModelScope.launch {
            runCatching { netRepository.startRoom(countdownSec) }.onFailure { throwable ->
                lobbyStateFlow.update { it.copy(errorMessage = throwable.message ?: "Failed to start room") }
            }
        }
    }

    fun refreshSocket() {
        viewModelScope.launch { netRepository.refreshSocket() }
    }

    private suspend fun submitCharacterSelection(characterId: String) {
        if (characterId.isBlank()) return
        runCatching { netRepository.setCharacter(characterId) }.onFailure { throwable ->
            lobbyStateFlow.update { it.copy(errorMessage = throwable.message ?: "Failed to set character") }
        }
    }

    private fun buildConnectionConfig(playerName: String): NetRepository.ConnectionConfig {
        val settings = lastSettings ?: Settings()
        return NetRepository.ConnectionConfig(
            playerName = playerName,
            clientVersion = CLIENT_VERSION,
            useInputBatch = settings.inputBatchEnabled,
            interpolationDelayMs = settings.interpolationDelayMs,
        )
    }

    override fun onCleared() {
        super.onCleared()
        netRepository.stop()
    }

    companion object {
        private const val CLIENT_VERSION = "android-dev"
        private const val DEFAULT_PLAYER_NAME = "Player"
    }
}
