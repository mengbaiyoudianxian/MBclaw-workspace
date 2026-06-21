package com.mbclaw.root.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import com.mbclaw.root.BuildConfig
import com.mbclaw.root.agent.MBclawAgent
import com.mbclaw.root.agent.PermissionTier
import com.mbclaw.root.agent.RootBootstrap
import com.mbclaw.root.agent.SafeOps
import com.mbclaw.root.data.SecureVault

/**
 * 设置页 — 仿 MiClaw 二级设置布局
 *  • 头部：渐变卡片 + Logo + 标题
 *  • 分组：每组白色卡片，组间灰色分隔
 *  • 项：左标题 / 右 Switch 或 ›
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    agent: MBclawAgent,
    onBack: () -> Unit,
    onSetupProvider: () -> Unit,
    onOpenHand: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSessions: () -> Unit = {},        // 任务 7
) {
    val ctx = LocalContext.current
    val s = agent.settings
    var utopia by remember { mutableStateOf(s.utopiaEnabled) }
    var sync by remember { mutableStateOf(s.serverSyncEnabled) }
    var url by remember { mutableStateOf(s.serverUrl) }
    var showTokens by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showPermissionsPage by remember { mutableStateOf(false) }    // 任务 5
    var showAccountSheet by remember { mutableStateOf(false) }       // 任务 8
    var showMiclawSheet by remember { mutableStateOf(false) }        // 任务 11
    val tier = remember { PermissionTier.get(ctx) }
    val (granted, total) = remember { RootBootstrap.status(ctx) }
    val vaultCount = remember { SecureVault.count(ctx) }
    val backupCount = remember {
        SafeOps.listBackups("apps").size + SafeOps.listBackups("files").size
    }
    // 任务 8: 读 QQ/微信账号信息
    val account = remember { com.mbclaw.root.data.AccountManager.load(ctx) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── 顶部渐变头卡 (使用真实 LOGO) ───
            val logoBmp = remember {
                try { ctx.assets.open("donate/logo.png").use { android.graphics.BitmapFactory.decodeStream(it) } }
                catch (_: Exception) { null }
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    Modifier.background(
                        Brush.linearGradient(listOf(
                            Color(0xFFE9F1FB),
                            Color(0xFFF7FAFE),
                        ))
                    ).padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (logoBmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = logoBmp.asImageBitmap(),
                                contentDescription = "MBclaw",
                                modifier = Modifier.size(64.dp),
                            )
                        } else {
                            Surface(shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("M", fontWeight = FontWeight.Bold,
                                         color = Color.White,
                                         style = MaterialTheme.typography.headlineMedium)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("MBclaw", fontWeight = FontWeight.SemiBold,
                             style = MaterialTheme.typography.titleMedium,
                             color = Color(0xFF1A2434))
                    }
                }
            }

            // ─── 组 1: 账号 & 设备 ───
            SettingGroup {
                // 任务 8: 我的账号 → 弹出账号面板
                SettingItemRow(
                    "我的账号",
                    subtitle = account.displayName(),
                    leading = { com.mbclaw.root.ui.screens.AccountAvatar(account, size = 40) },
                    onClick = { showAccountSheet = true },
                )
                SettingDivider()
                // 任务 7: 当前会话点击 → 全部会话搜索浮层
                SettingItemRow(
                    "当前会话",
                    subtitle = "${try { agent.db.getSessions().size } catch (_: Exception) { 0 }} 段历史 · 点击查看全部",
                    onClick = onOpenSessions,
                )
                SettingDivider()
                // 任务 5: 权限状态点击 → 详细页
                SettingItemRow(
                    "权限状态",
                    subtitle = "$granted / $total 已授予 · " +
                              (if (tier.hasRoot) "ROOT ✅" else "无 Root") +
                              " · " + (if (tier.hasAccessibility) "无障碍 ✅" else "无障碍 ❌"),
                    onClick = { showPermissionsPage = true },
                )
            }

            SectionTitle("模型与工具")
            SettingGroup {
                SettingItemRow(
                    "模型 API 配置",
                    subtitle = if (s.isConfigured())
                        "${s.providerId} · ${s.modelName}"
                    else "⚠️ 未配置",
                    onClick = onSetupProvider,
                )
                SettingDivider()
                SettingItemRow(
                    "工具市场",
                    subtitle = "${com.mbclaw.root.agent.ToolRegistry.ALL.size} 个工具 · 添加 / 上传 / 下载",
                    onClick = onOpenTools,
                )
                SettingDivider()
                // 任务 11: 白嫖 miclaw 算力
                SettingItemRow(
                    "🎁 白嫖 MiClaw 算力",
                    subtitle = "通过 NEORUAA bridge 中转 · 服务器隐藏 Key",
                    onClick = { showMiclawSheet = true },
                )
                SettingDivider()
                SettingItemRow(
                    "智能手 (Agent Hand)",
                    subtitle = "看得见点得准 · 校准 / 区块识别 / 模糊点击",
                    onClick = onOpenHand,
                )
            }

            SectionTitle("乌托邦计划")
            SettingGroup {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("启用乌托邦", fontWeight = FontWeight.SemiBold,
                             style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Switch(checked = utopia, onCheckedChange = { utopia = it; s.utopiaEnabled = it })
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("贡献你的非隐私数据改善所有人的 AI 体验。开启后你的 token 消耗可能会提高昨日的 1%-5% 左右，但性能、能力、记忆力功能都被母体提高到 100%。",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.outline,
                         lineHeight = TextUnit(20f, TextUnitType.Sp))

                    AnimatedVisibility(visible = utopia) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("连接服务器", fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                Switch(checked = sync,
                                       onCheckedChange = { sync = it; s.serverSyncEnabled = it })
                            }
                            if (sync) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = url,
                                    onValueChange = { url = it; s.serverUrl = it },
                                    label = { Text("服务器地址") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                )
                            }
                        }
                    }
                }
            }

            SectionTitle("隐私与安全")
            SettingGroup {
                SettingItemRow(
                    "🔐 隐私保险箱",
                    subtitle = "$vaultCount 项 · AES-256-GCM · 设备指纹派生",
                ) {}
                SettingDivider()
                SettingItemRow(
                    "💾 自动备份",
                    subtitle = "$backupCount 份备份 · 删除前自动备份, 3 份循环",
                ) {}
                SettingDivider()
                SettingItemRow(
                    "📊 Token 消耗统计",
                    onClick = { showTokens = true },
                )
                SettingDivider()
                SettingItemRow(
                    "🗑️ 清除历史对话",
                    subtitle = "删除所有对话记录, 不可恢复",
                    danger = true,
                    onClick = { showClearConfirm = true },
                )
            }

            SectionTitle("版本信息")
            SettingGroup {
                SettingItemRow(
                    "MBclaw",
                    subtitle = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                ) {}
                SettingDivider()
                SettingItemRow(
                    "酷安",
                    subtitle = "coolapk.com/u/26771405 · 关注作者",
                    onClick = {
                        val i = android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://www.coolapk.com/u/26771405"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(i)
                    }
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showTokens) {
        AlertDialog(
            onDismissRequest = { showTokens = false },
            icon = { Icon(Icons.Filled.Analytics, null) },
            title = { Text("Token 消耗统计") },
            text = {
                Column {
                    val vm = ChatViewModel.get(ctx, agent)
                    val st = vm.tokenStats.value
                    val totalIn = st.sessionTokensIn
                    val totalOut = st.sessionTokensOut
                    val total = totalIn + totalOut

                    Text("📊 本次会话累计", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("• 输入 token: $totalIn", style = MaterialTheme.typography.bodyMedium)
                    Text("• 输出 token: $totalOut", style = MaterialTheme.typography.bodyMedium)
                    Text("• 上一轮: ↑${st.lastTurnIn}  ↓${st.lastTurnOut}",
                         style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(14.dp))

                    // 乌托邦状态
                    if (s.utopiaEnabled) {
                        Text("🌍 乌托邦已开启", fontWeight = FontWeight.SemiBold,
                             color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Text("感谢你参与！你的非隐私数据会帮助优化所有人的 AI 体验。",
                             style = MaterialTheme.typography.bodyMedium,
                             color = MaterialTheme.colorScheme.outline)
                    } else {
                        Text("🔒 乌托邦未开启", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("在「我的 → 乌托邦计划」开启可解锁完整能力。",
                             style = MaterialTheme.typography.bodyMedium,
                             color = MaterialTheme.colorScheme.outline)
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("详细统计请查看服务端管理面板:\n${s.serverUrl}/admin",
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = { TextButton(onClick = { showTokens = false }) { Text("好的") } }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Filled.DeleteForever, null,
                          tint = MaterialTheme.colorScheme.error) },
            title = { Text("清除所有历史对话?") },
            text = { Text("将删除全部对话记录，不可恢复。\n（隐私 Vault 与备份不受影响）") },
            confirmButton = {
                TextButton(
                    onClick = {
                        agent.db.writableDatabase.execSQL("DELETE FROM messages")
                        agent.db.writableDatabase.execSQL("DELETE FROM sessions")
                        showClearConfirm = false
                        ChatViewModel.get(ctx, agent).newSession()
                        android.widget.Toast.makeText(ctx, "已清除所有历史",
                            android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showPermissionsPage) {
        PermissionsDetailDialog(ctx = ctx, onDismiss = { showPermissionsPage = false })
    }
    if (showAccountSheet) {
        AccountSheet(ctx = ctx, onDismiss = { showAccountSheet = false })
    }
    if (showMiclawSheet) {
        MiclawBridgeSheet(ctx = ctx, settings = s, onDismiss = { showMiclawSheet = false })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        fontWeight = FontWeight.Normal,
    )
}

@Composable
private fun SettingGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingItemRow(
    title: String,
    subtitle: String? = null,
    danger: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = if (danger) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.outline,
                     maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing()
        else if (onClick != null) {
            Icon(Icons.Filled.ChevronRight, null,
                 tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 工具市场页（从设置进入），带返回按钮 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsPageWithBack(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("工具市场", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad)) {
            ToolsScreen()
        }
    }
}
