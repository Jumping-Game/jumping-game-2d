package com.bene.jump.net

import android.os.Build
import com.bene.jump.core.model.GameInput
import com.bene.jump.core.model.GameSession
import com.bene.jump.core.net.C2SInput
import com.bene.jump.core.net.C2SInputBatch
import com.bene.jump.core.net.C2SJoin
import com.bene.jump.core.net.C2SReconnect
import com.bene.jump.core.net.Checksums
import com.bene.jump.core.net.ClientPredBuffer
import com.bene.jump.core.net.CompactState
import com.bene.jump.core.net.Interpolator
import com.bene.jump.core.net.NetErrorCode
import com.bene.jump.core.net.NetPlayer
import com.bene.jump.core.net.Role
import com.bene.jump.core.net.RoomState
import com.bene.jump.core.net.S2CError
import com.bene.jump.core.net.S2CFinish
import com.bene.jump.core.net.S2CLobbyState
import com.bene.jump.core.net.S2CPlayerPresence
import com.bene.jump.core.net.S2CPong
import com.bene.jump.core.net.S2CRoleChanged
import com.bene.jump.core.net.S2CSnapshot
import com.bene.jump.core.net.S2CStart
import com.bene.jump.core.net.S2CStartCountdown
import com.bene.jump.core.net.S2CWelcome
import com.bene.jump.core.net.asEnvelope
import com.bene.jump.data.NetPrefsStore
import com.bene.jump.vm.PlayerUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayDeque
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.roundToInt

