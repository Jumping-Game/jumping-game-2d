package com.bene.jump.core.model

import com.bene.jump.core.math.Rect
import com.bene.jump.core.math.Vec2

data class Platform(
    val position: Vec2 = Vec2(),
    val bounds: Rect = Rect(position, 0.8f, 0.15f),
    var spawnedTick: Long = 0L,
)
