package com.bene.jump.net

import com.bene.jump.core.model.GameConfig
import com.bene.jump.core.net.Envelope
import com.bene.jump.core.net.LobbyPlayer
import com.bene.jump.core.net.NetConfig
import com.bene.jump.core.net.NetDifficultyCfg
import com.bene.jump.core.net.NetPlayer
import com.bene.jump.core.net.NetWorldCfg
import com.bene.jump.core.net.PROTOCOL_VERSION
import com.bene.jump.core.net.Role
import com.bene.jump.core.net.RoomState
import com.bene.jump.core.net.S2CSnapshot
import com.bene.jump.core.net.S2CStart
import com.bene.jump.core.net.S2CWelcome
import com.bene.jump.core.net.S2CMessage
import com.bene.jump.core.sim.GameSession
import com.bene.jump.vm.PlayerUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.standardTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetControllerTest {
    private val session = GameSession(GameConfig(), seed = 1L)
    private val dispatcher = standardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val socket = FakeSocket()
    private var currentTime: Long = 0L
    private val controller =
        NetController(
            session = session,
            socket = socket,
            scope = scope,
            prefsStore = null,
            clock = { currentTime },
        )

    @Test
    fun `start roster updates lobby and role`() = runTest(dispatcher) {
        startConnection()
        val roster = roster()
        emitWelcome(roster)
        emitStart(roster)

        val state = controller.state.value
        assertEquals(RoomState.RUNNING, state.roomState)
        assertEquals(roster, state.lobby)
        assertEquals(Role.MASTER, state.role)
    }

    @Test
    fun `full snapshot removes missing remote players`() = runTest(dispatcher) {
        startConnection()
        val roster = roster()
        emitWelcome(roster)
        emitStart(roster)

        socket.emit(
            RtSocket.Event.Message(
                envelope(
                    type = "snapshot",
                    ts = 50L,
                    payload =
                        S2CSnapshot(
                            tick = 10,
                            ackTick = 10,
                            lastInputSeq = 1,
                            full = true,
                            players =
                                listOf(
                                    NetPlayer(id = "p1", x = 1f, y = 2f, vx = 0f, vy = 0f, alive = true),
                                    NetPlayer(id = "p2", x = 3f, y = 4f, vx = 0f, vy = 0f, alive = true),
                                ),
                            events = null,
                            stats = null,
                        ),
                ),
            ),
        )
        scope.runCurrent()

        val remote = mutableListOf<PlayerUi>()
        controller.sampleRemotePlayers(nowMs = 50L, out = remote)
        assertEquals(1, remote.size)

        socket.emit(
            RtSocket.Event.Message(
                envelope(
                    type = "snapshot",
                    ts = 100L,
                    payload =
                        S2CSnapshot(
                            tick = 20,
                            ackTick = 20,
                            lastInputSeq = 2,
                            full = true,
                            players = listOf(NetPlayer(id = "p1", x = 2f, y = 3f, vx = 0f, vy = 0f, alive = true)),
                            events = null,
                            stats = null,
                        ),
                ),
            ),
        )
        scope.runCurrent()

        remote.clear()
        controller.sampleRemotePlayers(nowMs = 100L, out = remote)
        assertTrue(remote.isEmpty())
    }

    private suspend fun startConnection() {
        controller.start(
            NetController.Config(
                roomId = "room1",
                wsUrl = "ws://localhost",
                playerName = "bene",
                clientVersion = "test",
                resumeToken = null,
                playerId = null,
                lastAckTick = null,
                useInputBatch = false,
                interpolationDelayMs = 0,
            ),
        )
        scope.runCurrent()
        socket.emit(RtSocket.Event.Opened)
        scope.runCurrent()
    }

    private fun roster(): List<LobbyPlayer> =
        listOf(
            LobbyPlayer(id = "p1", name = "bene", ready = true, role = Role.MASTER, characterId = "aurora"),
            LobbyPlayer(id = "p2", name = "ally", ready = true, role = Role.MEMBER, characterId = "cobalt"),
        )

    private suspend fun emitWelcome(players: List<LobbyPlayer>) {
        socket.emit(
            RtSocket.Event.Message(
                envelope(
                    type = "welcome",
                    payload =
                        S2CWelcome(
                            playerId = "p1",
                            resumeToken = "resume",
                            roomId = "room1",
                            seed = "1",
                            role = Role.MASTER,
                            roomState = RoomState.LOBBY,
                            lobby = S2CWelcome.LobbySnapshot(players = players, maxPlayers = 4),
                            cfg = netConfig(),
                            featureFlags = null,
                        ),
                ),
            ),
        )
        scope.runCurrent()
    }

    private suspend fun emitStart(players: List<LobbyPlayer>) {
        socket.emit(
            RtSocket.Event.Message(
                envelope(
                    type = "start",
                    payload =
                        S2CStart(
                            startTick = 10,
                            serverTick = 20,
                            serverTimeMs = 100,
                            tps = 60,
                            players = players,
                        ),
                ),
            ),
        )
        scope.runCurrent()
    }

    private fun envelope(
        type: String,
        payload: S2CMessage,
        ts: Long = currentTime,
    ): Envelope<S2CMessage> = Envelope(type, PROTOCOL_VERSION, 1u, ts, payload)

    private fun netConfig(): NetConfig =
        NetConfig(
            tps = 60,
            snapshotRateHz = 10,
            maxRollbackTicks = 6,
            inputLeadTicks = 2,
            world =
                NetWorldCfg(
                    worldWidth = 100f,
                    platformWidth = 10f,
                    platformHeight = 2f,
                    gapMin = 1f,
                    gapMax = 2f,
                    gravity = -9.8f,
                    jumpVy = 5f,
                    springVy = 7f,
                    maxVx = 3f,
                    tiltAccel = 0.5f,
                ),
            difficulty =
                NetDifficultyCfg(
                    gapMinStart = 1f,
                    gapMinEnd = 1f,
                    gapMaxStart = 2f,
                    gapMaxEnd = 2f,
                    springChanceStart = 0.1f,
                    springChanceEnd = 0.2f,
                ),
        )

    private inner class FakeSocket : RtSocketClient {
        private val shared = MutableSharedFlow<RtSocket.Event>(extraBufferCapacity = 16)

        override fun connect(wsUrl: String) = shared

        override suspend fun send(obj: Any) {
            // ignore sends in tests
        }

        override suspend fun close() {
            // no-op
        }

        suspend fun emit(event: RtSocket.Event) {
            shared.emit(event)
        }
    }
}
