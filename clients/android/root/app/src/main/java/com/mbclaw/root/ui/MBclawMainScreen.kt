package com.mbclaw.root.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mbclaw.root.ui.screens.*

/**
 * MBclaw Root 主界面
 *
 * 保留 70% MiClaw UI 结构（底部3Tab + 顶部状态栏 + 语音按钮）
 * Tab1: 助手对话（原MiClaw对话界面，后端替换为MBclaw）
 * Tab2: 工具市场（原有的MiClaw工具能力 + MBclaw记忆搜索）
 * Tab3: 我的（用户配置 + 服务器连接 + 乌托邦计划）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MBclawMainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("对话", "工具", "我的")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖 MBclaw", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ) { Text("Root", style = MaterialTheme.typography.labelSmall) }
                    }
                },
                actions = {
                    IconButton(onClick = { /* 语音输入 */ }) {
                        Icon(Icons.Filled.Mic, contentDescription = "语音")
                    }
                    IconButton(onClick = { /* 通知 */ }) {
                        Icon(Icons.Outlined.Notifications, contentDescription = "通知")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, label ->
                    val icon = when (index) {
                        0 -> if (selectedTab == 0) Icons.Filled.Chat else Icons.Outlined.Chat
                        1 -> if (selectedTab == 1) Icons.Filled.Build else Icons.Outlined.Build
                        else -> if (selectedTab == 2) Icons.Filled.Person else Icons.Outlined.Person
                    }
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AnimatedContent(selectedTab, transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }) { tab ->
                when (tab) {
                    0 -> ChatScreen()
                    1 -> ToolsScreen()
                    2 -> ProfileScreen()
                }
            }
        }
    }
}
