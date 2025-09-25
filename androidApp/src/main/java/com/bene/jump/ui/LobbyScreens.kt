@file:Suppress("FunctionName")

package com.bene.jump.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bene.jump.core.net.Role
import com.bene.jump.core.net.RoomState
import com.bene.jump.net.NetState
import com.bene.jump.vm.LobbyStatus
import com.bene.jump.vm.LobbyUiState

private data class CharacterPresentation(val name: String, val swatch: Color)

private val CharacterCatalog = mapOf(
    "jumper_red" to CharacterPresentation("Red Rocket", Color(0xFFE57373)),
    "jumper_blue" to CharacterPresentation("Blue Comet", Color(0xFF64B5F6)),
    "jumper_green" to CharacterPresentation("Green Glide", Color(0xFF81C784)),
    "jumper_yellow" to CharacterPresentation("Golden Leap", Color(0xFFFFF176)),
)

@Composable
fun LobbySetupScreen(
    lobbyState: LobbyUiState,
    onCreateRoom: (name: String, region: String?, maxPlayers: Int?, mode: String?) -> Unit,
    onJoinRoom: (roomId: String, name: String) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(lobbyState.playerName.ifBlank { "" }) }
    var roomId by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var maxPlayers by remember { mutableStateOf("4") }
    var mode by remember { mutableStateOf("endless") }

    LaunchedEffect(lobbyState.playerName) {
        if (lobbyState.playerName.isNotBlank() && name.isBlank()) {
            name = lobbyState.playerName
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Multiplayer Lobby", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Choose a name and either create a lobby or join an existing one.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Player name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = region,
            onValueChange = { region = it },
            label = { Text("Region (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = maxPlayers,
            onValueChange = { maxPlayers = it.filter { ch -> ch.isDigit() } },
            label = { Text("Max players") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = mode,
            onValueChange = { mode = it },
            label = { Text("Mode (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val max = maxPlayers.toIntOrNull()
                val regionValue = region.ifBlank { null }
                val modeValue = mode.ifBlank { null }
                onCreateRoom(name.trim(), regionValue, max, modeValue)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = lobbyState.status != LobbyStatus.Creating && lobbyState.loading.not(),
        ) {
            Text("Create room")
        }
        Spacer(modifier = Modifier.size(12.dp))
        OutlinedTextField(
            value = roomId,
            onValueChange = { roomId = it.uppercase() },
            label = { Text("Room code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onJoinRoom(roomId.trim(), name.trim()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = lobbyState.status != LobbyStatus.Joining && lobbyState.loading.not(),
        ) {
            Text("Join room")
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        lobbyState.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun LobbyRoomScreen(
    lobbyState: LobbyUiState,
    netState: NetState,
    onSelectCharacter: (String) -> Unit,
    onReadyChanged: (Boolean) -> Unit,
    onStart: (Int?) -> Unit,
    onLeave: () -> Unit,
) {
    val players = lobbyState.players
    val maxPlayers = lobbyState.maxPlayers.takeIf { it > 0 } ?: netState.lobbyMaxPlayers
    val selectedCharacter = lobbyState.selectedCharacter
    val takenBy = players.associate { player -> player.characterId to player.name }
    var countdown by remember { mutableStateOf("3") }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Lobby", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Room: ${lobbyState.roomId ?: "-"}", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Players (${players.size}${maxPlayers.takeIf { it > 0 }?.let { "/$it" } ?: ""})",
            style = MaterialTheme.typography.titleMedium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (players.isEmpty()) {
                Text(text = "Waiting for players…", style = MaterialTheme.typography.bodyMedium)
            } else {
                players.forEach { player ->
                    val roleLabel = if (player.role == Role.MASTER) " (Host)" else ""
                    val readyLabel = if (player.ready) "Ready" else "Not ready"
                    val characterLabel = player.characterId?.let { CharacterCatalog[it]?.name ?: it } ?: "No character"
                    Surface(
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = player.name + roleLabel, fontWeight = FontWeight.SemiBold)
                                Text(text = characterLabel, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(text = readyLabel, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Text(text = "Choose your character", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lobbyState.availableCharacters.forEach { characterId ->
                val info = CharacterCatalog[characterId] ?: CharacterPresentation(characterId, MaterialTheme.colorScheme.primary)
                val isSelected = characterId == selectedCharacter
                val claimedBy = takenBy[characterId]?.takeIf { it != lobbyState.playerName }
                CharacterChip(
                    characterId = characterId,
                    info = info,
                    selected = isSelected,
                    takenBy = claimedBy,
                    enabled = lobbyState.roomState == RoomState.LOBBY,
                    onClick = { onSelectCharacter(characterId) },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val readyText = if (lobbyState.ready) "Unready" else "Ready"
            Button(
                onClick = { onReadyChanged(!lobbyState.ready) },
                enabled = lobbyState.roomState == RoomState.LOBBY,
            ) {
                Text(readyText)
            }
            if (lobbyState.role == Role.MASTER) {
                OutlinedTextField(
                    value = countdown,
                    onValueChange = { value -> countdown = value.filter { it.isDigit() }.take(2) },
                    label = { Text("Countdown (s)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.outlinedTextFieldColors(),
                )
                val allReady = players.isNotEmpty() && players.all { it.ready }
                Button(
                    onClick = { onStart(countdown.toIntOrNull()) },
                    enabled = lobbyState.roomState == RoomState.LOBBY && allReady,
                ) {
                    Text("Start")
                }
            }
        }
        lobbyState.countdown?.let { countdownState ->
            val secondsRemaining = countdownState.countdownSec
            Text(
                text = "Starting in ${secondsRemaining}s",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        lobbyState.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("Leave lobby") }
    }
}

@Composable
private fun CharacterChip(
    characterId: String,
    info: CharacterPresentation,
    selected: Boolean,
    takenBy: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val borderColor =
        when {
            selected -> MaterialTheme.colorScheme.primary
            takenBy != null -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        }
    val label = info.name
    Surface(
        tonalElevation = if (selected) 6.dp else 0.dp,
        shape = RoundedCornerShape(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onClick() },
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .background(info.swatch, shape = RoundedCornerShape(6.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                )
                Column {
                    Text(text = label, fontWeight = FontWeight.Medium)
                    val subtitle =
                        when {
                            selected -> "You"
                            takenBy != null -> "Taken by $takenBy"
                            else -> "Available"
                        }
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (selected) {
                Text(text = "Selected", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, textAlign = TextAlign.End)
            }
        }
    }
}
