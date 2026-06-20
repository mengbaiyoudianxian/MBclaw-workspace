package com.mbclaw.nonroot.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mbclaw.nonroot.viewmodel.ChatViewModel
import com.mbclaw.nonroot.viewmodel.UIMessage
import com.mbclaw.nonroot.data.SessionRow

/**
 * 主界面 — 本地优先，零依赖服务器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(viewModel: ChatViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showSetup by remember { mutableStateOf(!viewModel.settings.isConfigured() || uiState.messages.isEmpty()) }
    var showSessions by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 自动滚动
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖 MBclaw", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = if (viewModel.settings.isConfigured())
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)) {
                            Text(
                                if (viewModel.settings.isConfigured()) "⚡${uiState.modelName}" else "⚠未配置",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showSessions = !showSessions }) {
                        Icon(Icons.Filled.Menu, "会话")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.searchMemory(inputText.ifBlank { "最近" }) }) {
                        Icon(Icons.Filled.Search, "记忆")
                    }
                    IconButton(onClick = { showSetup = true }) {
                        Icon(Icons.Filled.Settings, "配置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            // ── 侧边栏: 会话列表 ──
            AnimatedVisibility(visible = showSessions) {
                Surface(
                    modifier = Modifier.width(260.dp).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    tonalElevation = 2.dp,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("💬 会话", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.newSession() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Add, "新建", modifier = Modifier.size(18.dp))
                            }
                        }
                        Divider()
                        LazyColumn {
                            items(uiState.sessions) { session ->
                                SessionItem(session,
                                    selected = session.id == uiState.currentSessionId,
                                    onClick = {
                                        viewModel.switchSession(session.id)
                                        showSessions = false
                                    })
                            }
                        }
                    }
                }
            }

            // ── 主聊天区 ──
            Column(Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (uiState.messages.isEmpty()) {
                        item { WelcomeCard(onSetup = { showSetup = true }, viewModel = viewModel) }
                    }
                    items(uiState.messages.reversed()) { msg -> ChatBubble(msg) }
                    if (uiState.isThinking) {
                        item {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("MBclaw 思考中...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // ── 输入栏 ──
                Surface(tonalElevation = 3.dp, shadowElevation = 16.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = inputText, onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("发给 MBclaw...") },
                            shape = RoundedCornerShape(24.dp), maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (inputText.isNotBlank()) { viewModel.sendMessage(inputText); inputText = "" }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = {
                            if (inputText.isNotBlank()) { viewModel.sendMessage(inputText); inputText = "" }
                        }) {
                            Icon(Icons.Filled.Send, "发送")
                        }
                    }
                }
            }
        }
    }

    // ── 提供商配置弹窗 ──
    if (showSetup) {
        ProviderSetupScreen(settings = viewModel.settings, onDone = {
            viewModel.refreshProvider()
            showSetup = false
        })
    }
}

@Composable
private fun WelcomeCard(onSetup: () -> Unit, viewModel: ChatViewModel) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("🌟 MBclaw", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("由18岁的打工人孟白耗时2个月打造", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            FeatureRow("🔌", "独立运行", "不依赖服务器，直连大模型 API")
            FeatureRow("🧠", "本地记忆", "SQLite 存储，跨会话永不忘")
            FeatureRow("🌐", "25+ 提供商", "DeepSeek/OpenAI/智谱/阿里云/... 内置目录")
            FeatureRow("🔑", "你的 Key", "API Key 只存在你手机上")

            Spacer(Modifier.height(16.dp))
            Button(onClick = onSetup, modifier = Modifier.fillMaxWidth()) {
                Text(if (viewModel.settings.isConfigured()) "✅ 已配置，点击修改" else "⚡ 开始配置")
            }
        }
    }
}

@Composable
private fun FeatureRow(emoji: String, title: String, desc: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChatBubble(msg: UIMessage) {
    val isUser = msg.role == "user"
    Column(Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            color = when {
                isUser -> MaterialTheme.colorScheme.primary
                msg.isError -> MaterialTheme.colorScheme.errorContainer
                msg.role == "system" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(16.dp, 16.dp, if (isUser) 4.dp else 16.dp, if (isUser) 16.dp else 4.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when { isUser -> "🧑 你"; msg.role == "system" -> "📋"; else -> "🤖 MBclaw" },
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (msg.memoryRefs.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)) {
                            Text("🧠${msg.memoryRefs.size}", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(msg.content, style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun SessionItem(session: SessionRow, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
    ) {
        Text(session.title ?: session.id, modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}
