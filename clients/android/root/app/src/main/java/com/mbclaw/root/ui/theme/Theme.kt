package com.mbclaw.root.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 主色 → 小米橙；辅色 → 晴空蓝；MiClaw 风格强调 surface 而非 primary
private val DarkColorScheme = darkColorScheme(
    primary = MBclawOrange,
    onPrimary = Color.White,
    primaryContainer = MBclawOrange.copy(alpha = 0.18f),
    onPrimaryContainer = MBclawOrange,
    secondary = MBclawBlue,
    secondaryContainer = MBclawBlue.copy(alpha = 0.15f),
    tertiary = MBclawGreen,
    error = MBclawRed,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    outline = DarkBorder,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = DarkTextMuted,
)

private val LightColorScheme = lightColorScheme(
    primary = MBclawOrange,
    onPrimary = Color.White,
    primaryContainer = MBclawOrange.copy(alpha = 0.12f),
    onPrimaryContainer = MBclawOrange,
    secondary = MBclawBlue,
    secondaryContainer = MBclawBlue.copy(alpha = 0.10f),
    tertiary = MBclawGreen,
    error = MBclawRed,
    background = LightBackground,
    surface = LightSurface,
    outline = LightBorder,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = LightTextMuted,
)

@Composable
fun MBclawTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
