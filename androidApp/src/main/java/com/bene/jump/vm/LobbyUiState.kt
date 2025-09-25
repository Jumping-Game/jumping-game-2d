package com.bene.jump.vm

import com.bene.jump.core.net.LobbyPlayer
import com.bene.jump.core.net.Role
import com.bene.jump.core.net.RoomState
import com.bene.jump.core.net.S2CStartCountdown

enum class LobbyStatus {
    Idle,
    Creating,
    Joining,
    InRoom,
    Error,
}

data class LobbyUiState(
    val status: LobbyStatus = LobbyStatus.Idle,
    val roomId: String? = null,
    val playerName: String = "",
    val role: Role = Role.MEMBER,
    val roomState: RoomState = RoomState.LOBBY,
    val players: List<LobbyPlayer> = emptyList(),
    val maxPlayers: Int = 0,
    val countdown: S2CStartCountdown? = null,
    val availableCharacters: List<String> = DEFAULT_CHARACTERS,
    val selectedCharacter: String? = null,
    val ready: Boolean = false,
    val errorMessage: String? = null,
    val loading: Boolean = false,
) {
    companion object {
        val DEFAULT_CHARACTERS = listOf("jumper_red", "jumper_blue", "jumper_green", "jumper_yellow")
    }
}
