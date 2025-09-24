package com.bene.jump.core.model

import com.bene.jump.core.math.Vec2

data class Camera(
    val position: Vec2 = Vec2(0f, 0f),
    val velocity: Vec2 = Vec2(0f, 0f),
    var minY: Float = 0f,
) {
    fun follow(
        targetY: Float,
        lerp: Float,
    ) {
        val desired = targetY
        position.y += (desired - position.y) * lerp
        if (position.y < minY) {
            position.y = minY
        }
    }
}
