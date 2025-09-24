@file:Suppress("FunctionName")

package com.bene.jump.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bene.jump.core.model.SessionPhase
import com.bene.jump.render.ComposeRenderer
import com.bene.jump.vm.GameUiState

@Composable
fun GameScreen(
    state: GameUiState,
    onTogglePause: () -> Unit,
    onRestart: () -> Unit,
    onTouchChange: (Boolean) -> Unit,
    onBackToMenu: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        onTouchChange(true)
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) {
                                onTouchChange(false)
                                break
                            }
                        }
                    }
                },
    ) {
        ComposeRenderer(state = state, modifier = Modifier.fillMaxSize())
        ScoreHud(score = state.score)
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onTogglePause) {
                Text(text = if (state.phase == SessionPhase.Paused) "Resume" else "Pause")
            }
            Button(onClick = onBackToMenu) {
                Text(text = "Menu")
            }
        }
        when (state.phase) {
            SessionPhase.Paused -> OverlayPanel(title = "Paused", onRestart = onRestart, onBackToMenu = onBackToMenu)
            SessionPhase.GameOver -> OverlayPanel(title = "Game Over", onRestart = onRestart, onBackToMenu = onBackToMenu)
            else -> Unit
        }
    }
}

@Composable
private fun BoxScope.ScoreHud(score: Float) {
    Text(
        text = score.toInt().toString(),
        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
        color = MaterialTheme.colorScheme.onBackground,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
    )
}

@Composable
private fun BoxScope.OverlayPanel(
    title: String,
    onRestart: () -> Unit,
    onBackToMenu: () -> Unit,
) {
    Surface(
        tonalElevation = 8.dp,
        modifier =
            Modifier
                .align(Alignment.Center)
                .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Restart")
            }
            Button(onClick = onBackToMenu, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Menu")
            }
        }
    }
}
