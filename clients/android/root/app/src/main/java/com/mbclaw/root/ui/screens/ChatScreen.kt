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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

data class ChatMsg(val role: String, val content: String, val isError: Boolean = false)

/**
 * 聊天屏 — 接收外部 ChatViewModel
 *
 * 由 MBclawMainScreen 在最外层 remember(agent) { ChatViewModel(...) } 创建，
 * 整个 app 生命周期不销毁 → tab 切换不丢状态、重启接续上次会话
 */
@Composable
fun ChatScreen(vm: ChatViewModel) {
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.initIfNeeded() }
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部固定状态条（agent 运行时常驻）
        if (vm.isThinking.value) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(vm.agentStatus.value.ifBlank { "Agent 思考中…" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.weight(1f))
                    // Token 实时显示
                    if (vm.tokenStats.value.lastTurnIn > 0) {
                        Text("⚡ ↑${vm.tokenStats.value.lastTurnIn} ↓${vm.tokenStats.value.lastTurnOut} tok",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                   state = listState, reverseLayout = true) {
            items(vm.messages.reversed()) { msg -> ChatBubble(msg) }
        }

        Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = vm.inputText.value,
                    onValueChange = { vm.inputText.value = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(
                        if (vm.isThinking.value) "Agent 运行中，可点击右侧停止"
                        else "输入消息或指令..."
                    ) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.send() }),
                    maxLines = 4,
                    enabled = !vm.isThinking.value,
                    shape = RoundedCornerShape(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                if (vm.isThinking.value) {
                    FilledIconButton(
                        onClick = { vm.cancel() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Icon(Icons.Filled.Stop, "终止") }
                } else {
                    FilledIconButton(onClick = { vm.send() }) {
                        Icon(Icons.Filled.Send, "发送")
                    }
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
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp),
           horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
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
            shape = RoundedCornerShape(16.dp, 16.dp,
                                       if (isUser) 4.dp else 16.dp,
                                       if (isUser) 16.dp else 4.dp),
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
