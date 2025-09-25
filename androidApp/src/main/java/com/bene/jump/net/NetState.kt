package com.bene.jump.net

import com.bene.jump.core.net.LobbyPlayer
import com.bene.jump.core.net.NetErrorCode
import com.bene.jump.core.net.Role
import com.bene.jump.core.net.RoomState
import com.bene.jump.core.net.S2CStartCountdown

enum class ConnectionPhase {
    Idle,
    Connecting,
    Running,
    Reconnecting,
    Finished,
}

data class NetState(
    val connectionPhase: ConnectionPhase = ConnectionPhase.Idle,
    val connected: Boolean = false,
    val roomId: String? = null,
    val role: Role = Role.MEMBER,
    val roomState: RoomState = RoomState.LOBBY,
    val lobby: List<LobbyPlayer> = emptyList(),
    val countdown: S2CStartCountdown? = null,
    val playerId: String? = null,
    val resumeToken: String? = null,
    val ackTick: Int? = null,
    val lastInputSeq: Int? = null,
    val rttMs: Int = 0,
    val skewMs: Int = 0,
    val droppedSnapshots: Int = 0,
    val lastError: String? = null,
    val lastErrorCode: NetErrorCode? = null,
)
