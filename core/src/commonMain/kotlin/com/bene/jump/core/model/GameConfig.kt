package com.bene.jump.core.model

data class GameConfig(
    val worldWidth: Float = 6f,
    val worldHeightVisible: Float = 10f,
    val gravity: Float = -30f,
    val jumpVelocity: Float = 16f,
    val horizontalAcceleration: Float = 50f,
    val horizontalFriction: Float = 12f,
    val maxHorizontalSpeed: Float = 9f,
    val platformSpacingMin: Float = 1.4f,
    val platformSpacingMax: Float = 2.4f,
    val platformWidth: Float = 1.6f,
    val playerSize: Float = 0.6f,
    val platformBuffer: Int = 20,
    val deathHeight: Float = 12f,
)
