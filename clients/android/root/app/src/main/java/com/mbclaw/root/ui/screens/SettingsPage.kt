package com.mbclaw.root.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mbclaw.root.agent.MBclawAgent
import com.mbclaw.root.agent.PermissionTier
import com.mbclaw.root.agent.RootBootstrap
import com.mbclaw.root.agent.SafeOps
import com.mbclaw.root.agent.ToolRegistry
import com.mbclaw.root.data.AccountManager
import com.mbclaw.root.data.SecureVault
import com.mbclaw.root.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    agent: MBclawAgent,
    onBack: () -> Unit,
    onSetupProvider: () -> Unit,
    onOpenHand: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSessions: () -> Unit = {},
    onOpenCommunity: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val s = agent.settings
    var utopia by remember { mutableStateOf(s.utopiaEnabled) }
    var sync by remember { mutableStateOf(s.serverSyncEnabled) }
    var showTokens by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showPermissionsPage by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showMiclawSheet by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }
    var showSponsor by remember { mutableStateOf(false) }
    val tier = remember { PermissionTier.get(ctx) }
    val (granted, total) = remember { RootBootstrap.status(ctx) }
    val vaultCount = remember { SecureVault.count(ctx) }
    val backupCount = remember { SafeOps.listBackups("apps").size + SafeOps.listBackups("files").size }
    val account = remember { AccountManager.load(ctx) }
    val debugCode = remember { "mb-" + (android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID)?.take(8) ?: "unknown") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Spacer(Modifier.height(8.dp))

            // ── 账号 ──
            SectionLabel("账号")
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingRow(Icons.Outlined.AccountCircle, "我的账号", account.qqId.ifBlank { "未登录" }, onClick = { showAccountSheet = true })
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.ChatBubbleOutline, "当前会话", "${agent.db.getAllMemoryKeys().size} 段历史 · 点击查看全部", onClick = onOpenSessions)
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.Security, "权限状态", "$granted/$total 已授予", onClick = { showPermissionsPage = true },
                    trailing = { if (granted >= 25) Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp)) else Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp)) })
            }

            Spacer(Modifier.height(24.dp))

            // ── 外观 ──
            SectionLabel("外观")
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("主题模式", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val modes = listOf("浅色" to "light", "深色" to "dark", "跟随系统" to "system")
                        val current = com.mbclaw.root.ui.theme.ThemePreference.mode(ctx)
                        modes.forEach { (label, key) ->
                            val sel = current == key
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (sel) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f).clickable { com.mbclaw.root.ui.theme.ThemePreference.setMode(ctx, key) },
                            ) {
                                Text(label, Modifier.padding(vertical = 10.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── 核心模型与感知 ──
            SectionLabel("核心模型与感知")
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(horizontal = 16.dp)) {
                val modelSub = if (s.isConfigured()) s.modelName else "未配置"
                SettingRow(Icons.Outlined.Key, "模型 API 配置", modelSub, onClick = onSetupProvider,
                    trailing = { if (!s.isConfigured()) Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFEF2F2)) { Text("未配", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = Color(0xFFEF4444)) } else {} })
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.Visibility, "视觉识图模型", "未配 · 主模型不支持识图时使用", onClick = {})
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.Mic, "语音 TTS/ASR 模型", "未配 · 输入/输出语音时使用", onClick = {})
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.CardGiftcard, "白嫖 MiClaw 算力", "通过 NEORUAA bridge 中转", onClick = { showMiclawSheet = true })
            }

            Spacer(Modifier.height(24.dp))

            // ── 功能与扩展 ──
            SectionLabel("功能与扩展")
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingRow(Icons.Outlined.Computer, "完整 Linux 环境",
                    if (com.mbclaw.root.sandbox.LocalSandbox(ctx).isInstalled) "已安装 · 706MB · /data/mbclaw/linux"
                    else "一键下载 · 278MB · 预装 Python/JDK17/Git/GCC/CMake",
                    onClick = {})
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.Cable, "MCP 插件市场", "Model Context Protocol · 连接外部工具", onClick = {})
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.Apps, "工具市场", "${ToolRegistry.ALL.size} 个工具 · 添加/上传/下载", onClick = onOpenTools)
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.TouchApp, "智能手 Agent Hand", "看得见点得准 · 校准/区块识别", onClick = onOpenHand)
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.AutoAwesome, "Skill 技能", "本地/云端技能 · 扩展AI能力", onClick = {})
            }

            Spacer(Modifier.height(24.dp))

            // ── 共建反馈 ──
            SectionLabel("共建反馈")
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingRow(Icons.Outlined.BugReport, "Bug 反馈", "反馈问题 · 投票支持", onClick = onOpenCommunity)
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.Lightbulb, "共建计划", "功能建议 · 投票支持", onClick = onOpenCommunity)
            }

            Spacer(Modifier.height(24.dp))

            // ── 乌托邦计划 ──
            SectionLabel("乌托邦计划")
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(horizontal = 16.dp)) {
                SwitchRow("启用乌托邦", "主动收集评价、分析心理学画像、优化交互", utopia, { utopia = it; s.utopiaEnabled = it })
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow("连接 MBclaw 服务器", "同步数据到母体记忆系统", sync, { sync = it; s.serverSyncEnabled = it })
            }

            Spacer(Modifier.height(24.dp))

            // ── 隐私与安全 ──
            SectionLabel("隐私与安全")
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingRow(Icons.Outlined.Lock, "隐私保险箱", "${vaultCount}项 · AES-256-GCM", onClick = {})
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.Backup, "自动备份", "${backupCount}份备份 · 删除前自动备份", onClick = {})
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.BarChart, "Token 消耗统计", "", onClick = { showTokens = true })
            }

            Spacer(Modifier.height(24.dp))

            // ── 开发者调试 ──
            SectionLabel("开发者调试")
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(horizontal = 16.dp)) {
                val debugId = tier.runCatching { PermissionTier.get(ctx) }.getOrNull()?.let { "mb-" + android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID).take(8) } ?: "未知"
                SettingRow(Icons.Outlined.Terminal, "远程调试", "永久开启 · $debugCode · 点击复制", onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("debug", debugCode))
                })
            }

            Spacer(Modifier.height(24.dp))

            // ── 版本信息 ──
            SectionLabel("版本信息")
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingRow(Icons.Outlined.Info, "MBclaw 版本", "v${BuildConfig.VERSION_NAME}", onClick = { showAboutSheet = true },
                    trailing = { Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFEF3C7)) { Text("最新", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = Color(0xFF92400E)) } })
            }

            Spacer(Modifier.height(8.dp))

            // ── 关于 ──
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingRow(Icons.Outlined.Forum, "酷安", "coolapk.com/u/26771405", onClick = {
                    val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.coolapk.com/u/26771405"))
                    i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i)
                })
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.Person, "作者 QQ", "1973054239 · 点击复制", onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("qq", "1973054239"))
                })
                Divider(Modifier.padding(horizontal = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.FavoriteBorder, "友情赞助", "请作者喝杯奶茶", onClick = { showSponsor = true })
            }

            Spacer(Modifier.height(32.dp))

            // ── 危险操作区（底部隔离）──
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)), modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().clickable { showClearConfirm = true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DeleteForever, null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("清除历史对话", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFEF4444))
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // 弹窗
    if (showClearConfirm) AlertDialog(
        onDismissRequest = { showClearConfirm = false },
        title = { Text("清除历史对话", fontWeight = FontWeight.Bold) },
        text = { Text("删除所有对话记录，不可恢复。") },
        confirmButton = {
            TextButton(onClick = { agent.db.writableDatabase.execSQL("DELETE FROM messages"); agent.db.writableDatabase.execSQL("DELETE FROM sessions"); showClearConfirm = false }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))) { Text("删除") }
        },
        dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
    )

    if (showPermissionsPage) PermissionGrantScreen(ctx = ctx, onDone = { showPermissionsPage = false }, onSkip = { showPermissionsPage = false })
    if (showAccountSheet) AccountSheet(ctx = ctx, onDismiss = { showAccountSheet = false })
    if (showMiclawSheet) MiclawBridgeSheet(ctx = ctx, settings = s, onDismiss = { showMiclawSheet = false })
    if (showTokens) TokenDialog(s.modelName, onDismiss = { showTokens = false })
    if (showSponsor) SponsorDialog(onDismiss = { showSponsor = false })
    if (showAboutSheet) AboutSheet(onDismiss = { showAboutSheet = false })
}

