package com.bene.jump.vm

import com.bene.jump.core.model.SessionPhase

data class GameUiState(
    val phase: SessionPhase = SessionPhase.Paused,
    val score: Float = 0f,
    val worldWidth: Float = 6f,
    val visibleHeight: Float = 10f,
    val cameraY: Float = 0f,
    val player: PlayerUi = PlayerUi(),
    val platforms: List<PlatformUi> = emptyList(),
    val remotePlayers: List<PlayerUi> = emptyList(),
)

data class PlayerUi(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0.6f,
    val height: Float = 0.6f,
)

data class PlatformUi(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)
