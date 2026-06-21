package com.mbclaw.root.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = MBclawOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE9D6),       // 浅橙容器（设置头卡用）
    onPrimaryContainer = Color(0xFF7A3A00),
    secondary = MBclawBlue,
    onSecondary = MBclawBlueT,
    secondaryContainer = MBclawBlue,            // 用户气泡用
    onSecondaryContainer = MBclawBlueT,
    tertiary = MBclawGreen,
    error = MBclawRed,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,                     // 卡片白
    onSurface = LightText,
    surfaceVariant = LightSurfaceVar,           // AI 气泡 / 二级卡片
    onSurfaceVariant = LightText,               // bug.3: AI 气泡文字用深色不用muted
    outline = LightTextMuted,                   // 副标题灰
    outlineVariant = LightBorder,               // 分隔线
)

private val DarkColorScheme = darkColorScheme(
    primary = MBclawOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A2200),
    onPrimaryContainer = Color(0xFFFFE9D6),
    secondary = MBclawBlue,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF334466),
    onSecondaryContainer = Color(0xFFE0EAFF),
    tertiary = MBclawGreen,
    error = MBclawRed,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkText,                // bug.3: 暗色 AI 气泡文字用主色
    outline = DarkTextMuted,
    outlineVariant = DarkBorder,
)

@Composable
fun MBclawTheme(
    darkTheme: Boolean = false,                 // 仿 MiClaw 默认浅色
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
