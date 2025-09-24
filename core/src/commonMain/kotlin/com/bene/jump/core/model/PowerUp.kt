package com.bene.jump.core.model

import com.bene.jump.core.math.Rect
import com.bene.jump.core.math.Vec2

data class PowerUp(
    val position: Vec2 = Vec2(),
    val bounds: Rect = Rect(position, 0.3f, 0.3f),
    val type: PowerUpType = PowerUpType.None,
)

enum class PowerUpType {
    None,
    Spring,
    Jetpack,
}
