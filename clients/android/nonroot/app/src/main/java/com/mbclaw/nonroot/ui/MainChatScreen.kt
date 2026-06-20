package com.mbclaw.nonroot.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import com.mbclaw.nonroot.api.NetworkModule
import com.mbclaw.nonroot.viewmodel.ChatMessage
import com.mbclaw.nonroot.viewmodel.ChatViewModel

/**
 * MBclaw Lite 主界面 — 完整功能版
 *
 * 对接 MBclaw-Lite 服务端 API：
 *   - Agent 对话 (POST /agent/run)
 *   - 记忆搜索 (GET /search)
 *   - 健康检查 (GET /health/health)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(viewModel: ChatViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(uiState.serverUrl) }
    val listState = rememberLazyListState()

    // 自动滚动到底部
    LaunchedEffect(uiState.messages.size, uiState.isThinking) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // 初始问候
    LaunchedEffect(Unit) {
        if (uiState.messages.isEmpty()) {
            // 通过 VM 检查服务器后显示状态
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖 MBclaw", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        // 服务器连接指示灯
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (uiState.serverConnected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        ) {
                            Text(
                                if (uiState.serverConnected) "● 已连接" else "○ 未连接",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.serverConnected)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                actions = {
                    // 记忆搜索按钮
                    IconButton(onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.searchMemory(inputText)
                        }
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索记忆")
                    }
                    // 设置按钮
                    IconButton(onClick = {
                        serverUrlInput = uiState.serverUrl
                        showSettings = true
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )

            // 未连接警告条
            if (!uiState.serverConnected && uiState.messages.isNotEmpty()) {
                androidx.compose.animation.AnimatedVisibility(visible = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "⚠️ 未连接服务器 — 点击右上角⚙设置服务器地址",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 消息列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 欢迎卡片（消息为空时）
                if (uiState.messages.isEmpty()) {
                    item {
                        WelcomeCard(
                            serverConnected = uiState.serverConnected,
                            serverUrl = uiState.serverUrl,
                            onSettings = { showSettings = true },
                            onHealthCheck = { viewModel.checkServerHealth() },
                        )
                    }
                }

                items(uiState.messages.reversed()) { msg ->
                    ChatBubble(msg)
                }

                // 思考中
                if (uiState.isThinking) {
                    item {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("MBclaw 思考中...", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // 连接状态提示
                if (uiState.messages.isEmpty() && !uiState.serverConnected) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("⚠️ 服务端未连接", fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall)
                                Text("MBclaw 需要连接后端服务器才能工作。\n点击 ⚙ → 输入服务器地址 → 测试连接。",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // 输入栏
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 16.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("发送消息给 MBclaw...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }

    // 设置对话框
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("⚙ 服务器设置") },
            text = {
                Column {
                    Text("MBclaw 服务端地址", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        placeholder = { Text("http://47.83.2.188:8000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("由孟白打造的 AI 记忆助手",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.checkServerHealth()
                    }) {
                        Text("测试连接")
                    }
                    Button(onClick = {
                        viewModel.updateServerUrl(serverUrlInput)
                        showSettings = false
                    }) {
                        Text("保存")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun WelcomeCard(
    serverConnected: Boolean,
    serverUrl: String,
    onSettings: () -> Unit,
    onHealthCheck: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("🌟 欢迎使用 MBclaw",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "由一个18岁的打工人**孟白**耗时2个月打造。\n\n" +
                "💬 智能对话 — 基于 MiMo v2.5-pro (820亿token)\n" +
                "🧠 永久记忆 — 记住你说过的每一句话\n" +
                "🔍 记忆搜索 — 快速找回之前的讨论\n" +
                "🛠 工具调用 — 完整功能需 Root 版",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))

            // 服务器状态
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (serverConnected)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            ) {
                Row(
                    Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (serverConnected) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (serverConnected)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (serverConnected) "服务器已连接" else "未连接服务器",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(serverUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!serverConnected) {
                        TextButton(onClick = onSettings) { Text("设置") }
                        TextButton(onClick = onHealthCheck) { Text("重试") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    val isSystem = msg.role == "system"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = when {
            isUser -> Alignment.End
            isSystem -> Alignment.CenterHorizontally
            else -> Alignment.Start
        },
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            color = when {
                isUser -> MaterialTheme.colorScheme.primary
                isSystem -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                msg.isError -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
        ) {
            Column(Modifier.padding(12.dp)) {
                // 角色标签
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            isUser -> "🧑 你"
                            isSystem -> "📋 系统"
                            else -> "🤖 MBclaw"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser)
                            MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 记忆引用标签
                    if (msg.memoryRefs.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        ) {
                            Text("引用 ${msg.memoryRefs.size} 条记忆",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    msg.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
