package com.bene.jump.core.math

data class Rect(var center: Vec2 = Vec2(), var halfWidth: Float = 0.5f, var halfHeight: Float = 0.5f) {
    fun set(other: Rect): Rect {
        center.set(other.center)
        halfWidth = other.halfWidth
        halfHeight = other.halfHeight
        return this
    }

    fun contains(point: Vec2): Boolean {
        val dx = kotlin.math.abs(point.x - center.x)
        val dy = kotlin.math.abs(point.y - center.y)
        return dx <= halfWidth && dy <= halfHeight
    }

    fun intersects(other: Rect): Boolean {
        val dx = kotlin.math.abs(center.x - other.center.x)
        if (dx > halfWidth + other.halfWidth) return false
        val dy = kotlin.math.abs(center.y - other.center.y)
        if (dy > halfHeight + other.halfHeight) return false
        return true
    }

    fun bottom(): Float = center.y - halfHeight

    fun top(): Float = center.y + halfHeight

    fun left(): Float = center.x - halfWidth

    fun right(): Float = center.x + halfWidth
}
