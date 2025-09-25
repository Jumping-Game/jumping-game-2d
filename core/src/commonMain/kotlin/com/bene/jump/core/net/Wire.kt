package com.bene.jump.core.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

const val PROTOCOL_VERSION: Int = 1

val NetworkJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

@Serializable
data class Envelope<T>(
    val type: String,
    val pv: Int = PROTOCOL_VERSION,
    val seq: Int,
    val ts: Long,
    val payload: T,
)

fun <T> encodeEnvelope(
    envelope: Envelope<T>,
    serializer: KSerializer<T>,
): String = NetworkJson.encodeToString(Envelope.serializer(serializer), envelope)

fun <T> decodeEnvelope(
    json: String,
    serializer: KSerializer<T>,
): Envelope<T> = NetworkJson.decodeFromString(Envelope.serializer(serializer), json)

// ----- Client → Server -----

@Serializable
@SerialName("join")
data class C2SJoin(
    val name: String,
    val clientVersion: String,
    val device: String? = null,
    val capabilities: Capabilities? = null,
) : C2SMessage {
    @Serializable
    data class Capabilities(
        val tilt: Boolean,
        val vibrate: Boolean,
    )
}

@Serializable
@SerialName("input")
data class C2SInput(
    val tick: Int,
    val axisX: Float,
    val jump: Boolean? = null,
    val shoot: Boolean? = null,
    val checksum: String? = null,
) : C2SMessage

@Serializable
@SerialName("input_batch")
data class C2SInputBatch(
    val startTick: Int,
    val frames: List<InputDelta>,
) : C2SMessage {
    @Serializable
    data class InputDelta(
        val d: Int,
        val axisX: Float,
        val jump: Boolean? = null,
        val shoot: Boolean? = null,
        val checksum: String? = null,
    )
}

@Serializable
@SerialName("ping")
data class C2SPing(val t0: Long) : C2SMessage

@Serializable
@SerialName("reconnect")
data class C2SReconnect(
    val playerId: String,
    val resumeToken: String,
    val lastAckTick: Int,
) : C2SMessage

// ----- Server → Client -----

@Serializable
data class NetWorldCfg(
    val worldWidth: Float,
    val platformWidth: Float,
    val platformHeight: Float,
    val gapMin: Float,
    val gapMax: Float,
    val gravity: Float,
    val jumpVy: Float,
    val springVy: Float,
    val maxVx: Float,
    val tiltAccel: Float,
)

@Serializable
data class NetDifficultyCfg(
    val gapMinStart: Float,
    val gapMinEnd: Float,
    val gapMaxStart: Float,
    val gapMaxEnd: Float,
    val springChanceStart: Float,
    val springChanceEnd: Float,
)

@Serializable
data class NetConfig(
    val tps: Int,
    val snapshotRateHz: Int,
    val maxRollbackTicks: Int,
    val inputLeadTicks: Int,
    val world: NetWorldCfg,
    val difficulty: NetDifficultyCfg,
)

@Serializable
@SerialName("welcome")
data class S2CWelcome(
    val playerId: String,
    val resumeToken: String,
    val roomId: String,
    val seed: String,
    val cfg: NetConfig,
    val featureFlags: Map<String, Boolean>? = null,
) : S2CMessage

@Serializable
@SerialName("start")
data class S2CStart(
    val startTick: Int,
    val serverTick: Int,
    val serverTimeMs: Long,
    val tps: Int,
) : S2CMessage

@Serializable
data class NetPlayer(
    val id: String,
    val x: Float? = null,
    val y: Float? = null,
    val vx: Float? = null,
    val vy: Float? = null,
    val alive: Boolean? = null,
)

@Serializable
data class NetEvent(
    val kind: String,
    val x: Float,
    val y: Float,
    val tick: Int,
)

@Serializable
@SerialName("snapshot")
data class S2CSnapshot(
    val tick: Int,
    val ackTick: Int? = null,
    val lastInputSeq: Int? = null,
    val full: Boolean,
    val players: List<NetPlayer>,
    val events: List<NetEvent>? = null,
    val stats: SnapshotStats? = null,
) : S2CMessage {
    @Serializable
    data class SnapshotStats(val droppedSnapshots: Int? = null)
}

