package com.mbclaw.root.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mbclaw.root.BuildConfig
import com.mbclaw.root.agent.MBclawAgent
import com.mbclaw.root.agent.PermissionTier
import com.mbclaw.root.agent.RootBootstrap
import com.mbclaw.root.data.SecureVault
import com.mbclaw.root.model.ProviderCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(agent: MBclawAgent, onSetupProvider: () -> Unit) {
    val ctx = LocalContext.current
    val s = agent.settings
    val p = ProviderCatalog.find(s.providerId)
    var utopia by remember { mutableStateOf(s.utopiaEnabled) }
    var sync by remember { mutableStateOf(s.serverSyncEnabled) }
    var url by remember { mutableStateOf(s.serverUrl) }
    var showTokens by remember { mutableStateOf(false) }
    val tier = remember { PermissionTier.get(ctx) }
    val (granted, total) = remember { RootBootstrap.status(ctx) }
    val vaultCount = remember { SecureVault.count(ctx) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── 头像卡 ──
        Card(shape = RoundedCornerShape(16.dp),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("M", style = MaterialTheme.typography.headlineMedium,
                             color = MaterialTheme.colorScheme.onPrimary,
                             fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("MBclaw Root", style = MaterialTheme.typography.titleMedium,
                         fontWeight = FontWeight.SemiBold)
                    Text("v${BuildConfig.VERSION_NAME} · 独立架构",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // ── 权限状态 ──
        Card(shape = RoundedCornerShape(16.dp),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    Text("🛡 权限状态", fontWeight = FontWeight.SemiBold,
                         style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(50),
                            color = if (tier.hasRoot) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.errorContainer) {
                        Text(if (tier.hasRoot) "ROOT 已授权" else "无 Root",
                             modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                             style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("$granted / $total 危险权限已授予",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurface)
                LinearProgressIndicator(
                    progress = { granted.toFloat() / total.toFloat().coerceAtLeast(1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                Row {
                    Text("根 ${if (tier.hasRoot) "✅" else "❌"} · ADB ${if (tier.hasAdb) "✅" else "❌"} · 无障碍 ${if (tier.hasAccessibility) "✅" else "❌"}",
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { RootBootstrap.resetAndRerun(ctx) }) {
                        Text("重新获取", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // ── AI 引擎 ──
        Card(shape = RoundedCornerShape(16.dp),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("🤖 AI 引擎", style = MaterialTheme.typography.titleSmall,
                     fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row {
                    AssistChip(onClick = onSetupProvider,
                        label = { Text(p?.name ?: "未选择") })
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = onSetupProvider,
                        label = { Text(s.modelName.ifBlank { "未配置" }) })
                }
                if (!s.isConfigured()) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onSetupProvider, modifier = Modifier.fillMaxWidth()) {
                        Text("⚡ 配置 AI 提供商")
                    }
                }
            }
        }

        // ── 乌托邦计划（文案更新） ──
        Card(shape = RoundedCornerShape(16.dp),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("🏙 乌托邦计划", style = MaterialTheme.typography.titleSmall,
                     fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("贡献你的非隐私数据改善所有人的 AI 体验。开启后你的 token 消耗可能会提高昨日的 1%-5% 左右，但性能、能力、记忆力功能都被母体提高到 100%。",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     lineHeight = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = utopia, onCheckedChange = { utopia = it; s.utopiaEnabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text("开启乌托邦", fontWeight = FontWeight.SemiBold)
                }
                AnimatedVisibility(visible = utopia) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text("☁ 多端同步", style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = sync, onCheckedChange = { sync = it; s.serverSyncEnabled = it })
                            Spacer(Modifier.width(8.dp))
                            Text("启用多端同步")
                        }
                        if (sync) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(value = url, onValueChange = { url = it; s.serverUrl = it },
                                label = { Text("服务器地址") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true)
                        }
                    }
                }
            }
        }

        // ── Token 统计 ──
        Card(modifier = Modifier.clickable { showTokens = true },
             shape = RoundedCornerShape(16.dp),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Analytics, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("📊 Token 消耗统计", fontWeight = FontWeight.SemiBold,
                         style = MaterialTheme.typography.titleSmall)
                    Text("查看每日 / 每个 provider 的 token 用量",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.outline)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
            }
        }

        // ── 隐私 Vault ──
        Card(shape = RoundedCornerShape(16.dp),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("🔐 隐私保险箱", fontWeight = FontWeight.SemiBold,
                         style = MaterialTheme.typography.titleSmall)
                    Text("$vaultCount 项 · AES-256-GCM · 设备指纹派生",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // ── 关于 ──
        Card(shape = RoundedCornerShape(16.dp),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("📝 关于", style = MaterialTheme.typography.titleSmall,
                     fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("MBclaw 由 18 岁打工人孟白耗时 2 个月独立打造。\n独立架构 — 直连大模型 API。\n版本 ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\nGitHub: github.com/mengbaiyoudianxian",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showTokens) {
        TokenStatsDialog(onDismiss = { showTokens = false })
    }
}

@Composable
private fun TokenStatsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Analytics, null) },
        title = { Text("Token 消耗统计") },
        text = {
            Column {
                Text("本次会话累计:", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                // TODO: 从 ChatViewModel.tokenStats 拉真实数据
                Text("⚠️ 详细统计仍在采集中，下个版本支持：", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text("• 每日 token 消耗\n• 按 provider 拆分\n• 按工具调用拆分\n• 月度账单估算",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.outline)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("好的") } }
    )
}
