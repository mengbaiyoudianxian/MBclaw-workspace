package com.mbclaw.root.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 工具屏幕 — 保留 MiClaw 原版 386 工具能力
 * 增加：MBclaw 记忆搜索 + 技能卡
 */
@Composable
fun ToolsScreen() {
    val tools = remember {
        listOf(
            ToolItem("wifi", "WiFi 管理", "打开/关闭/连接/扫描/热点共享", Icons.Filled.Wifi),
            ToolItem("bluetooth", "蓝牙", "配对/连接/传输文件/音频设备", Icons.Filled.Bluetooth),
            ToolItem("sms", "短信", "发送/读取/搜索/备份/拦截", Icons.Filled.Sms),
            ToolItem("call", "通话", "拨号/接听/录音/号码拦截", Icons.Filled.Call),
            ToolItem("camera", "相机", "拍照/录像/二维码扫描/文字识别", Icons.Filled.CameraAlt),
            ToolItem("screen", "录屏截图", "录制/截屏/投屏/区域截取", Icons.Filled.Screenshot),
            ToolItem("file", "文件管理", "浏览/搜索/压缩/传输/加密", Icons.Filled.Folder),
            ToolItem("calendar", "日历", "查看/添加/提醒/日程同步", Icons.Filled.CalendarMonth),
            ToolItem("note", "笔记", "创建/编辑/搜索/云同步", Icons.Filled.Note),
            ToolItem("browser", "浏览器", "网页搜索/书签/下载管理", Icons.Filled.Public),
            ToolItem("map", "地图导航", "位置搜索/路线规划/兴趣点", Icons.Filled.Map),
            ToolItem("home", "智能家居", "灯光/空调/窗帘/门锁控制", Icons.Filled.Home),
            ToolItem("system", "系统控制", "音量/亮度/省电/应用管理/重启", Icons.Filled.Settings),
            ToolItem("sandbox", "本地沙箱", "Linux环境/危险指令隔离执行", Icons.Filled.Terminal),
            ToolItem("agent", "智能体", "子Agent协作/思维碰撞/双Key审查", Icons.Filled.Psychology),
            ToolItem("memory", "记忆搜索", "全文搜索/语义搜索/关键词唤醒", Icons.Filled.Search),
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("🛠 工具市场", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("继承 MiClaw 386 工具 + MBclaw 智能增强",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        items(tools) { tool ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().clickable { },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tool.name, style = MaterialTheme.typography.titleSmall)
                        Text(tool.desc, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

data class ToolItem(val id: String, val name: String, val desc: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
