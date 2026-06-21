package com.mbclaw.root.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mbclaw.root.agent.MBclawAgent

/**
 * 历史 Tab — 仿 MiClaw 的「对话列表」
 * 卡片化、圆角 16dp、米白主色、长按删除（roadmap）
 */
@Composable
fun HistoryScreen(agent: MBclawAgent, onOpenSession: (String) -> Unit = {}) {
    var sessions by remember { mutableStateOf(listOf<com.mbclaw.root.data.SessionRow>()) }
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
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
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenSession(row.id) },
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
                                     style = MaterialTheme.typography.titleSmall)
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
}
