package com.bene.jump.net

import com.bene.jump.core.net.C2SInput
import com.bene.jump.core.net.C2SInputBatch
import com.bene.jump.core.net.C2SJoin
import com.bene.jump.core.net.C2SMessage
import com.bene.jump.core.net.C2SPing
import com.bene.jump.core.net.C2SReadySet
import com.bene.jump.core.net.C2SReconnect
import com.bene.jump.core.net.C2SStartRequest
import com.bene.jump.core.net.Envelope
import com.bene.jump.core.net.S2CMessage
import com.bene.jump.core.net.decodeS2C
import com.bene.jump.core.net.encodeC2S
import com.bene.jump.core.net.encodeEnvelope as encodeEnvelopeWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

class RtSocket(
    client: OkHttpClient? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    sealed class Event {
        object Opened : Event()

        data class Message(val envelope: Envelope<S2CMessage>) : Event()

        data class Closed(val code: Int?, val reason: String?) : Event()

        data class Failure(val throwable: Throwable) : Event()
    }

    private val httpClient: OkHttpClient = client ?: defaultClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()
    private var webSocket: WebSocket? = null
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
                        val request = Request.Builder().url(wsUrl).build()
                        val terminated = CompletableDeferred<Unit>()
                        val listener = FlowListener(this@callbackFlow, backoff, terminated)
                        val socket = httpClient.newWebSocket(request, listener)
                        sessionMutex.withLock { webSocket = socket }
                        terminated.await()
                        sessionMutex.withLock { if (webSocket == socket) webSocket = null }
                        stopHeartbeat()
                        if (!isActive || flowScope.isClosedForSend) break
                        val delayMs = backoff.nextDelay()
                        if (delayMs > 0) delay(delayMs)
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
            sessionMutex.withLock { webSocket }
                ?: throw IllegalStateException("WebSocket not connected")
        if (isInputType(obj)) {
            rateLimiter.await(clock)
        }
        if (!activeSession.send(text)) {
            throw IllegalStateException("WebSocket send failed")
        }
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

    private fun nextSeq(): UInt {
        val next = seq.getAndUpdate { current -> if (current == Int.MAX_VALUE) 0 else current + 1 }
        return next.toUInt()
    }

    private fun encodeEnvelopeDynamic(envelope: Envelope<*>): String {
        return when (val payload = envelope.payload) {
            is C2SJoin -> encodeEnvelopeWith(Envelope(envelope.type, envelope.pv, envelope.seq, envelope.ts, payload), C2SJoin.serializer())
            is C2SInput -> encodeEnvelopeWith(Envelope(envelope.type, envelope.pv, envelope.seq, envelope.ts, payload), C2SInput.serializer())
            is C2SInputBatch -> encodeEnvelopeWith(Envelope(envelope.type, envelope.pv, envelope.seq, envelope.ts, payload), C2SInputBatch.serializer())
            is C2SPing -> encodeEnvelopeWith(Envelope(envelope.type, envelope.pv, envelope.seq, envelope.ts, payload), C2SPing.serializer())
            is C2SReconnect -> encodeEnvelopeWith(Envelope(envelope.type, envelope.pv, envelope.seq, envelope.ts, payload), C2SReconnect.serializer())
            is C2SReadySet -> encodeEnvelopeWith(Envelope(envelope.type, envelope.pv, envelope.seq, envelope.ts, payload), C2SReadySet.serializer())
            is C2SStartRequest -> encodeEnvelopeWith(Envelope(envelope.type, envelope.pv, envelope.seq, envelope.ts, payload), C2SStartRequest.serializer())
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

    private fun defaultClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
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

    private inner class FlowListener(
        private val channel: SendChannel<Event>,
        private val backoff: Backoff,
        private val terminated: CompletableDeferred<Unit>,
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            backoff.reset()
            channel.trySendSafe(Event.Opened)
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching { decodeS2C(text) }
                .onSuccess { channel.trySendSafe(Event.Message(it)) }
                .onFailure { channel.trySendSafe(Event.Failure(it)) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            stopHeartbeat()
            channel.trySendSafe(Event.Closed(code, reason))
            terminated.complete(Unit)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            stopHeartbeat()
            channel.trySendSafe(Event.Failure(t))
            terminated.complete(Unit)
        }
    }

    private fun <T> SendChannel<T>.trySendSafe(value: T) {
        runCatching { trySend(value).isSuccess }
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private val BACKOFF_DELAYS = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)
        private const val INPUT_TYPE = "input"
        private const val INPUT_BATCH_TYPE = "input_batch"
    }

    suspend fun close(code: Int = 1000, reason: String? = "client") {
        sessionMutex.withLock {
            val current = webSocket ?: return
            runCatching { current.close(code, reason) }
            webSocket = null
        }
    }
}
