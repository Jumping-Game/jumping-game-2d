@file:Suppress("FunctionName")

package com.bene.jump.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.bene.jump.vm.GameUiState

@Composable
fun ComposeRenderer(
    state: GameUiState,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
    ) {
        val scale = if (state.worldWidth == 0f) 1f else size.width / state.worldWidth
        val originY = state.cameraY - state.visibleHeight * 0.5f
        drawRect(Color(0xFF0E1428))

        state.platforms.forEach { platform ->
            val left = (platform.x - platform.width * 0.5f + state.worldWidth * 0.5f) * scale
            val top = size.height - ((platform.y - originY) + platform.height * 0.5f) * scale
            drawRect(
                color = Color(0xFF3DDC97),
                topLeft = Offset(left, top),
                size = Size(platform.width * scale, platform.height * scale),
            )
        }

        val player = state.player
        val playerLeft = (player.x - player.width * 0.5f + state.worldWidth * 0.5f) * scale
        val playerTop = size.height - ((player.y - originY) + player.height * 0.5f) * scale
        drawRect(
            color = Color(0xFFFFB74D),
            topLeft = Offset(playerLeft, playerTop),
            size = Size(player.width * scale, player.height * scale),
        )
    }
}
