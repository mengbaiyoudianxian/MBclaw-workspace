package com.mbclaw.root.ui

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.asImageBitmap
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

    // 路由栈 — 后进先出，保证「返回」回到上一页而不是桌面
    val routeStack = remember { mutableStateListOf("chat") }
    val route = routeStack.last()
    fun push(r: String) { if (routeStack.last() != r) routeStack.add(r) }
    fun pop(): Boolean = if (routeStack.size > 1) { routeStack.removeAt(routeStack.size - 1); true } else false

    var showSetup by remember { mutableStateOf(false) }
    var showHand by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showAllSessions by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 启动立即加载历史会话
    LaunchedEffect(Unit) { chatVM.initIfNeeded() }

    // BackHandler — 系统返回键统一处理（按 MiClaw 习惯）
    //   优先级：弹窗 > 抽屉 > 路由栈 pop > 默认退出
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showSetup -> showSetup = false
            showHand -> showHand = false
            showAbout -> showAbout = false
            showAllSessions -> showAllSessions = false
            drawerState.isOpen -> scope.launch { drawerState.close() }
            !pop() -> {
                // 已在根 (chat)，让系统处理（回桌面）
                (ctx as? android.app.Activity)?.moveTaskToBack(true)
            }
            else -> { /* pop 成功 */ }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = (route == "chat"),
        drawerContent = {
            ChatListDrawer(
                agent = agent,
                vm = chatVM,
                onOpen = { sid -> chatVM.openSession(sid); scope.launch { drawerState.close() } },
                onSettings = {
                    scope.launch { drawerState.close() }
                    push("settings")
                },
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
                onBack = { pop() },
                onSetupProvider = { showSetup = true },
                onOpenHand = { showHand = true },
                onOpenTools = { push("tools") },
                onOpenSessions = { showAllSessions = true },
            )
            "tools" -> ToolsPageWithBack(onBack = { pop() })
        }
    }

    if (showSetup) ProviderSetupScreen(settings = agent.settings, onDone = { showSetup = false })
    if (showHand) AgentHandScreen(onBack = { showHand = false })
    if (showAbout) AboutDialog(ctx = ctx, onDismiss = { showAbout = false })
    if (showAllSessions) AllSessionsSheet(
        agent = agent,
        currentSid = chatVM.sessionId.value,
        onDismiss = { showAllSessions = false },
        onOpen = { sid ->
            chatVM.openSession(sid)
            showAllSessions = false
            // 关回到 chat 页
            routeStack.clear()
            routeStack.add("chat")
        },
    )
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
private fun AboutDialog(ctx: android.content.Context, onDismiss: () -> Unit) {
    var showQrSheet by remember { mutableStateOf(false) }
    val logoBmp = remember {
        try { ctx.assets.open("donate/logo.png").use { android.graphics.BitmapFactory.decodeStream(it) } }
        catch (_: Exception) { null }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            if (logoBmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = logoBmp.asImageBitmap(),
                    contentDescription = "MBclaw",
                    modifier = Modifier.size(56.dp),
                )
            } else {
                Surface(shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("M", color = androidx.compose.ui.graphics.Color.White,
                             fontWeight = FontWeight.Bold,
                             style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        },
        title = { Text("MBclaw", textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                       modifier = Modifier.fillMaxWidth()) },
        text = {
            Column {
                Text("你的全生态 AI 助手",
                     textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                     modifier = Modifier.fillMaxWidth(),
                     color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                // 任务 10: MBclaw 行 → 下载页
                AboutRow("MBclaw", "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})  →  下载页") {
                    val i = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("http://121.199.57.195/"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                }
                // 任务 10: 显示 QQ 号
                AboutRow("作者 QQ", "1973054239 (点击复制)") {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("MBclaw QQ", "1973054239"))
                    android.widget.Toast.makeText(ctx, "已复制 QQ：1973054239",
                        android.widget.Toast.LENGTH_SHORT).show()
                }
                // 酷安主页
                AboutRow("酷安", "coolapk.com/u/26771405 · 关注作者") {
                    val i = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.coolapk.com/u/26771405"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                }
                // 任务 10: 友情赞助
                AboutRow("💖 友情赞助", "请作者喝杯咖啡") { showQrSheet = true }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )

    if (showQrSheet) DonateQRSheet(onDismiss = { showQrSheet = false })
}

@Composable
private fun AboutRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium,
                 fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.outline)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonateQRSheet(onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💖 友情赞助", textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                       modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("感谢支持，请扫码赞赏 ✨",
                     textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QrFromAsset(ctx, "donate/wechat.png", "微信",
                        androidx.compose.ui.graphics.Color(0xFF07C160))
                    QrFromAsset(ctx, "donate/alipay.jpg", "支付宝",
                        androidx.compose.ui.graphics.Color(0xFF1677FF))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun QrFromAsset(ctx: android.content.Context, path: String, label: String, tint: androidx.compose.ui.graphics.Color) {
    val bmp = remember(path) {
        try {
            ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) }
        } catch (_: Exception) { null }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(140.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.QrCode2, label, modifier = Modifier.size(64.dp), tint = tint)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

/** 任务 7: 全部会话搜索浮层 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AllSessionsSheet(
    agent: MBclawAgent,
    currentSid: String,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var sessions by remember { mutableStateOf(listOf<com.mbclaw.root.data.SessionRow>()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        sessions = try { agent.db.getSessions() } catch (_: Exception) { emptyList() }
    }
    val filtered = if (query.isBlank()) sessions
                   else sessions.filter { (it.title ?: "").contains(query, true) || it.id.contains(query, true) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp, 4.dp, 16.dp, 16.dp).fillMaxHeight(0.85f)) {
            Text("全部会话", fontWeight = FontWeight.Bold,
                 style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索标题或会话 ID") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Text("${filtered.size} / ${sessions.size}",
                 style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered) { row ->
                    SessionRowCard(
                        title = row.title ?: "新对话",
                        timestamp = row.updatedAt,
                        selected = row.id == currentSid,
                        onClick = { onOpen(row.id) },
                        onLongClick = {},
                    )
                }
            }
        }
    }
}