class NetController(
    private val session: GameSession,
    private val socket: RtSocket,
    private val scope: CoroutineScope,
    private val prefsStore: NetPrefsStore? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    data class Config(
        val roomId: String,
        val wsUrl: String,
        val playerName: String,
        val clientVersion: String,
        val resumeToken: String? = null,
        val playerId: String? = null,
        val lastAckTick: Int? = null,
        val useInputBatch: Boolean,
        val interpolationDelayMs: Int,
    )

    private val stateFlow = MutableStateFlow(NetState())
    val state: StateFlow<NetState> = stateFlow

    private val buffer = ClientPredBuffer(512)
    private val compactScratch = CompactState()
    private val interpolation = Interpolator(bufferSize = 128, maxExtrapolationMs = 150)
    private val remoteScratch = CompactState()
    private val replayInput = GameInput()
    private val snapshotQueue: ArrayDeque<SnapshotEnvelope> = ArrayDeque()
    private val remoteIdsScratch = ArrayList<String>(4)
    private val batchFrames = ArrayList<C2SInputBatch.InputDelta>(8)

    private var controllerJob: Job? = null
    private var config: Config? = null
    private var playerId: String? = null
    private var resumeToken: String? = null
    private var roomId: String? = null
    private var role: Role = Role.MEMBER
    private var roomState: RoomState = RoomState.LOBBY
    private var countdown: S2CStartCountdown? = null
    private var lastAckTick: Int = -1
    private var persistedAckTick: Int = -1
    private var lastInputSeq: Int = -1
    private var droppedSnapshots: Int = 0
    private var lastSentTick: Int = -1
    private var latestTick: Int = -1
    private var latestInputTick: Int = -1
    private var lastSendAtMs: Long = 0L
    private var lastChecksumTick: Int = -1
    private var rttMs: Int = 0
    private var skewMs: Int = 0
    private var phase: ConnectionPhase = ConnectionPhase.Idle
    private var awaitingStart: Boolean = false
    private val pendingSeq = AtomicInteger(1)
    private var interpolationDelayMs: Long = 100L
    private var useInputBatch: Boolean = true
    private var lobbyMaxPlayers: Int = 0

    fun start(config: Config) {
        this.config = config
        this.useInputBatch = config.useInputBatch
        this.interpolationDelayMs = config.interpolationDelayMs.toLong()
        this.resumeToken = config.resumeToken
        this.playerId = config.playerId
        this.roomId = config.roomId
        this.lastAckTick = config.lastAckTick ?: -1
        this.persistedAckTick = this.lastAckTick
        this.lastInputSeq = -1
        this.droppedSnapshots = 0
        if (phase == ConnectionPhase.Running || phase == ConnectionPhase.Connecting) return
        phase = ConnectionPhase.Connecting
        stateFlow.update {
            it.copy(
                connectionPhase = phase,
                connected = false,
                roomId = config.roomId,
                playerId = config.playerId ?: it.playerId,
                resumeToken = config.resumeToken ?: it.resumeToken,
                ackTick = lastAckTick.takeIf { tick -> tick >= 0 },
                lastInputSeq = null,
                droppedSnapshots = 0,
                lastError = null,
                lastErrorCode = null,
            )
        }
        controllerJob?.cancel()
        controllerJob =
            scope.launch {
                socket.connect(config.wsUrl).collect { event ->
                    when (event) {
                        is RtSocket.Event.Opened -> handleOpened()
                        is RtSocket.Event.Message -> handleMessage(event)
                        is RtSocket.Event.Closed -> handleClosed(event)
                        is RtSocket.Event.Failure -> handleFailure(event.throwable)
                    }
                }
            }
    }

    fun stop() {
        controllerJob?.cancel()
        controllerJob = null
        scope.launch { socket.close() }
        phase = ConnectionPhase.Finished
        roomState = RoomState.FINISHED
        countdown = null
        stateFlow.update {
            it.copy(
                connectionPhase = phase,
                connected = false,
                roomState = roomState,
                countdown = null,
            )
        }
    }

    fun step(
        input: GameInput,
        dt: Float,
    ) {
        drainSnapshots()
        if (phase != ConnectionPhase.Running) {
            return
        }
        val tick = session.world.tick.toInt()
        val axisX = input.tilt.coerceIn(-1f, 1f)
        val jump = input.touchDown
        buffer.putInput(tick, axisX, jump, false, null)
        latestInputTick = tick
        session.step(input, dt)
        val simTick = session.world.tick.toInt()
        latestTick = simTick
        compactScratch.capture(session.world)
        buffer.putState(simTick, compactScratch)
        if (phase == ConnectionPhase.Running) {
            maybeAttachChecksum(tick)
            maybeSendInputs(latestInputTick)
        }
    }

    fun sampleRemotePlayers(
        nowMs: Long,
        out: MutableList<PlayerUi>,
    ) {
        interpolation.prune(nowMs, 1_000L)
        out.clear()
        remoteIdsScratch.clear()
        remoteIdsScratch.addAll(remotePlayerStates.keys)
        for (id in remoteIdsScratch) {
            if (id == playerId) continue
            if (interpolation.sample(id, nowMs - interpolationDelayMs, remoteScratch)) {
                out.add(
                    PlayerUi(
                        x = remoteScratch.x,
                        y = remoteScratch.y,
                        width = session.world.player.bounds.halfWidth * 2f,
                        height = session.world.player.bounds.halfHeight * 2f,
                    ),
                )
            }
        }
    }

    private fun handleOpened() {
        val resumeAck = lastAckTick
        buffer.reset()
        lastSentTick = -1
        latestInputTick = -1
        latestTick = session.world.tick.toInt()
        snapshotQueue.clear()
        lastChecksumTick = -1
        droppedSnapshots = 0
        lastInputSeq = -1
        pendingSeq.set(1)
        val cfg = checkNotNull(config)
        val resume = resumeToken
        awaitingStart = true
        phase = if (resume != null && playerId != null && resumeAck >= 0) ConnectionPhase.Reconnecting else ConnectionPhase.Connecting
        stateFlow.update {
            it.copy(
                connectionPhase = phase,
                connected = true,
                droppedSnapshots = 0,
            )
        }
        val message =
            if (resume != null && playerId != null && resumeAck >= 0) {
                C2SReconnect(
                    playerId = playerId!!,
                    resumeToken = resume,
                    lastAckTick = resumeAck,
                ).asEnvelope(seq = nextSeq(), ts = clock())
            } else {
                C2SJoin(
                    name = cfg.playerName,
                    clientVersion = cfg.clientVersion,
                    device = Build.MODEL ?: "android",
                    capabilities = C2SJoin.Capabilities(tilt = true, vibrate = true),
                ).asEnvelope(seq = nextSeq(), ts = clock())
            }
        scope.launch { socket.send(message) }
    }

    private fun handleMessage(event: RtSocket.Event.Message) {
        when (val payload = event.envelope.payload) {
            is S2CWelcome -> onWelcome(payload)
            is S2CLobbyState -> onLobbyState(payload)
            is S2CStartCountdown -> onStartCountdown(payload)
            is S2CStart -> onStart(payload)
            is S2CSnapshot -> onSnapshot(event.envelope.ts, payload)
            is S2CError -> onError(payload)
            is S2CPong -> onPong(event.envelope.ts, payload)
            is S2CPlayerPresence -> onPresence(payload)
            is S2CFinish -> onFinish(payload)
            is S2CRoleChanged -> onRoleChanged(payload)
            else -> Unit
        }
    }

    private fun handleClosed(event: RtSocket.Event.Closed) {
        stateFlow.update {
            it.copy(
                connected = false,
                connectionPhase = if (phase == ConnectionPhase.Finished) ConnectionPhase.Finished else ConnectionPhase.Reconnecting,
            )
        }
        if (phase != ConnectionPhase.Finished) {
            phase = ConnectionPhase.Reconnecting
        }
    }

    private fun handleFailure(throwable: Throwable) {
        phase = if (phase == ConnectionPhase.Finished) ConnectionPhase.Finished else ConnectionPhase.Reconnecting
        stateFlow.update {
            it.copy(
                connectionPhase = phase,
                connected = false,
                lastError = throwable.message,
            )
        }
    }

    private fun onWelcome(welcome: S2CWelcome) {
        playerId = welcome.playerId
        resumeToken = welcome.resumeToken
        roomId = welcome.roomId
        role = welcome.role
        roomState = welcome.roomState
        countdown = null
        session.restart(welcome.seed.toLongOrNull() ?: session.world.seed)
        session.world.tick = 0
        buffer.reset()
        lastAckTick = -1
        persistedAckTick = -1
        latestInputTick = -1
        interpolation.clear()
        remotePlayerStates.clear()
        awaitingStart = welcome.roomState != RoomState.RUNNING
        val lobbyPlayers = welcome.lobby?.players.orEmpty()
        lobbyMaxPlayers = welcome.lobby?.maxPlayers ?: lobbyMaxPlayers
        if (roomState == RoomState.RUNNING) {
            phase = ConnectionPhase.Running
        } else if (roomState == RoomState.FINISHED) {
            phase = ConnectionPhase.Finished
        } else {
            phase = ConnectionPhase.Connecting
        }
        stateFlow.update {
            it.copy(
                connectionPhase = phase,
                connected = true,
                playerId = welcome.playerId,
                roomId = welcome.roomId,
                role = role,
                roomState = roomState,
                lobby = lobbyPlayers,
                lobbyMaxPlayers = lobbyMaxPlayers,
                countdown = countdown,
                resumeToken = welcome.resumeToken,
                ackTick = null,
                lastInputSeq = null,
                droppedSnapshots = 0,
            )
        }
        persistCredentialsIfNeeded()
    }

    private fun onStart(start: S2CStart) {
        awaitingStart = false
        session.world.tick = start.startTick.toLong()
        latestTick = start.startTick
        buffer.reset()
        phase = ConnectionPhase.Running
        roomState = RoomState.RUNNING
        countdown = null
        stateFlow.update {
            it.copy(
                connectionPhase = phase,
                roomState = roomState,
                countdown = null,
            )
        }
    }

    private fun onSnapshot(
        ts: Long,
        snapshot: S2CSnapshot,
    ) {
        if (awaitingStart) return
        snapshotQueue.addLast(SnapshotEnvelope(ts, snapshot))
    }

    private fun onError(error: S2CError) {
        val code = NetErrorCode.fromRaw(error.code)
        stateFlow.update {
            it.copy(
                lastError = error.message,
                lastErrorCode = code,
            )
        }
        phase = ConnectionPhase.Reconnecting
        stateFlow.update { it.copy(connectionPhase = phase) }
    }

    private fun onFinish(finish: S2CFinish) {
        phase = ConnectionPhase.Finished
        roomState = RoomState.FINISHED
        countdown = null
        stateFlow.update {
            it.copy(
                connectionPhase = phase,
                roomState = roomState,
                countdown = null,
            )
        }
        stop()
    }

    private fun onPresence(presence: S2CPlayerPresence) {
        if (presence.state == "left") {
            remotePlayerStates.remove(presence.id)
            interpolation.remove(presence.id)
        }
    }

    private fun onLobbyState(state: S2CLobbyState) {
        roomState = state.roomState
        countdown = countdown.takeIf { roomState == RoomState.STARTING }
        stateFlow.update {
            it.copy(
                roomState = roomState,
                lobby = state.players,
                lobbyMaxPlayers = state.maxPlayers,
                countdown = countdown,
            )
        }
    }

    private fun onStartCountdown(countdown: S2CStartCountdown) {
        awaitingStart = true
        roomState = RoomState.STARTING
        this.countdown = countdown
        stateFlow.update {
            it.copy(
                roomState = roomState,
                countdown = countdown,
            )
        }
    }

    private fun onRoleChanged(roleChanged: S2CRoleChanged) {
        if (roleChanged.newMasterId == playerId) {
            role = Role.MASTER
        } else if (role == Role.MASTER) {
            role = Role.MEMBER
        }
        stateFlow.update { it.copy(role = role) }
    }

    private fun onPong(
        serverTs: Long,
        pong: S2CPong,
    ) {
        val now = clock()
        val rtt = (now - pong.t0).coerceAtLeast(0L)
        val skew = (((pong.t1 + serverTs) / 2.0) - ((pong.t0 + now) / 2.0)).roundToInt()
        rttMs = rtt.toInt()
        skewMs = skew
        stateFlow.update {
            it.copy(
                rttMs = rttMs,
                skewMs = skewMs,
            )
        }
    }

    private fun drainSnapshots() {
        while (snapshotQueue.isNotEmpty()) {
            val envelope = snapshotQueue.removeFirst()
            applySnapshot(envelope)
        }
    }

    private fun applySnapshot(envelope: SnapshotEnvelope) {
        val snapshot = envelope.snapshot
        snapshot.ackTick?.let {
            if (it > lastAckTick) {
                lastAckTick = it
                persistAckTickIfNeeded()
            }
        }
        snapshot.lastInputSeq?.let { lastInputSeq = it }
        snapshot.stats?.droppedSnapshots?.let { droppedSnapshots = it }
        snapshot.players.forEach { player ->
            if (player.id == playerId) {
                applyLocalPlayer(snapshot.tick, player)
            } else {
                applyRemotePlayer(envelope.ts, player)
            }
        }
        stateFlow.update {
            it.copy(
                ackTick = lastAckTick.takeIf { tick -> tick >= 0 },
                lastInputSeq = lastInputSeq.takeIf { seq -> seq >= 0 },
                droppedSnapshots = droppedSnapshots,
            )
        }
    }

    private fun applyLocalPlayer(
        tick: Int,
        player: NetPlayer,
    ) {
        val world = session.world
        player.x?.let { world.player.position.x = it }
        player.y?.let { world.player.position.y = it }
        player.vx?.let { world.player.velocity.x = it }
        player.vy?.let { world.player.velocity.y = it }
        world.player.syncBounds()
        world.tick = tick.toLong()
        latestTick = max(latestTick, tick)
        if (lastSentTick < tick) {
            lastSentTick = tick
        }
        if (latestTick > tick) {
            var nextTick = tick + 1
            while (nextTick <= latestTick) {
                val frame = buffer.getInput(nextTick) ?: break
                replayInput.tilt = frame.axisX
                replayInput.touchDown = frame.jump
                replayInput.pauseRequested = false
                session.step(replayInput, STEP_SECONDS)
                nextTick += 1
            }
            latestTick = session.world.tick.toInt()
        }
    }

    private fun applyRemotePlayer(
        ts: Long,
        player: NetPlayer,
    ) {
        val state = remotePlayerStates.getOrPut(player.id) { CompactState() }
        player.x?.let { state.x = it }
        player.y?.let { state.y = it }
        player.vx?.let { state.vx = it }
        player.vy?.let { state.vy = it }
        interpolation.push(player.id, ts + interpolationDelayMs, state)
    }

    private fun maybeAttachChecksum(tick: Int) {
        if (tick - lastChecksumTick >= CHECKSUM_INTERVAL) {
            compactScratch.capture(session.world)
            val checksum = Checksums.computeHex(compactScratch, session.world)
            buffer.getInput(tick)?.checksum = checksum
            lastChecksumTick = tick
        }
    }

    private fun maybeSendInputs(targetTick: Int) {
        val now = clock()
        if (targetTick <= lastSentTick) return
        val minIntervalMs = if (useInputBatch) 45L else 60L
        if (now - lastSendAtMs < minIntervalMs) return
        if (useInputBatch) {
            sendInputBatch(lastSentTick + 1, targetTick)
        } else {
            sendSingle(targetTick)
        }
        lastSentTick = targetTick
        lastSendAtMs = now
    }

    private fun sendSingle(tick: Int) {
        val frame = buffer.getInput(tick) ?: return
        val message =
            C2SInput(
                tick = tick,
                axisX = frame.axisX,
                jump = frame.jump.takeIf { it },
                shoot = frame.shoot.takeIf { it },
                checksum = frame.checksum,
            )
        scope.launch { socket.send(message) }
    }

    private fun sendInputBatch(
        fromTick: Int,
        toTick: Int,
    ) {
        if (toTick < fromTick) return
        batchFrames.clear()
        var startTick = fromTick
        buffer.replay(fromTick, toTick) { frame ->
            if (batchFrames.isEmpty()) {
                startTick = frame.tick
            }
            batchFrames.add(
                C2SInputBatch.InputDelta(
                    d = frame.tick - startTick,
                    axisX = frame.axisX,
                    jump = frame.jump.takeIf { it },
                    shoot = frame.shoot.takeIf { it },
                    checksum = frame.checksum,
                ),
            )
        }
        if (batchFrames.isEmpty()) return
        val batch = C2SInputBatch(startTick = startTick, frames = batchFrames.toList())
        scope.launch { socket.send(batch) }
    }

    private fun nextSeq(): UInt {
        val next = pendingSeq.getAndUpdate { current -> if (current == Int.MAX_VALUE) 0 else current + 1 }
        return next.toUInt()
    }

    private fun persistCredentialsIfNeeded() {
        val pid = playerId ?: return
        val token = resumeToken ?: return
        val store = prefsStore ?: return
        scope.launch { store.persistCredentials(pid, token) }
    }

    private fun persistAckTickIfNeeded() {
        val ack = lastAckTick
        val store = prefsStore ?: return
        if (ack < 0 || ack == persistedAckTick) return
        persistedAckTick = ack
        scope.launch { store.updateLastAckTick(ack) }
    }

    companion object {
        private const val CHECKSUM_INTERVAL = 20
        private const val STEP_SECONDS = 1f / 60f
    }

    private val remotePlayerStates = mutableMapOf<String, CompactState>()

    private data class SnapshotEnvelope(
        val ts: Long,
        val snapshot: S2CSnapshot,
    )
}
