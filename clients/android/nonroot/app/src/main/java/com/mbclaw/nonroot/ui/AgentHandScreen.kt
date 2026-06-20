package com.mbclaw.nonroot.ui

import android.app.Application
import android.graphics.Point
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.mbclaw.nonroot.hand.AgentHand
import com.mbclaw.nonroot.hand.HandMode
import com.mbclaw.nonroot.data.UserSettings
import kotlinx.coroutines.launch

/**
 * 智能体之手 — 控制面板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentHandScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { UserSettings(context) }
    val hand = remember {
        AgentHand(context, settings) { point -> true }
    }
    var selectedMode by remember { mutableStateOf(HandMode.BALANCE) }
    var isCalibrated by remember { mutableStateOf(hand.calibration.isCalibrated()) }
    var stats by remember { mutableStateOf(hand.getStats()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🦾 智能体之手", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 状态总览 ──
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("📊 系统状态", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        AssistChip(onClick = {}, label = { Text("${stats["screen_size"]}") })
                        Spacer(Modifier.width(8.dp))
                        AssistChip(onClick = {}, label = {
                            Text(if (isCalibrated) "✅ 已标定" else "⚠️ 未标定")
                        })
                    }
                    Spacer(Modifier.height(4.dp))
                    val rate = (stats["success_rate"] as? Float) ?: 0f
                    Text("成功率: ${"%.1f".format(rate * 100)}%")
                    LinearProgressIndicator(progress = { rate }, modifier = Modifier.fillMaxWidth())
                }
            }

            // ── 模式选择 ──
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("⚡ 运行模式", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HandMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode; hand.setMode(mode) },
                                label = {
                                    Text(when (mode) {
                                        HandMode.SPEED -> "⚡极速"
                                        HandMode.BALANCE -> "⚖均衡"
                                        HandMode.PRECISE -> "🎯高精"
                                    })
                                },
                            )
                        }
                    }
                    Text(
                        when (selectedMode) {
                            HandMode.SPEED -> "3×4网格 | 0轮精定位 | 模糊优先"
                            HandMode.BALANCE -> "4×6网格 | 1轮精定位 | 模糊辅助"
                            HandMode.PRECISE -> "6×8网格 | 2轮精定位 | 模糊关闭"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── 关键词库 ──
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("📝 关键词库 (${stats["keywords_count"] ?: 0}个)", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("内置12类100+常用操作词，自动学习扩充",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ── 方法统计 ──
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("📈 方法命中率", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val methodStats = stats["method_stats"] as? Map<String, Pair<Int, Int>> ?: emptyMap()
                    methodStats.forEach { (method, pair) ->
                        val (success, total) = pair
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(when (method) { "memory"->"🧠记忆"; "fuzzy"->"⚡模糊"; "fine"->"🎯精定位"; "coarse"->"📐粗筛"; else->method },
                                modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(progress = { success.toFloat() / maxOf(total, 1) },
                                modifier = Modifier.weight(1f).height(8.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("$success/$total", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
