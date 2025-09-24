package com.bene.jump.core.model

import com.bene.jump.core.math.Rect
import com.bene.jump.core.math.Vec2

class Player(size: Float) {
    val position: Vec2 = Vec2(0f, 0f)
    val lastPosition: Vec2 = Vec2(0f, 0f)
    val velocity: Vec2 = Vec2(0f, 0f)
    val bounds: Rect = Rect(Vec2(), size * 0.5f, size * 0.5f)
    var isJumping: Boolean = false

    fun reset(
        startX: Float,
        startY: Float,
    ) {
        position.set(startX, startY)
        lastPosition.set(position)
        velocity.set(0f, 0f)
        bounds.center.set(position)
        isJumping = false
    }

    fun syncBounds() {
        bounds.center.set(position)
    }
}
