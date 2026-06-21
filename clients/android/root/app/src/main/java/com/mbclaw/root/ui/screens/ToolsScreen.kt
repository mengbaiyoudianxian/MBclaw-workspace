package com.mbclaw.root.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mbclaw.root.agent.ToolRegistry

/**
 * 工具屏幕 — 仿 MiClaw 工具市场
 * • 顶部统计条 (实时计数)
 * • 分类标签条 (横滚 chips)
 * • 卡片化工具列表
 */
@Composable
fun ToolsScreen() {
    var selectedCat by remember { mutableStateOf("全部") }
    val allTools = remember { ToolRegistry.ALL }

    val cats = remember {
        // 简单按工具名前缀分类（仿 MiClaw 分组）
        listOf(
            "全部" to allTools,
            "系统" to allTools.filter { it.name.startsWith("toggle_") || it.name in listOf("set_brightness","set_volume","get_battery","device_status","get_system_info","check_permissions") },
            "WiFi" to allTools.filter { it.name.contains("wifi", true) },
            "蓝牙" to allTools.filter { it.name.startsWith("bluetooth_") },
            "通讯" to allTools.filter { it.name.contains("sms") || it.name.contains("call") || it.name.contains("phone") || it.name.contains("contact") },
            "文件" to allTools.filter { it.name.contains("file") || it.name in listOf("read_file","write_file","append_file","edit_file","delete_file","copy_file","move_file","list_files","search_files","file_grep","file_info") },
            "屏幕" to allTools.filter { it.name in listOf("take_screenshot","screen_record","click_at","long_press_at","swipe","input_text","press_key") },
            "媒体" to allTools.filter { it.name.contains("media") || it.name == "camera" || it.name.contains("control_media") },
            "日历" to allTools.filter { it.name.contains("calendar") },
            "应用" to allTools.filter { it.name.contains("app_") || it.name in listOf("open_app","list_apps","uninstall_app","force_stop_app") },
            "Web" to allTools.filter { it.name in listOf("url_fetch","web_search","browser_open","browser_extract","browser_click","browser_input","browser_close","get_weather") },
            "记忆" to allTools.filter { it.name in listOf("search_memory","dream_memory","classify_conversation","dual_key_review","collision_think","search_history","load_message") },
            "高级" to allTools.filter { it.name in listOf("local_sandbox_run","list_agents","start_agent","timer","get_location","send_intent","send_notification","get_capability") },
        )
    }
    val current = cats.find { it.first == selectedCat }?.second ?: allTools

    Column(Modifier.fillMaxSize()) {
        // ─── 顶部统计 ───
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛠 工具市场", fontWeight = FontWeight.Bold,
                         style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(" ${allTools.size} 个工具 ",
                             modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                             style = MaterialTheme.typography.labelMedium,
                             color = MaterialTheme.colorScheme.onPrimaryContainer,
                             fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("仿 MiClaw 命名 · root shell 落地 · 87 工具实际可调",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.outline)
            }
        }
        // ─── 分类 chips ───
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cats.forEach { (label, list) ->
                FilterChip(
                    selected = selectedCat == label,
                    onClick = { selectedCat = label },
                    label = { Text("$label (${list.size})", style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(50),
                )
            }
        }
        // ─── 工具列表 ───
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(current) { tool ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {},
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(iconFor(tool.name), null,
                                     tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tool.name, fontWeight = FontWeight.SemiBold,
                                 style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(2.dp))
                            Text(tool.description,
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.outline,
                                 maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun iconFor(name: String) = when {
    name.contains("wifi", true) -> Icons.Filled.Wifi
    name.startsWith("bluetooth") -> Icons.Filled.Bluetooth
    name.contains("sms") || name.contains("message") -> Icons.Filled.Sms
    name.contains("call") || name.contains("phone") -> Icons.Filled.Call
    name.contains("contact") -> Icons.Filled.Contacts
    name == "camera" -> Icons.Filled.CameraAlt
    name.contains("screen") -> Icons.Filled.Screenshot
    name.contains("file") || name in listOf("write_file","read_file","append_file","edit_file","delete_file","copy_file","move_file","list_files","search_files","file_grep","file_info") -> Icons.Filled.Folder
    name.contains("calendar") -> Icons.Filled.CalendarMonth
    name.contains("browser") || name == "url_fetch" || name == "web_search" -> Icons.Filled.Public
    name.contains("media") || name == "control_media" -> Icons.Filled.MusicNote
    name.contains("weather") -> Icons.Filled.Cloud
    name.contains("location") -> Icons.Filled.LocationOn
    name.contains("app_") || name in listOf("open_app","list_apps","uninstall_app","force_stop_app") -> Icons.Filled.Apps
    name.contains("memory") || name == "search_memory" || name == "search_history" -> Icons.Filled.Psychology
    name == "local_sandbox_run" -> Icons.Filled.Terminal
    name == "timer" -> Icons.Filled.Schedule
    name.contains("clipboard") -> Icons.Filled.ContentCopy
    name.contains("notification") -> Icons.Filled.Notifications
    name.contains("brightness") -> Icons.Filled.BrightnessMedium
    name.contains("volume") -> Icons.Filled.VolumeUp
    name == "get_battery" -> Icons.Filled.BatteryStd
    name.contains("flashlight") -> Icons.Filled.FlashOn
    name.contains("airplane") -> Icons.Filled.FlightTakeoff
    else -> Icons.Filled.Build
}
