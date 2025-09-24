package com.bene.jump

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.bene.jump.data.Settings
import com.bene.jump.data.SettingsStore
import com.bene.jump.input.TiltInput
import com.bene.jump.input.TouchInput
import com.bene.jump.ui.GameScreen
import com.bene.jump.ui.MenuScreen
import com.bene.jump.ui.SettingsScreen
import com.bene.jump.ui.theme.JumpTheme
import com.bene.jump.vm.GameViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var settingsStore: SettingsStore
    private lateinit var tiltInput: TiltInput
    private lateinit var touchInput: TouchInput
    private lateinit var gameViewModel: GameViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsStore(applicationContext)
        tiltInput = TiltInput(this)
        touchInput = TouchInput()
        AnalyticsRegistry.service = LogAnalyticsService()
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return GameViewModel(settingsStore, tiltInput, touchInput, seed = 42L) as T
                }
            }
        gameViewModel = ViewModelProvider(this, factory)[GameViewModel::class.java]

        setContent {
            val state by gameViewModel.state.collectAsState()
            val settings by settingsStore.settings.collectAsState(initial = Settings())
            var screen by remember { mutableStateOf(Screen.Menu) }
            val scope = rememberCoroutineScope()

            JumpTheme {
                when (screen) {
                    Screen.Menu ->
                        MenuScreen(
                            canResume = state.phase == SessionPhase.Paused,
                            onPlay = {
                                gameViewModel.restart(seed = 42L)
                                screen = Screen.Game
                            },
                            onResume = {
                                screen = Screen.Game
                                gameViewModel.togglePause()
                            },
                            onSettings = { screen = Screen.Settings },
                        )
                    Screen.Game ->
                        GameScreen(
                            state = state,
                            onTogglePause = { gameViewModel.togglePause() },
                            onRestart = { gameViewModel.restart(seed = 42L) },
                            onTouchChange = { touchInput.onTouch(it) },
                            onBackToMenu = { screen = Screen.Menu },
                        )
                    Screen.Settings ->
                        SettingsScreen(
                            settings = settings,
                            onMusicChanged = { enabled ->
                                scope.launch { settingsStore.setMusicEnabled(enabled) }
                            },
                            onTiltChanged = { value ->
                                scope.launch { settingsStore.setTiltSensitivity(value) }
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
        Settings,
    }
}
