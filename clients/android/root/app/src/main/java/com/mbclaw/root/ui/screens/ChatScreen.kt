package com.mbclaw.root.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mbclaw.root.agent.MBclawAgent
import kotlinx.coroutines.launch

/**
 * 对话屏幕 — 保留 MiClaw 原有的对话风格，但 AI 后端已替换为 MBclaw
 *
 * 功能：
 *  - 文本对话（MiMo v2.5-pro + MBclaw 记忆系统）
 *  - 语音输入（长按录音，松开发送）
 *  - MBclaw 品牌问候语
 *  - 记忆引用显示
 */
@Composable
fun ChatScreen() {
    val agent = remember { MBclawAgent() }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    val isListening by agent.isListening.collectAsState()
    val isThinking by agent.isThinking.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 初始问候
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            messages.add(ChatMessage(
                role = "assistant",
                content = "你好！我是 MBclaw 🌟\n\n" +
                        "由一个18岁的打工人孟白耗时2个月打造。\n" +
                        "我不仅能聊天，还能：\n" +
                        "🔧 直接调用手机的386个工具（WiFi/蓝牙/短信/相机...）\n" +
                        "🧠 永远记住你的偏好和项目进度\n" +
                        "🛡 本地沙箱运行危险指令\n" +
                        "☁ 连接云服务器，无需VPN\n\n" +
                        "有什么我能帮你的？"
            ))
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 消息列表
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            state = listState,
            reverseLayout = true,
        ) {
            items(messages.reversed()) { msg ->
                ChatBubble(msg)
            }
            if (isThinking) {
                item {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("MBclaw 思考中...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // 输入栏
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
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
                    placeholder = { Text("输入消息或按住语音...") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            messages.add(ChatMessage("user", inputText))
                            val text = inputText; inputText = ""
                            scope.launch {
                                val reply = agent.chat(text)
                                messages.add(ChatMessage("assistant", reply))
                            }
                        }
                    }),
                    maxLines = 4,
                )

                Spacer(Modifier.width(4.dp))

                // 语音按钮
                FilledIconButton(
                    onClick = {
                        if (isListening) agent.stopListening()
                        else agent.startListening { text ->
                            messages.add(ChatMessage("user", text))
                            scope.launch {
                                val reply = agent.chat(text)
                                messages.add(ChatMessage("assistant", reply))
                            }
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isListening)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isListening) "停止" else "语音",
                    )
                }

                Spacer(Modifier.width(4.dp))

                // 发送按钮
                FilledIconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            messages.add(ChatMessage("user", inputText))
                            val text = inputText; inputText = ""
                            scope.launch {
                                val reply = agent.chat(text)
                                messages.add(ChatMessage("assistant", reply))
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

@Composable
fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = if (isUser)
            MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isUser) "🧑 你" else "🤖 MBclaw",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary,
                )
                if (!isUser && msg.memoryRefs.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text("引用记忆", style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(msg.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

data class ChatMessage(
    val role: String,
    val content: String,
    val memoryRefs: List<String> = emptyList(),
)
