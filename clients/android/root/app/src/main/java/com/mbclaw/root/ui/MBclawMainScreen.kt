package com.mbclaw.root.ui

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mbclaw.root.BuildConfig
import com.mbclaw.root.agent.MBclawAgent
import com.mbclaw.root.ui.screens.*

/**
 * 主界面 — 4-tab 仿 MiClaw
 * ChatViewModel 在最外层创建，整个 app 生命周期单例
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MBclawMainScreen() {
    val ctx = LocalContext.current
    val agent = remember { MBclawAgent(ctx.applicationContext as Application) }
    val chatVM = remember { ChatViewModel(ctx.applicationContext, agent) }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSetup by remember { mutableStateOf(!agent.settings.isConfigured()) }
    var showHand by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { chatVM.initIfNeeded() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                if (agent.settings.isConfigured())
                                    "v${BuildConfig.VERSION_NAME} · ⚡${agent.settings.modelName.take(14)}"
                                else "v${BuildConfig.VERSION_NAME} · ⚠ 未配置",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                actions = {
                    if (tab == 0) {
                        IconButton(onClick = { chatVM.newSession() }) {
                            Icon(Icons.Filled.Add, "新对话")
                        }
                        IconButton(onClick = { chatVM.clearCurrentMessages() }) {
                            Icon(Icons.Filled.DeleteSweep, "清空当前")
                        }
                    }
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
            // 不用 AnimatedContent，直接 when，避免 @Composable context 问题
            when (tab) {
                0 -> ChatScreen(chatVM)
                1 -> ToolsScreen()
                2 -> HistoryScreen(
                        agent,
                        onOpenSession = { sid ->
                            chatVM.openSession(sid)
                            tab = 0
                        },
                        onDeleteSession = { sid -> chatVM.deleteSession(sid) },
                     )
                3 -> ProfileScreen(agent, onSetupProvider = { showSetup = true })
            }
        }
    }

    if (showSetup) ProviderSetupScreen(settings = agent.settings, onDone = { showSetup = false })
    if (showHand) com.mbclaw.root.ui.AgentHandScreen(onBack = { showHand = false })
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