@Serializable
@SerialName("pong")
data class S2CPong(
    val t0: Long,
    val t1: Long,
) : S2CMessage

@Serializable
@SerialName("error")
data class S2CError(
    val code: String,
    val message: String? = null,
) : S2CMessage

@Serializable
@SerialName("finish")
data class S2CFinish(
    val reason: String,
) : S2CMessage

@Serializable
@SerialName("player_presence")
data class S2CPlayerPresence(
    val id: String,
    val state: String,
) : S2CMessage

sealed interface C2SMessage

sealed interface S2CMessage

private val s2cSerializers: Map<String, KSerializer<out S2CMessage>> =
    mapOf(
        "welcome" to S2CWelcome.serializer(),
        "start" to S2CStart.serializer(),
        "snapshot" to S2CSnapshot.serializer(),
        "pong" to S2CPong.serializer(),
        "error" to S2CError.serializer(),
        "finish" to S2CFinish.serializer(),
        "player_presence" to S2CPlayerPresence.serializer(),
    )

@Serializable
private data class RawEnvelope(
    val type: String,
    val pv: Int,
    val seq: Int,
    val ts: Long,
    val payload: JsonElement,
)

fun C2SJoin.asEnvelope(
    seq: Int,
    ts: Long,
): Envelope<C2SJoin> = Envelope("join", PROTOCOL_VERSION, seq, ts, this)

fun C2SInput.asEnvelope(
    seq: Int,
    ts: Long,
): Envelope<C2SInput> = Envelope("input", PROTOCOL_VERSION, seq, ts, this)

fun C2SInputBatch.asEnvelope(
    seq: Int,
    ts: Long,
): Envelope<C2SInputBatch> = Envelope("input_batch", PROTOCOL_VERSION, seq, ts, this)

fun C2SPing.asEnvelope(
    seq: Int,
    ts: Long,
): Envelope<C2SPing> = Envelope("ping", PROTOCOL_VERSION, seq, ts, this)

fun C2SReconnect.asEnvelope(
    seq: Int,
    ts: Long,
): Envelope<C2SReconnect> = Envelope("reconnect", PROTOCOL_VERSION, seq, ts, this)

inline fun <reified T> Envelope<T>.encode(): String where T : Any, T : C2SMessage =
    encodeEnvelope(this, serializer())

inline fun <reified T> decodeEnvelope(json: String): Envelope<T> =
    NetworkJson.decodeFromString(Envelope.serializer(serializer()), json)

inline fun <reified T> serializer(): KSerializer<T> = kotlinx.serialization.serializer()

fun encodeC2S(message: C2SMessage, seq: Int, ts: Long): String =
    when (message) {
        is C2SJoin -> encodeEnvelope(Envelope("join", PROTOCOL_VERSION, seq, ts, message), C2SJoin.serializer())
        is C2SInput -> encodeEnvelope(Envelope("input", PROTOCOL_VERSION, seq, ts, message), C2SInput.serializer())
        is C2SInputBatch -> encodeEnvelope(Envelope("input_batch", PROTOCOL_VERSION, seq, ts, message), C2SInputBatch.serializer())
        is C2SPing -> encodeEnvelope(Envelope("ping", PROTOCOL_VERSION, seq, ts, message), C2SPing.serializer())
        is C2SReconnect -> encodeEnvelope(Envelope("reconnect", PROTOCOL_VERSION, seq, ts, message), C2SReconnect.serializer())
    }

fun decodeS2C(json: String): Envelope<S2CMessage>? {
    val raw = NetworkJson.decodeFromString(RawEnvelope.serializer(), json)
    val serializer = s2cSerializers[raw.type] ?: return null
    val payload = NetworkJson.decodeFromJsonElement(serializer, raw.payload)
    return Envelope(raw.type, raw.pv, raw.seq, raw.ts, payload)
}
