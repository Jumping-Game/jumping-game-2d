package com.bene.jump

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.bene.jump.analytics.AnalyticsRegistry
import com.bene.jump.analytics.LogAnalyticsService
import com.bene.jump.core.model.SessionPhase
import com.bene.jump.core.net.RoomState
import com.bene.jump.data.NetPrefsStore
import com.bene.jump.data.Settings
import com.bene.jump.data.SettingsStore
import com.bene.jump.input.TiltInput
import com.bene.jump.input.TouchInput
import com.bene.jump.net.api.RoomsApi
import com.bene.jump.ui.DevSettingsScreen
import com.bene.jump.ui.GameScreen
import com.bene.jump.ui.LobbyRoomScreen
import com.bene.jump.ui.LobbySetupScreen
import com.bene.jump.ui.MenuScreen
import com.bene.jump.ui.SettingsScreen
import com.bene.jump.ui.theme.JumpTheme
import com.bene.jump.vm.GameViewModel
import com.bene.jump.vm.LobbyStatus
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    private lateinit var settingsStore: SettingsStore
    private lateinit var tiltInput: TiltInput
    private lateinit var touchInput: TouchInput
    private lateinit var netPrefsStore: NetPrefsStore
    private lateinit var roomsApi: RoomsApi
    private lateinit var gameViewModel: GameViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsStore(applicationContext)
        tiltInput = TiltInput(this)
        touchInput = TouchInput()
        netPrefsStore = NetPrefsStore(applicationContext)
        roomsApi = RoomsApi(OkHttpClient.Builder().retryOnConnectionFailure(true).build())
        AnalyticsRegistry.service = LogAnalyticsService()
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return GameViewModel(
                        settingsStore = settingsStore,
                        tiltInput = tiltInput,
                        touchInput = touchInput,
                        roomsApi = roomsApi,
                        netPrefsStore = netPrefsStore,
                        seed = 42L,
                    ) as T
                }
            }
        gameViewModel = ViewModelProvider(this, factory)[GameViewModel::class.java]

        setContent {
            val state by gameViewModel.state.collectAsState()
            val netState by gameViewModel.netState.collectAsState()
            val settings by settingsStore.settings.collectAsState(initial = Settings())
            val lobbyState by gameViewModel.lobbyState.collectAsState()
            var screen by remember { mutableStateOf(Screen.Menu) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(lobbyState.status) {
                if (screen == Screen.LobbySetup && lobbyState.status == LobbyStatus.InRoom) {
                    screen = Screen.LobbyRoom
                }
                if (screen == Screen.LobbyRoom && lobbyState.status == LobbyStatus.Idle && netState.roomId == null) {
                    screen = Screen.Menu
                }
            }
            LaunchedEffect(netState.roomState) {
                if (screen == Screen.LobbyRoom && netState.roomState == RoomState.RUNNING) {
                    screen = Screen.Game
                } else if (screen == Screen.Game && netState.roomState == RoomState.FINISHED) {
                    screen = Screen.Menu
                }
            }

            JumpTheme(darkTheme = settings.darkTheme) {
                when (screen) {
                    Screen.Menu ->
                        MenuScreen(
                            canResume = state.phase == SessionPhase.Paused,
                            onPlay = {
                                screen = Screen.LobbySetup
                            },
                            onResume = {
                                screen = Screen.Game
                                gameViewModel.togglePause()
                            },
                            onSettings = { screen = Screen.Settings },
                            onDevSettings = { screen = Screen.DevSettings },
                        )
                    Screen.Game ->
                        GameScreen(
                            state = state,
                            onTogglePause = { gameViewModel.togglePause() },
                            onRestart = { gameViewModel.restart(seed = 42L) },
                            onTouchChange = { touchInput.onTouch(it) },
                            onBackToMenu = {
                                gameViewModel.leaveLobby()
                                screen = Screen.Menu
                            },
                        )
                    Screen.LobbySetup ->
                        LobbySetupScreen(
                            lobbyState = lobbyState,
                            onCreateRoom = { name, region, maxPlayers, mode ->
                                gameViewModel.createRoom(name, region, maxPlayers, mode)
                            },
                            onJoinRoom = { roomId, name -> gameViewModel.joinRoom(roomId, name) },
                            onBack = {
                                gameViewModel.leaveLobby()
                                screen = Screen.Menu
                            },
                        )
                    Screen.LobbyRoom ->
                        LobbyRoomScreen(
                            lobbyState = lobbyState,
                            netState = netState,
                            onSelectCharacter = { characterId -> gameViewModel.selectCharacter(characterId) },
                            onReadyChanged = { ready -> gameViewModel.setReady(ready) },
                            onStart = { countdown -> gameViewModel.requestStart(countdown) },
                            onLeave = {
                                gameViewModel.leaveLobby()
                                screen = Screen.Menu
                            },
                        )
                    Screen.Settings ->
                        SettingsScreen(
                            settings = settings,
                            onMusicChanged = { enabled ->
                                scope.launch { settingsStore.setMusicEnabled(enabled) }
                            },
                            onThemeChanged = { enabled ->
                                scope.launch { settingsStore.setDarkTheme(enabled) }
                            },
                            onTiltChanged = { value ->
                                scope.launch { settingsStore.setTiltSensitivity(value) }
                            },
                            onBack = { screen = Screen.Menu },
                        )
                    Screen.DevSettings ->
                        DevSettingsScreen(
                            settings = settings,
                            onMultiplayerEnabled = { enabled ->
                                scope.launch { settingsStore.setMultiplayerEnabled(enabled) }
                            },
                            onWsUrlChanged = { url ->
                                scope.launch { settingsStore.setWsUrl(url) }
                            },
                            onInputBatchChanged = { enabled ->
                                scope.launch { settingsStore.setInputBatchEnabled(enabled) }
                            },
                            onInterpolationDelayChanged = { value ->
                                scope.launch { settingsStore.setInterpolationDelay(value) }
                            },
                            onBack = { screen = Screen.Menu },
                        )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tiltInput.start()
    }

    override fun onPause() {
        super.onPause()
        tiltInput.stop()
        touchInput.onTouch(false)
    }

    private enum class Screen {
        Menu,
        Game,
        LobbySetup,
        LobbyRoom,
        Settings,
        DevSettings,
    }
}
