package com.mbclaw.root.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mbclaw.root.agent.MBclawAgent
import com.mbclaw.root.model.ProviderCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(agent: MBclawAgent, onSetupProvider: () -> Unit) {
    val s = agent.settings; val p = ProviderCatalog.find(s.providerId)
    var utopia by remember { mutableStateOf(s.utopiaEnabled) }
    var sync by remember { mutableStateOf(s.serverSyncEnabled) }
    var url by remember { mutableStateOf(s.serverUrl) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElevatedCard { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Person, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column { Text("MBclaw Root", style = MaterialTheme.typography.titleMedium); Text("v0.3.0 | 独立架构 | 孟白打造", style = MaterialTheme.typography.bodySmall) } } } }

        ElevatedCard { Column(Modifier.padding(16.dp)) { Text("🤖 AI 引擎", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Row { AssistChip(onClick = {}, label = { Text(p?.name ?: "未配置") }); Spacer(Modifier.width(8.dp)); AssistChip(onClick = {}, label = { Text(s.modelName) }) }; if (!s.isConfigured()) { Spacer(Modifier.height(8.dp)); Button(onClick = onSetupProvider, modifier = Modifier.fillMaxWidth()) { Text("⚡ 配置 AI 提供商") } } } }

        ElevatedCard { Column(Modifier.padding(16.dp)) {
            Text("🏙 乌托邦计划", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp))
            Text("贡献 token 改善所有人的 AI 体验。开启后可上传 Key 实现多端同步。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = utopia, onCheckedChange = { utopia = it; s.utopiaEnabled = it }); Spacer(Modifier.width(8.dp)); Text("开启乌托邦", fontWeight = FontWeight.Bold) }
            AnimatedVisibility(visible = utopia) { Column { Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp)); Text("☁ 云端同步", style = MaterialTheme.typography.labelSmall); Spacer(Modifier.height(4.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = sync, onCheckedChange = { sync = it; s.serverSyncEnabled = it }); Spacer(Modifier.width(8.dp)); Text("启用多端同步") }; if (sync) { Spacer(Modifier.height(4.dp)); OutlinedTextField(value = url, onValueChange = { url = it; s.serverUrl = it }, label = { Text("服务器地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(4.dp)); Text(if (s.canUploadKey()) "✅ 已授权：Key 加密上传" else "⚠️ Key 仅存本地", style = MaterialTheme.typography.labelSmall, color = if (s.canUploadKey()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } } }
        } }

        ElevatedCard { Column(Modifier.padding(16.dp)) { Text("📝 关于", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("MBclaw 由18岁打工人孟白耗时2个月独立打造。\n独立架构 — 直连大模型API，不依赖中间服务器。\nGitHub: github.com/mengbaiyoudianxian", style = MaterialTheme.typography.bodySmall) } }
    }
}
