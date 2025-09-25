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
import com.bene.jump.vm.GameUiState

@Composable
fun ComposeRenderer(
    state: GameUiState,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Canvas(
        modifier =
            modifier
                .background(colors.background)
                .fillMaxSize(),
    ) {
        val scale = if (state.worldWidth == 0f) 1f else size.width / state.worldWidth
        val originY = state.cameraY - state.visibleHeight * 0.5f
        drawRect(colors.background)

        state.platforms.forEach { platform ->
            val left = (platform.x - platform.width * 0.5f + state.worldWidth * 0.5f) * scale
            val top = size.height - ((platform.y - originY) + platform.height * 0.5f) * scale
            drawRect(
                color = colors.secondary,
                topLeft = Offset(left, top),
                size = Size(platform.width * scale, platform.height * scale),
            )
        }

        val player = state.player
        val playerLeft = (player.x - player.width * 0.5f + state.worldWidth * 0.5f) * scale
        val playerTop = size.height - ((player.y - originY) + player.height * 0.5f) * scale
        drawRect(
            color = colors.primary,
            topLeft = Offset(playerLeft, playerTop),
            size = Size(player.width * scale, player.height * scale),
        )
    }
}
