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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mbclaw.root.agent.MBclawAgent
import com.mbclaw.root.ui.screens.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MBclawMainScreen() {
    val ctx = LocalContext.current
    val agent = remember { MBclawAgent(ctx.applicationContext as Application) }
    var tab by remember { mutableIntStateOf(0) }
    var showSetup by remember { mutableStateOf(!agent.settings.isConfigured()) }

    Scaffold(
        topBar = { TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically) { Text("🤖 MBclaw", fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Surface(shape = RoundedCornerShape(4.dp), color = if (agent.settings.isConfigured()) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)) { Text(if (agent.settings.isConfigured()) "⚡${agent.settings.modelName}" else "⚠未配置", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall) } } }, actions = { IconButton(onClick = { showSetup = true }) { Icon(Icons.Filled.Settings, "配置") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)) },
        bottomBar = { NavigationBar { listOf("对话" to Icons.Filled.Chat, "工具" to Icons.Filled.Build, "我的" to Icons.Filled.Person).forEachIndexed { i, (label, icon) -> NavigationBarItem(icon = { Icon(icon, label) }, label = { Text(label) }, selected = tab == i, onClick = { tab = i }) } } },
    ) { padding -> Box(Modifier.padding(padding)) { AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }) { when (it) { 0 -> ChatScreen(agent); 1 -> ToolsScreen(); 2 -> ProfileScreen(agent, onSetupProvider = { showSetup = true }) } } } }

    if (showSetup) ProviderSetupScreen(settings = agent.settings, onDone = { showSetup = false })
}
