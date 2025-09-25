package com.bene.jump.net

import com.bene.jump.core.net.C2SInput
import com.bene.jump.core.net.C2SInputBatch
import com.bene.jump.core.net.C2SJoin
import com.bene.jump.core.net.C2SMessage
import com.bene.jump.core.net.C2SPing
import com.bene.jump.core.net.C2SReconnect
import com.bene.jump.core.net.Envelope
import com.bene.jump.core.net.S2CMessage
import com.bene.jump.core.net.decodeS2C
import com.bene.jump.core.net.encodeC2S
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import com.bene.jump.core.net.encodeEnvelope as encodeEnvelopeWith

class RtSocket(
    client: HttpClient? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    sealed class Event {
        object Opened : Event()

        data class Message(val envelope: Envelope<S2CMessage>) : Event()

        data class Closed(val reason: CloseReason?) : Event()

        data class Failure(val throwable: Throwable) : Event()
    }

    private val httpClient: HttpClient = client ?: defaultClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()
    private var session: io.ktor.client.plugins.websocket.ClientWebSocketSession? = null
    private var heartbeatJob: Job? = null
    private val seq = AtomicInteger(0)
    private val rateLimiter = RateLimiter(40)

    fun connect(wsUrl: String): Flow<Event> {
        return callbackFlow {
            val flowScope = this
            val job =
                scope.launch {
                    val backoff = Backoff()
                    while (isActive && !flowScope.isClosedForSend) {
                        try {
                            httpClient.webSocket(urlString = wsUrl) {
                                sessionMutex.withLock { session = this }
                                backoff.reset()
                                trySend(Event.Opened)
                                startHeartbeat()
                                var closeReason: CloseReason? = null
                                try {
                                    for (frame in incoming) {
                                        when (frame) {
                                            is Frame.Text -> {
                                                val text = frame.readText()
                                                val envelope = runCatching { decodeS2C(text) }.getOrNull()
                                                if (envelope != null) {
                                                    trySend(Event.Message(envelope))
                                                } else {
                                                    trySend(Event.Failure(IllegalStateException("Unknown message: ${'$'}text")))
                                                }
                                            }
                                            is Frame.Binary -> Unit
                                            is Frame.Ping -> send(Frame.Pong(frame.buffer))
                                            is Frame.Pong -> Unit
                                            is Frame.Close -> {
                                                closeReason = frame.readReason()
                                                break
                                            }
                                        }
                                    }
                                } catch (closed: ClosedReceiveChannelException) {
                                    if (closeReason == null) {
                                        closeReason = runCatching { this@webSocket.closeReason.await() }.getOrNull()
                                    }
                                } finally {
                                    stopHeartbeat()
                                    sessionMutex.withLock { session = null }
                                    val finalReason = closeReason ?: runCatching { this@webSocket.closeReason.await() }.getOrNull()
                                    trySend(Event.Closed(finalReason))
                                }
                                return@webSocket
                            }
                        } catch (t: Throwable) {
                            stopHeartbeat()
                            sessionMutex.withLock { session = null }
                            trySend(Event.Failure(t))
                            val delayMs = backoff.nextDelay()
                            if (!isActive || flowScope.isClosedForSend) break
                            delay(delayMs)
                        }
                    }
                }
            awaitClose {
                job.cancel()
                stopHeartbeat()
                scope.launch { close() }
            }
        }
    }

    suspend fun send(obj: Any) {
        val text =
            when (obj) {
                is String -> obj
                is C2SMessage -> encodeC2S(obj, nextSeq(), clock())
                is Envelope<*> -> encodeEnvelopeDynamic(obj)
                else -> throw IllegalArgumentException("Unsupported payload ${obj::class.java.simpleName}")
            }
        val activeSession =
            sessionMutex.withLock { session }
                ?: throw IllegalStateException("WebSocket not connected")
        if (isInputType(obj)) {
            rateLimiter.await(clock)
        }
        if (!activeSession.isActive) {
            throw IllegalStateException("WebSocket not connected")
        }
        activeSession.send(Frame.Text(text))
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob =
            scope.launch {
                while (isActive) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    runCatching { send(C2SPing(clock())) }
                }
            }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun nextSeq(): Int = seq.getAndUpdate { current -> if (current == Int.MAX_VALUE) 0 else current + 1 }

    private fun encodeEnvelopeDynamic(envelope: Envelope<*>): String {
        return when (val payload = envelope.payload) {
            is C2SJoin ->
                encodeEnvelopeWith(
                    Envelope(envelope.type, envelope.pv, envelope.seq, envelope.ts, payload),
                    C2SJoin.serializer(),
                )
            is C2SInput ->
                encodeEnvelopeWith(
                    Envelope(envelope.type, envelope.pv, envelope.seq, envelope.ts, payload),
                    C2SInput.serializer(),
                )
            is C2SInputBatch ->
                encodeEnvelopeWith(
                    Envelope(envelope.type, envelope.pv, envelope.seq, envelope.ts, payload),
                    C2SInputBatch.serializer(),
                )
            is C2SPing ->
                encodeEnvelopeWith(
                    Envelope(envelope.type, envelope.pv, envelope.seq, envelope.ts, payload),
                    C2SPing.serializer(),
                )
            is C2SReconnect ->
                encodeEnvelopeWith(
                    Envelope(envelope.type, envelope.pv, envelope.seq, envelope.ts, payload),
                    C2SReconnect.serializer(),
                )
            else -> throw IllegalArgumentException("Unsupported envelope payload ${payload?.javaClass?.simpleName}")
        }
    }

    private fun isInputType(obj: Any): Boolean {
        return when (obj) {
            is C2SInput, is C2SInputBatch -> true
            is Envelope<*> -> obj.type == INPUT_TYPE || obj.type == INPUT_BATCH_TYPE
            else -> false
        }
    }

    private fun defaultClient(): HttpClient {
        return HttpClient(OkHttp) {
            install(WebSockets)
            install(Logging)
        }
    }

    private inner class Backoff {
        private var attempt = 0

        fun reset() {
            attempt = 0
        }

        fun nextDelay(): Long {
            val index = min(attempt, BACKOFF_DELAYS.lastIndex)
            if (attempt < BACKOFF_DELAYS.lastIndex) {
                attempt += 1
            }
            return BACKOFF_DELAYS[index]
        }
    }

    private inner class RateLimiter(private val ratePerSecond: Int, private val windowMs: Long = 1000L) {
        private var tokens = ratePerSecond.toDouble()
        private var lastRefill = 0L

        suspend fun await(nowProvider: () -> Long) {
            while (true) {
                val now = nowProvider()
                refill(now)
                if (tokens >= 1.0) {
                    tokens -= 1.0
                    return
                }
                val needed = 1.0 - tokens
                val msPerToken = windowMs.toDouble() / ratePerSecond
                val waitMs = (needed * msPerToken).toLong().coerceAtLeast(1L)
                delay(waitMs)
            }
        }

        private fun refill(now: Long) {
            if (lastRefill == 0L) {
                lastRefill = now
                tokens = ratePerSecond.toDouble()
                return
            }
            val elapsed = now - lastRefill
            if (elapsed <= 0) return
            val tokensToAdd = elapsed.toDouble() * ratePerSecond / windowMs
            tokens = min(ratePerSecond.toDouble(), tokens + tokensToAdd)
            lastRefill = now
        }
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private val BACKOFF_DELAYS = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)
        private const val INPUT_TYPE = "input"
        private const val INPUT_BATCH_TYPE = "input_batch"
    }

    suspend fun close(reason: CloseReason? = null) {
        sessionMutex.withLock {
            val current = session ?: return
            runCatching { current.close(reason ?: CloseReason(CloseReason.Codes.NORMAL, "client")) }
            session = null
        }
    }
}
