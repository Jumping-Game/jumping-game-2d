package com.bene.jump.core.math

data class Vec2(var x: Float = 0f, var y: Float = 0f) {
    fun set(other: Vec2): Vec2 {
        x = other.x
        y = other.y
        return this
    }

    fun set(
        nx: Float,
        ny: Float,
    ): Vec2 {
        x = nx
        y = ny
        return this
    }

    fun add(
        dx: Float,
        dy: Float,
    ): Vec2 {
        x += dx
        y += dy
        return this
    }

    fun scale(s: Float): Vec2 {
        x *= s
        y *= s
        return this
    }

    fun length(): Float = kotlin.math.sqrt(x * x + y * y)

    fun clampMagnitude(max: Float): Vec2 {
        val len = length()
        if (len > max && len > 0f) {
            val scale = max / len
            x *= scale
            y *= scale
        }
        return this
    }

    fun copyInto(target: Vec2): Vec2 {
        target.x = x
        target.y = y
        return target
    }
}
