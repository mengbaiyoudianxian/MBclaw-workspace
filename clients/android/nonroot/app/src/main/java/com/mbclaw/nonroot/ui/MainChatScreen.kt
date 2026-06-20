package com.mbclaw.nonroot.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * MBclaw Lite 主聊天界面
 *
 * 设计：GPT风格（简洁白/暗切换 + 顶栏 + 输入框）+ MiClaw元素（智能功能按钮）
 * 品牌：MBclaw 标识 + "由一个18岁的打工人孟白创造" 自我介绍
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen() {
    val messages = remember { mutableStateListOf<LiteMessage>() }
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 初始问候
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            messages.add(LiteMessage(
                "assistant",
                "你好！我是 **MBclaw** 🌟\n\n" +
                "由一个18岁的打工人**孟白**耗时2个月打造。\n\n" +
                "我能帮你：\n" +
                "💬 智能对话，记住你说过的每句话\n" +
                "🔍 搜索记忆，快速找回之前的讨论\n" +
                "🛠 调用工具（需Root版获得完整功能）\n\n" +
                "有什么想聊的？"
            ))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖 MBclaw Lite", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { /* 设置 */ }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 消息列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages.reversed()) { msg ->
                    LiteChatBubble(msg)
                }
                if (isThinking) {
                    item {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("思考中...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // 快捷功能栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "🧠 记忆" to Icons.Filled.Search,
                    "🔍 搜索" to Icons.Filled.Search,
                    "⚡ 技能" to Icons.Filled.Star,
                    "📁 工具" to Icons.Filled.Settings,
                ).forEach { (label, icon) ->
                    SuggestionChip(
                        onClick = {
                            messages.add(LiteMessage("user", label))
                            scope.launch {
                                isThinking = true
                                kotlinx.coroutines.delay(1000)
                                messages.add(LiteMessage("assistant",
                                    "「${label}」功能正在开发中，敬请期待！"))
                                isThinking = false
                            }
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(icon, contentDescription = null, Modifier.size(14.dp)) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 输入栏 — GPT 风格底部输入
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
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                messages.add(LiteMessage("user", inputText))
                                val text = inputText; inputText = ""
                                scope.launch {
                                    isThinking = true
                                    kotlinx.coroutines.delay(800 + (Math.random() * 1500).toLong())
                                    messages.add(LiteMessage("assistant",
                                        "[MBclaw回复] 收到你的消息：「${text.take(50)}」。\n" +
                                        "我是MBclaw非Root版，基础AI对话正常。要获得完整的386工具调用能力，请安装Root版。"
                                    ))
                                    isThinking = false
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }
}

@Composable
fun LiteChatBubble(msg: LiteMessage) {
    val isUser = msg.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            color = if (isUser)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (isUser) "🧑 你" else "🤖 MBclaw",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(msg.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

data class LiteMessage(val role: String, val content: String)
