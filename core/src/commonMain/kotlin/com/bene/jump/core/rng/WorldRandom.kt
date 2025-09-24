package com.bene.jump.core.rng

class WorldRandom(seed: Long) {
    private var state: ULong = seed.toULong()

    fun nextLong(): Long {
        state += PHI
        var z = state
        z = (z xor (z shr 30)) * MULT1
        z = (z xor (z shr 27)) * MULT2
        z = z xor (z shr 31)
        return z.toLong()
    }

    fun nextFloat(): Float {
        val value = (nextLong().ushr(40) and 0xFFFFFF).toInt()
        return value / (1 shl 24).toFloat()
    }

    fun nextFloat(range: Float): Float = nextFloat() * range

    fun nextRange(
        min: Float,
        max: Float,
    ): Float {
        return min + nextFloat() * (max - min)
    }

    fun nextInt(bound: Int): Int {
        val r = nextLong().ushr(1)
        return (r % bound.toLong()).toInt()
    }

    fun shuffle(
        value: Float,
        jitter: Float,
    ): Float {
        return value + nextRange(-jitter, jitter)
    }

    fun nextBoolean(): Boolean = (nextLong() and 1L) == 0L

    fun reseed(seed: Long) {
        state = seed.toULong()
    }

    private fun Long.ushr(bits: Int): Long = (this.toULong() shr bits).toLong()

    companion object {
        private val PHI = 0x9E3779B97F4A7C15uL
        private val MULT1 = 0xBF58476D1CE4E5B9uL
        private val MULT2 = 0x94D049BB133111EBuL
    }
}
