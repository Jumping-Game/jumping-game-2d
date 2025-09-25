package com.bene.jump.core.net

import com.bene.jump.core.model.World
import kotlin.math.max
import kotlin.math.sqrt

private const val INVALID_TICK = -1

class MutableInputFrame {
    var tick: Int = INVALID_TICK
    var axisX: Float = 0f
    var jump: Boolean = false
    var shoot: Boolean = false
    var checksum: String? = null

    fun set(
        tick: Int,
        axisX: Float,
        jump: Boolean,
        shoot: Boolean,
        checksum: String?,
    ) {
        this.tick = tick
        this.axisX = axisX
        this.jump = jump
        this.shoot = shoot
        this.checksum = checksum
    }

    fun clear() {
        tick = INVALID_TICK
        axisX = 0f
        jump = false
        shoot = false
        checksum = null
    }
}

fun CompactState.capture(world: World) {
    val player = world.player
    set(
        x = player.position.x,
        y = player.position.y,
        vx = player.velocity.x,
        vy = player.velocity.y,
    )
}

class CompactState(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
) {
    fun set(
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
    ) {
        this.x = x
        this.y = y
        this.vx = vx
        this.vy = vy
    }

    fun copyFrom(other: CompactState) {
        x = other.x
        y = other.y
        vx = other.vx
        vy = other.vy
    }

    fun copyInto(target: CompactState) {
        target.copyFrom(this)
    }

    fun positionError(other: CompactState): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    fun velocityError(other: CompactState): Float {
        val dvx = vx - other.vx
        val dvy = vy - other.vy
        return sqrt(dvx * dvx + dvy * dvy)
    }

    fun exceedsEpsilon(
        other: CompactState,
        positionEpsilon: Float,
        velocityEpsilon: Float,
    ): Boolean = positionError(other) > positionEpsilon || velocityError(other) > velocityEpsilon
}

class ClientPredBuffer(capacity: Int) {
    val mask: Int = capacity - 1
    val inputs: Array<MutableInputFrame> = Array(capacity) { MutableInputFrame() }
    private val states: Array<CompactState> = Array(capacity) { CompactState() }
    private val stateTicks: IntArray = IntArray(capacity) { INVALID_TICK }
    val inputTicks: IntArray = IntArray(capacity) { INVALID_TICK }

    var latestInputTick: Int = INVALID_TICK
        private set

    init {
        require(capacity > 0 && (capacity and mask) == 0) {
            "capacity must be power of two"
        }
    }

    fun reset() {
        for (i in inputs.indices) {
            inputs[i].clear()
            inputTicks[i] = INVALID_TICK
            stateTicks[i] = INVALID_TICK
        }
        latestInputTick = INVALID_TICK
    }

    fun putInput(
        tick: Int,
        axisX: Float,
        jump: Boolean,
        shoot: Boolean,
        checksum: String?,
    ) {
        val index = tick and mask
        inputs[index].set(tick, axisX, jump, shoot, checksum)
        inputTicks[index] = tick
        latestInputTick = max(latestInputTick, tick)
    }

    fun putState(
        tick: Int,
        state: CompactState,
    ) {
        val index = tick and mask
        states[index].copyFrom(state)
        stateTicks[index] = tick
    }

    fun getInput(tick: Int): MutableInputFrame? {
        val index = tick and mask
        return if (inputTicks[index] == tick) inputs[index] else null
    }

    fun getState(
        tick: Int,
        out: CompactState,
    ): Boolean {
        val index = tick and mask
        return if (stateTicks[index] == tick) {
            out.copyFrom(states[index])
            true
        } else {
            false
        }
    }

    inline fun replay(
        fromTick: Int,
        toTick: Int,
        consumer: (MutableInputFrame) -> Unit,
    ) {
        if (toTick < fromTick) return
        var tick = fromTick
        while (tick <= toTick) {
            val index = tick and mask
            if (inputTicks[index] == tick) {
                consumer(inputs[index])
            }
            tick += 1
        }
    }

    fun trimBefore(tick: Int) {
        val cutoff = tick - mask
        for (i in inputs.indices) {
            if (inputTicks[i] != INVALID_TICK && inputTicks[i] < cutoff) {
                inputs[i].clear()
                inputTicks[i] = INVALID_TICK
            }
            if (stateTicks[i] != INVALID_TICK && stateTicks[i] < cutoff) {
                stateTicks[i] = INVALID_TICK
            }
        }
    }
}
