package com.mbclaw.root.ui.screens

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
import com.mbclaw.root.agent.MBclawAgent
import kotlinx.coroutines.launch

data class ChatMsg(val role: String, val content: String, val isError: Boolean = false)

@Composable
fun ChatScreen(agent: MBclawAgent) {
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var inputText by remember { mutableStateOf("") }
    val isThinking by agent.isThinking.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        agent.initSession()
        if (messages.isEmpty()) {
            messages.add(ChatMsg("assistant",
                "🌟 MBclaw Root v0.3.0\n由18岁的打工人孟白耗时2个月独立打造。\n" +
                "当前: ${agent.settings.modelName}" +
                (if (!agent.settings.isConfigured()) "\n⚠️ 请先配置API提供商" else "") +
                "\n\n386工具就绪 | 本地记忆激活 | 独立架构\n试试说「help」查看工具列表？"))
        }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), state = listState, reverseLayout = true) {
            items(messages.reversed()) { msg -> ChatBubble(msg) }
            if (isThinking) item { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("思考中...", style = MaterialTheme.typography.bodySmall) } }
        }
        Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f), placeholder = { Text("输入消息...") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (inputText.isNotBlank()) { messages.add(ChatMsg("user", inputText)); val t = inputText; inputText = ""; scope.launch { messages.add(ChatMsg("assistant", agent.chat(t))) } } }), maxLines = 4)
                Spacer(Modifier.width(4.dp))
                FilledIconButton(onClick = { if (inputText.isNotBlank()) { messages.add(ChatMsg("user", inputText)); val t = inputText; inputText = ""; scope.launch { messages.add(ChatMsg("assistant", agent.chat(t))) } } }) { Icon(Icons.Filled.Send, "发送") }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMsg) {
    val isUser = msg.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(modifier = Modifier.widthIn(max = 320.dp), color = when { isUser -> MaterialTheme.colorScheme.primary; msg.isError -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surfaceVariant }, shape = RoundedCornerShape(16.dp, 16.dp, if (isUser) 4.dp else 16.dp, if (isUser) 16.dp else 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(if (isUser) "🧑 你" else "🤖 MBclaw", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(msg.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
