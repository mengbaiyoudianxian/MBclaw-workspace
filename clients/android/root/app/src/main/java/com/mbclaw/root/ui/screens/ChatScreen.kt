package com.mbclaw.root.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mbclaw.root.agent.MBclawAgent
import com.mbclaw.root.agent.AgentLoop
import kotlinx.coroutines.launch

data class ChatMsg(val role: String, val content: String, val isError: Boolean = false)

@Composable
fun ChatScreen(agent: MBclawAgent) {
    val ctx = LocalContext.current
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var agentStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val agentLoop = remember { AgentLoop(ctx, agent.db, agent.settings) }
    var sessionId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        agent.initSession()
        val prevSid = agent.db.getLastSessionId()
        if (prevSid != null) {
            sessionId = prevSid
            agent.db.getMessages(prevSid).forEach { m ->
                messages.add(ChatMsg(m.role, m.content))
            }
        } else {
            sessionId = agent.db.createSession("新对话")
        }
        if (messages.isEmpty()) {
            messages.add(ChatMsg("assistant",
                "🌟 MBclaw Root v2.0\n由18岁的打工人孟白耗时2个月独立打造。\n" +
                "当前: ${agent.settings.modelName}" +
                (if (!agent.settings.isConfigured()) "\n⚠️ 请先配置API提供商" else "") +
                "\n\n🛠 31个工具就绪 | 🧠 Hermes记忆 | su通道\n" +
                "试试说「打开飞行模式」或「截图」？"))
        }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(0) }

    fun doSend() {
        if (inputText.isBlank() || isThinking) return
        val text = inputText; inputText = ""
        messages.add(ChatMsg("user", text))
        isThinking = true
        agentStatus = "🤖 启动中…"
        scope.launch {
            try {
                val reply = agentLoop.run(text, sessionId, maxTurns = 20) { status ->
                    agentStatus = status
                }
                messages.add(ChatMsg("assistant", reply))
            } catch (e: Exception) {
                messages.add(ChatMsg("assistant", "❌ ${e.message}", isError = true))
            }
            isThinking = false
            agentStatus = ""
        }
    }

    fun doCancel() {
        agentLoop.cancel()
        agentStatus = "⏹ 终止中…"
    }

    Column(Modifier.fillMaxSize()) {
        // ── 顶部固定状态条 (agent 运行时常驻) ──
        if (isThinking) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(agentStatus.ifBlank { "Agent 思考中…" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), state = listState, reverseLayout = true) {
            items(messages.reversed()) { msg -> ChatBubble(msg) }
        }
        Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = inputText, onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (isThinking) "Agent 运行中，可点击右侧停止" else "输入消息或指令...") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { doSend() }),
                    maxLines = 4,
                    enabled = !isThinking,
                )
                Spacer(Modifier.width(4.dp))
                if (isThinking) {
                    FilledIconButton(
                        onClick = { doCancel() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                    ) { Icon(Icons.Filled.Stop, "终止") }
                } else {
                    FilledIconButton(onClick = { doSend() }) { Icon(Icons.Filled.Send, "发送") }
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(msg: ChatMsg) {
    val isUser = msg.role == "user"
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun copyToClip() {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("MBclaw", msg.content))
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = { copyToClip() },
                ),
            color = when {
                isUser -> MaterialTheme.colorScheme.primary
                msg.isError -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(16.dp, 16.dp, if (isUser) 4.dp else 16.dp, if (isUser) 16.dp else 4.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (isUser) "🧑 你" else "🤖 MBclaw",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    msg.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                TextButton(onClick = { copyToClip() }, contentPadding = PaddingValues(4.dp)) {
                    Text("📋 复制", style = MaterialTheme.typography.labelSmall,
                         color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                 else MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
