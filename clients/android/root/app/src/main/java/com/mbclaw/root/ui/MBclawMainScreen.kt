package com.mbclaw.root.ui

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.launch
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
import com.mbclaw.root.BuildConfig
import com.mbclaw.root.agent.MBclawAgent
import com.mbclaw.root.ui.screens.*

/**
 * MBclawMainScreen — 仿 MiClaw 风格
 *
 * 主屏: 单一聊天页 (无 4-tab)
 * 顶栏: ☰ 抽屉 | 中央标题 | 右上 ⓘ 信息
 * 抽屉: 聊天列表 + 添加助手 + 设置入口
 * 设置: 二级页（账号 / 模型 / 工具 / 智能手 / Token / 隐私 / 清除 / 版本）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MBclawMainScreen() {
    val ctx = LocalContext.current
    val agent = remember { MBclawAgent(ctx.applicationContext as Application) }
    val chatVM = remember { ChatViewModel.get(ctx.applicationContext, agent) }

    var route by remember { mutableStateOf("chat") }   // chat | settings | tools | history
    var showSetup by remember { mutableStateOf(false) }
    var showHand by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 启动立即加载历史会话
    LaunchedEffect(Unit) { chatVM.initIfNeeded() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = (route == "chat"),
        drawerContent = {
            ChatListDrawer(
                agent = agent,
                vm = chatVM,
                onOpen = { sid -> chatVM.openSession(sid); scope.launch { drawerState.close() } },
                onSettings = { route = "settings"; scope.launch { drawerState.close() } },
            )
        }
    ) {
        when (route) {
            "chat" -> ChatPage(
                vm = chatVM,
                agent = agent,
                onMenuClick = { scope.launch { drawerState.open() } },
                onInfoClick = { showAbout = true },
            )
            "settings" -> SettingsPage(
                agent = agent,
                onBack = { route = "chat" },
                onSetupProvider = { showSetup = true },
                onOpenHand = { showHand = true },
                onOpenTools = { route = "tools" },
            )
            "tools" -> ToolsPageWithBack(onBack = { route = "settings" })
        }
    }

    if (showSetup) ProviderSetupScreen(settings = agent.settings, onDone = { showSetup = false })
    if (showHand) AgentHandScreen(onBack = { showHand = false })
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatPage(
    vm: ChatViewModel,
    agent: MBclawAgent,
    onMenuClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MBclaw", fontWeight = FontWeight.SemiBold,
                             style = MaterialTheme.typography.titleMedium)
                        Text("内容由 AI 生成",
                             style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.outline)
                    }
                },
                navigationIcon = {
                    // 左侧抽屉按钮：MiClaw 是圆形头像，我们用 ☰
                    IconButton(onClick = onMenuClick,
                        modifier = Modifier.padding(8.dp)) {
                        Surface(shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Menu, "聊天列表",
                                     modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                },
                actions = {
                    // bug.4 — 保留 + (新对话), 删除 🗑️
                    // bug.4 — 智能手不再放这里，转移到设置
                    IconButton(onClick = { vm.newSession() }) {
                        Icon(Icons.Filled.Add, "新对话")
                    }
                    IconButton(onClick = onInfoClick) {
                        Surface(shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Info, "关于",
                                     modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            ChatScreen(vm)
        }
    }
}

@Composable
private fun ChatListDrawer(
    agent: MBclawAgent,
    vm: ChatViewModel,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    var sessions by remember { mutableStateOf(listOf<com.mbclaw.root.data.SessionRow>()) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var trigger by remember { mutableStateOf(0) }
    LaunchedEffect(trigger) {
        sessions = try { agent.db.getSessions() } catch (_: Exception) { emptyList() }
    }
    val ctx = LocalContext.current

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(0.82f),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 标题
            Row(
                Modifier.fillMaxWidth().padding(20.dp, 24.dp, 20.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("聊天列表",
                     style = MaterialTheme.typography.headlineSmall,
                     fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    vm.newSession()
                    trigger++
                }) { Icon(Icons.Filled.AddCircleOutline, "新对话") }
            }
            // 列表
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(sessions) { row ->
                    val selected = row.id == vm.sessionId.value
                    SessionRowCard(
                        title = row.title ?: "新对话",
                        timestamp = row.updatedAt,
                        selected = selected,
                        onClick = { onOpen(row.id) },
                        onLongClick = { confirmDelete = row.id },
                    )
                }
                if (sessions.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp),
                            contentAlignment = Alignment.Center) {
                            Text("暂无对话，点击右上 + 新建",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            HorizontalDivider()
            // 底部：添加助手 + 设置
            DrawerBottomItem(Icons.Filled.PersonAdd, "添加助手") {
                android.widget.Toast.makeText(ctx, "助手系统下版本开放", android.widget.Toast.LENGTH_SHORT).show()
            }
            DrawerBottomItem(Icons.Filled.Settings, "设置") { onSettings() }
            Spacer(Modifier.height(8.dp))
        }
    }

    confirmDelete?.let { sid ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Filled.DeleteForever, null,
                          tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除这条对话?") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteSession(sid)
                        confirmDelete = null
                        trigger++
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRowCard(
    title: String,
    timestamp: Long,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = indication,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(50),
                    color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("💬", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold,
                     maxLines = 1,
                     overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                     style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
                Text(formatTime(timestamp),
                     style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun DrawerBottomItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(20.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null,
             tint = MaterialTheme.colorScheme.outline)
    }
}

private fun formatTime(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86400_000L -> "${diff / 3600_000L} 小时前"
        else -> java.text.SimpleDateFormat("MM-dd HH:mm").format(java.util.Date(ts))
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MBclaw") },
        text = {
            Column {
                Text("你的全生态 AI 助手", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("MBclaw 是 18 岁打工人孟白独立开发的智能体。\n" +
                     "• 长期记忆，越用越懂你\n" +
                     "• Root 通道，可控手机一切\n" +
                     "• 84 个工具，仿 MiClaw 命名\n" +
                     "• 内置后端 47.83.2.188\n\n" +
                     "版本: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                     style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("好的") } }
    )
}
