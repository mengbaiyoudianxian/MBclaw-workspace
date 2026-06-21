package com.mbclaw.root.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mbclaw.root.agent.MBclawAgent

/**
 * 历史 Tab — 仿 MiClaw 「对话列表」
 *  • 卡片化、圆角 16dp、点击进入、长按删除
 *  • 搜索栏
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    agent: MBclawAgent,
    onOpenSession: (String) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
) {
    var sessions by remember { mutableStateOf(listOf<com.mbclaw.root.data.SessionRow>()) }
    var searchText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        sessions = try { agent.db.getSessions() } catch (_: Exception) { emptyList() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            placeholder = { Text("搜索历史对话") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
        )
        val filtered = if (searchText.isBlank()) sessions
                       else sessions.filter { (it.title ?: "").contains(searchText, ignoreCase = true) }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.History, null, Modifier.size(48.dp),
                         tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    Text("暂无对话历史", color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(6.dp))
                    Text("发起一段对话后会出现在这里 · 长按可删除",
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { row ->
                    val interaction = remember { MutableInteractionSource() }
                    val indication = androidx.compose.foundation.LocalIndication.current
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = interaction,
                                indication = indication,
                                onClick = { onOpenSession(row.id) },
                                onLongClick = { confirmDelete = row.id },
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("💬", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(row.title ?: "未命名对话",
                                     fontWeight = FontWeight.SemiBold,
                                     style = MaterialTheme.typography.titleSmall,
                                     color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(2.dp))
                                Text(java.text.SimpleDateFormat("MM-dd HH:mm").format(java.util.Date(row.updatedAt)),
                                     style = MaterialTheme.typography.labelSmall,
                                     color = MaterialTheme.colorScheme.outline)
                            }
                            Icon(Icons.Filled.ChevronRight, null,
                                 tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { sid ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Filled.DeleteForever, null,
                          tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除这条对话?") },
            text = { Text("删除后无法恢复，确认继续?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSession(sid)
                        confirmDelete = null
                        refreshTrigger++
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

