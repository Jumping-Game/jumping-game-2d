@file:Suppress("FunctionName")

package com.bene.jump.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors =
    darkColorScheme(
        primary = Primary,
        secondary = Secondary,
        background = BackgroundDark,
        onBackground = Color(0xFFE8F1F2),
    )

@Composable
fun JumpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
