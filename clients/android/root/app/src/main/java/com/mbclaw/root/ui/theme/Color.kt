package com.mbclaw.root.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────
// MBclaw 调色 — 仿 MiClaw / 小米 HyperOS
// 重点修正：MiClaw 用户气泡是浅蓝，AI 气泡是浅灰白
//          主色保留小米橙，但只用在 logo / 按钮，不入气泡
// ─────────────────────────────────────────────────

// 品牌色
val MBclawOrange = Color(0xFFFF6900)
val MBclawBlue   = Color(0xFFD8E5FF)   // 浅蓝（用户气泡底，从截图取色）
val MBclawBlueT  = Color(0xFF1A1A1E)   // 用户气泡文本
val MBclawGreen  = Color(0xFF34C759)
val MBclawRed    = Color(0xFFFF3B30)
val MBclawPurple = Color(0xFFAF52DE)

// 浅色（默认）— 仿 MiClaw 截图
val LightBackground = Color(0xFFF5F5F7)   // 全局浅灰底（介于卡片白和墙底之间）
val LightSurface    = Color(0xFFFFFFFF)   // 卡片白
val LightSurfaceVar = Color(0xFFF0F0F2)   // AI 气泡底
val LightBorder     = Color(0xFFE0E0E5)
val LightText       = Color(0xFF1C1C1E)   // 主标题
val LightTextMuted  = Color(0xFF8E8E93)   // 副标题

// 暗色 (备用)
val DarkBackground     = Color(0xFF0F0F12)
val DarkSurface        = Color(0xFF1A1A1F)
val DarkSurfaceVariant = Color(0xFF24242B)
val DarkBorder         = Color(0xFF2E2E36)
val DarkText           = Color(0xFFF5F5F7)
val DarkTextMuted      = Color(0xFFB8B8C0)   // bug.3 加深副标题，提高对比
