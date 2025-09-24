package com.bene.jump.core.engine

class FixedTimestepLoop(
    private val step: Float = 1f / 60f,
    private val maxFrameDelta: Float = 0.25f,
) {
    private var accumulator = 0f

    fun advance(
        elapsedSeconds: Float,
        stepper: (Float) -> Unit,
    ) {
        val clamped = elapsedSeconds.coerceIn(0f, maxFrameDelta)
        accumulator += clamped
        while (accumulator >= step) {
            stepper(step)
            accumulator -= step
        }
    }

    fun reset() {
        accumulator = 0f
    }
}
