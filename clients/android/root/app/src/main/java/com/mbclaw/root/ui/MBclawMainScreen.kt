package com.mbclaw.root.ui

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mbclaw.root.agent.MBclawAgent
import com.mbclaw.root.ui.screens.*

/**
 * MBclaw 主界面 — 70-80% 仿 MiClaw 风格
 *
 * 4-tab 底部导航：
 *   ① 对话  (ChatScreen)        — MiClaw 首页
 *   ② 工具  (ToolsScreen)        — MiClaw "工具市场"
 *   ③ 历史  (HistoryScreen)      — MiClaw "对话列表"
 *   ④ 我的  (ProfileScreen)       — MiClaw "个人中心"
 *
 * 顶部 AppBar 仿 MiClaw 风格：
 *   • 极简 Logo + 模型状态徽章
 *   • 右侧 设置 + 助手手势
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MBclawMainScreen() {
    val ctx = LocalContext.current
    val agent = remember { MBclawAgent(ctx.applicationContext as Application) }
    var tab by remember { mutableIntStateOf(0) }
    var showSetup by remember { mutableStateOf(!agent.settings.isConfigured()) }
    var showHand by remember { mutableStateOf(false) }
    var jumpToSession by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 仿 MiClaw 圆角 logo
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("M", fontWeight = FontWeight.Bold,
                                     color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("MBclaw", fontWeight = FontWeight.Bold,
                             style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (agent.settings.isConfigured())
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                if (agent.settings.isConfigured()) "⚡ ${agent.settings.modelName.take(18)}"
                                else "⚠ 未配置",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showHand = true }) {
                        Text("🦾", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { showSetup = true }) {
                        Icon(Icons.Filled.Settings, "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val tabs = listOf(
                    Quad("对话", Icons.Filled.Chat, Icons.Filled.Chat, 0),
                    Quad("工具", Icons.Filled.GridView, Icons.Filled.GridView, 1),
                    Quad("历史", Icons.Filled.History, Icons.Filled.History, 2),
                    Quad("我的", Icons.Filled.Person, Icons.Filled.Person, 3),
                )
                tabs.forEach { (label, sel, unsel, idx) ->
                    NavigationBarItem(
                        selected = tab == idx,
                        onClick = { tab = idx },
                        icon = { Icon(if (tab == idx) sel else unsel, label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }) {
                when (it) {
                    0 -> ChatScreen(agent)
                    1 -> ToolsScreen()
                    2 -> HistoryScreen(agent) { sid ->
                            jumpToSession = sid
                            tab = 0
                         }
                    3 -> ProfileScreen(agent, onSetupProvider = { showSetup = true })
                }
            }
        }
    }

    if (showSetup) ProviderSetupScreen(settings = agent.settings, onDone = { showSetup = false })
    if (showHand) com.mbclaw.root.ui.AgentHandScreen(onBack = { showHand = false })
}

/** 四元组：四元素装载  (selected icon, unselected icon, etc.) */
private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
