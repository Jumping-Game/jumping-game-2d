package com.bene.jump.core.net

import com.bene.jump.core.model.GameInput

class ClientPredBuffer(capacity: Int = 120) {
    private val frames: Array<GameInput> = Array(capacity) { GameInput() }
    private val ticks: LongArray = LongArray(capacity)
    private var head = 0

    fun store(
        tick: Long,
        input: GameInput,
    ) {
        val index = head % frames.size
        frames[index].tilt = input.tilt
        frames[index].touchDown = input.touchDown
        frames[index].pauseRequested = input.pauseRequested
        ticks[index] = tick
        head++
    }

    fun replay(
        fromTick: Long,
        apply: (GameInput) -> Unit,
    ) {
        for (i in frames.indices) {
            val idx = (head - frames.size + i).coerceAtLeast(0)
            val frameIndex = idx % frames.size
            if (ticks[frameIndex] >= fromTick) {
                apply(frames[frameIndex])
            }
        }
    }
}
