@file:Suppress("FunctionName")

package com.bene.jump.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors =
    darkColorScheme(
        primary = Primary,
        secondary = Secondary,
        background = BackgroundDark,
        onBackground = Color(0xFFE8F1F2),
    )

private val LightColors =
    lightColorScheme(
        primary = Primary,
        secondary = Secondary,
        background = BackgroundLight,
        onBackground = Color(0xFF102A43),
    )

@Composable
fun JumpTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
