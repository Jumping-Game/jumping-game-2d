package com.bene.jump.core.net

import com.bene.jump.core.model.GameInput
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Envelope {
    abstract val tick: Long
}

@Serializable
@SerialName("c2s_input")
data class C2SInput(
    override val tick: Long,
    val inputs: List<InputFrame>,
) : Envelope()

@Serializable
@SerialName("s2c_state")
data class S2CState(
    override val tick: Long,
    val players: List<NetPlayer>,
    val events: List<NetEvent> = emptyList(),
) : Envelope()

@Serializable
@SerialName("event")
data class NetEvent(
    val type: String,
    val payload: String = "",
)

@Serializable
data class NetPlayer(
    val id: String,
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
)

@Serializable
data class InputFrame(
    val tick: Long,
    val tilt: Float,
    val touch: Boolean,
)

fun GameInput.toFrame(tick: Long): InputFrame = InputFrame(tick, tilt, touchDown)
