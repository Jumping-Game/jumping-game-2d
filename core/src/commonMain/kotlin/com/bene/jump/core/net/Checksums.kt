package com.bene.jump.core.net

import com.bene.jump.core.model.World
import kotlin.math.min

private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL
private const val FNV_PRIME = 0x100000001b3L
private const val MASK_64 = -0x1L

object Checksums {
    fun compute(
        state: CompactState,
        world: World,
        platformSample: Int = 4,
    ): Long {
        var hash = FNV_OFFSET_BASIS
        hash = mixFloat(hash, state.x)
        hash = mixFloat(hash, state.y)
        hash = mixFloat(hash, state.vx)
        hash = mixFloat(hash, state.vy)
        hash = mixLong(hash, world.tick.toLong())
        val platforms = world.activePlatforms
        val limit = if (platformSample <= 0) 0 else min(platformSample, platforms.size)
        for (i in 0 until limit) {
            val platform = platforms[i]
            hash = mixFloat(hash, platform.position.x)
            hash = mixFloat(hash, platform.position.y)
            hash = mixFloat(hash, platform.bounds.halfWidth)
            hash = mixFloat(hash, platform.bounds.halfHeight)
        }
        return hash
    }

    fun computeHex(
        state: CompactState,
        world: World,
        platformSample: Int = 4,
    ): String {
        return compute(state, world, platformSample).toULong().toString(16)
    }

    private fun mixFloat(
        hash: Long,
        value: Float,
    ): Long {
        return mixInt(hash, value.toRawBits())
    }

    private fun mixInt(
        hash: Long,
        value: Int,
    ): Long {
        return mixLong(hash, value.toLong() and 0xFFFFFFFFL)
    }

    private fun mixLong(
        hash: Long,
        value: Long,
    ): Long {
        var h = hash
        var v = value
        for (i in 0 until 8) {
            h = mixByte(h, (v and 0xFF).toInt())
            v = v shr 8
        }
        return h
    }

    private fun mixByte(
        hash: Long,
        byte: Int,
    ): Long {
        val v = byte.toLong() and 0xFF
        return ((hash xor v) * FNV_PRIME) and MASK_64
    }
}
