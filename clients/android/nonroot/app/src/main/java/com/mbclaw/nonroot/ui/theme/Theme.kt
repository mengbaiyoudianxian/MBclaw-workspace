package com.mbclaw.nonroot.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MBclawBlue = Color(0xFF58A6FF)
val MBclawGreen = Color(0xFF3FB950)
val DarkBg = Color(0xFF0D1117)
val DarkSurface = Color(0xFF161B22)

private val DarkColors = darkColorScheme(
    primary = MBclawBlue,
    secondary = MBclawGreen,
    background = DarkBg,
    surface = DarkSurface,
)

@Composable
fun MBclawLiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
