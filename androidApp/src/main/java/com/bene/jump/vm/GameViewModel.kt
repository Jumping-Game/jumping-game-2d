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
import com.bene.jump.data.Settings
import com.bene.jump.data.SettingsStore
import com.bene.jump.input.TiltInput
import com.bene.jump.input.TouchInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GameViewModel(
    private val settingsStore: SettingsStore,
    private val tiltInput: TiltInput,
    private val touchInput: TouchInput,
    seed: Long = 0L,
) : ViewModel() {
    private val config = GameConfig()
    private val session = GameSession(config, seed)
    private val loop = FixedTimestepLoop()
    private val input = GameInput()
    private val platformScratch = ArrayList<PlatformUi>(config.platformBuffer)

    private val mutableState = MutableStateFlow(buildState())
    val state: StateFlow<GameUiState> = mutableState.asStateFlow()

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
        )
    }

    private suspend fun runLoop() {
        var lastTime = SystemClock.elapsedRealtimeNanos()
        while (isActive) {
            val now = SystemClock.elapsedRealtimeNanos()
            val delta = ((now - lastTime).coerceAtMost(50_000_000L)) / 1_000_000_000f
            lastTime = now
            loop.advance(delta) { dt ->
                input.tilt = tiltInput.tilt.value
                session.step(input, dt)
                input.pauseRequested = false
            }
            emitState()
            delay(16L)
        }
    }

    private fun applySettings(settings: Settings) {
        tiltInput.setSensitivity(settings.tiltSensitivity)
        if (settings.musicEnabled && session.phase == SessionPhase.Running) {
            AnalyticsRegistry.service.track("music_enabled")
        }
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
}
