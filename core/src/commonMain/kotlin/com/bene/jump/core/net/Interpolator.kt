package com.bene.jump.core.net

import kotlin.math.max
import kotlin.math.min

class Interpolator(
    private val bufferSize: Int = 64,
    private val maxExtrapolationMs: Long = 100,
) {
    private val buffers = mutableMapOf<String, StateBuffer>()

    fun push(playerId: String, sampleTimeMs: Long, state: CompactState) {
        val buffer = buffers.getOrPut(playerId) { StateBuffer(bufferSize) }
        buffer.push(sampleTimeMs, state)
    }

    fun sample(playerId: String, renderTimeMs: Long, out: CompactState): Boolean {
        val buffer = buffers[playerId] ?: return false
        return buffer.sample(renderTimeMs, maxExtrapolationMs, out)
    }

    fun prune(renderTimeMs: Long, keepWindowMs: Long) {
        val cutoff = renderTimeMs - keepWindowMs
        buffers.values.forEach { it.prune(cutoff) }
    }

    fun remove(playerId: String) {
        buffers.remove(playerId)
    }

    fun clear() {
        buffers.clear()
    }

    private class StateBuffer(capacity: Int) {
        private val mask = capacity - 1
        private val times = LongArray(capacity)
        private val states = Array(capacity) { CompactState() }
        private var head = 0
        private var size = 0

        init {
            require(capacity > 0 && (capacity and mask) == 0) {
                "capacity must be power of two"
            }
        }

        fun push(sampleTimeMs: Long, state: CompactState) {
            val index = (head + size) and mask
            states[index].copyFrom(state)
            times[index] = sampleTimeMs
            if (size == states.size) {
                head = (head + 1) and mask
            } else {
                size += 1
            }
        }

        fun sample(renderTimeMs: Long, maxExtrapolationMs: Long, out: CompactState): Boolean {
            if (size == 0) return false
            val newestIndex = (head + size - 1) and mask
            val newestTime = times[newestIndex]
            if (renderTimeMs >= newestTime) {
                val delta = renderTimeMs - newestTime
                if (delta > maxExtrapolationMs) {
                    out.copyFrom(states[newestIndex])
                    return false
                }
                val state = states[newestIndex]
                out.copyFrom(state)
                val dtSeconds = delta / 1000f
                out.x += state.vx * dtSeconds
                out.y += state.vy * dtSeconds
                return true
            }

            var index = head
            for (i in 0 until size) {
                val sampleIndex = (head + i) and mask
                val sampleTime = times[sampleIndex]
                if (sampleTime >= renderTimeMs) {
                    if (i == 0) {
                        out.copyFrom(states[sampleIndex])
                        return true
                    }
                    val prevIndex = (sampleIndex - 1) and mask
                    val prevTime = times[prevIndex]
                    val t = if (sampleTime == prevTime) 0f else ((renderTimeMs - prevTime).toFloat() / (sampleTime - prevTime).toFloat())
                    val prev = states[prevIndex]
                    val next = states[sampleIndex]
                    interpolate(prev, next, t, out)
                    return true
                }
                index = sampleIndex
            }

            val oldestIndex = head
            out.copyFrom(states[oldestIndex])
            return true
        }

        fun prune(cutoffMs: Long) {
            while (size > 0) {
                val time = times[head]
                if (time >= cutoffMs) break
                head = (head + 1) and mask
                size -= 1
            }
        }

        private fun interpolate(a: CompactState, b: CompactState, t: Float, out: CompactState) {
            val clamped = min(1f, max(0f, t))
            out.x = lerp(a.x, b.x, clamped)
            out.y = lerp(a.y, b.y, clamped)
            out.vx = lerp(a.vx, b.vx, clamped)
            out.vy = lerp(a.vy, b.vy, clamped)
        }

        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    }
}