// ── 组件 ──

@Composable
fun SectionLabel(text: String) {
    Text(text, Modifier.padding(start = 16.dp, bottom = 8.dp, top = 0.dp),
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        letterSpacing = 0.5.sp)
}

@Composable
fun SettingRow(
    icon: ImageVector, title: String, subtitle: String,
    onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        trailing?.invoke() ?: Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SwitchRow(title: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF3B82F6)))
    }
}

@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("MBclaw", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column {
                Text("版本 v${BuildConfig.VERSION_NAME}", fontSize = 14.sp); Spacer(Modifier.height(4.dp))
                Text("作者 QQ: 1973054239", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)); Spacer(Modifier.height(2.dp))
                Text("酷安: coolapk.com/u/26771405", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
fun SponsorDialog(onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请作者喝杯奶茶 🧋", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (selected.isEmpty()) {
                    Text("选择赞助方式", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp), color = Color(0xFF1677FF).copy(alpha = 0.1f),
                            modifier = Modifier.weight(1f).clickable { selected = "alipay" }.padding(16.dp)
                        ) { Text("支付宝", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1677FF)) }
                        Surface(
                            shape = RoundedCornerShape(12.dp), color = Color(0xFF07C160).copy(alpha = 0.1f),
                            modifier = Modifier.weight(1f).clickable { selected = "wechat" }.padding(16.dp)
                        ) { Text("微信", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF07C160)) }
                    }
                } else {
                    TextButton(onClick = { selected = "" }) { Text("‹ 返回选择", fontSize = 12.sp) }
                    Spacer(Modifier.height(8.dp))
                    Text("扫码赞助", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    val url = if (selected == "alipay") "http://8.130.42.188/sponsor/1782078138823.jpg" else "http://8.130.42.188/sponsor/mm_reward_qrcode_1782077995846.png"
                    val bmp = remember(url) {
                        try { android.graphics.BitmapFactory.decodeStream(java.net.URL(url).openStream()) } catch (e: Exception) { null }
                    }
                    val ctx2 = androidx.compose.ui.platform.LocalContext.current
                    Surface(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable {
                        val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); ctx2.startActivity(i)
                    }) {
                        if (bmp != null) {
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "收款码", modifier = Modifier.fillMaxWidth())
                        } else {
                            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
fun TokenDialog(modelName: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Token 消耗统计", fontWeight = FontWeight.Bold) },
        text = { Text("当前模型: $modelName\n暂无详细统计，后续版本支持。", fontSize = 14.sp) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}
