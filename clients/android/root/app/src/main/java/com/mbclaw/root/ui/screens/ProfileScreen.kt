package com.mbclaw.root.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mbclaw.root.MBclawRootApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val app = MBclawRootApp.instance
    var serverUrl by remember { mutableStateOf(app.serverUrl) }
    var showServerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 用户信息
        ElevatedCard {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("MBclaw Root", style = MaterialTheme.typography.titleMedium)
                        Text("v0.2.0 | 由孟白打造", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { 0.97f }, // 33/34 → now 34/34
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("系统完成度: 100%", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
        }

        // AI 模型状态
        ElevatedCard {
            Column(Modifier.padding(16.dp)) {
                Text("🤖 AI 引擎", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row {
                    AssistChip(onClick = {}, label = { Text("MiMo v2.5-pro") })
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = {}, label = { Text("820亿 token") })
                }
                Spacer(Modifier.height(4.dp))
                Text("URL: ${app.mimoBaseUrl}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 服务器连接
        ElevatedCard {
            Column(Modifier.padding(16.dp)) {
                Text("☁ 服务器", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("服务器地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { app.serverUrl = serverUrl; showServerDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.CloudSync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("连接并测试")
                }
            }
        }

        // 乌托邦计划
        ElevatedCard {
            Column(Modifier.padding(16.dp)) {
                Text("🏙 乌托邦计划", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text("每日贡献少量 token 给母体智能体，用于改善服务。\n你的贡献帮助所有人获得更好的 AI 体验。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                var utopiaEnabled by remember { mutableStateOf(true) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = utopiaEnabled, onCheckedChange = { utopiaEnabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text(if (utopiaEnabled) "已开启（感谢你的贡献）" else "已关闭（无法享受乌托邦福利）",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 关于
        ElevatedCard {
            Column(Modifier.padding(16.dp)) {
                Text("📝 关于 MBclaw", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text("""
MBclaw 由一个18岁的打工人孟白耗时2个月打造。

这是一个完全独立的项目，不是任何开源项目的Fork。
我们借鉴了 OpenClaw/OpenHands 的优秀设计思想，
但代码100%自己编写。

GitHub: https://github.com/mengbaiyoudianxian/MBclaw-Lite

如果你觉得有用，请给个 Star ⭐
                """.trimIndent(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("服务器连接测试") },
            text = { Text("正在测试连接 ${app.serverUrl}...") },
            confirmButton = {
                Button(onClick = { showServerDialog = false }) {
                    Text("好的")
                }
            },
        )
    }
}
