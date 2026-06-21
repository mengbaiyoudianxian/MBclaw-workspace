package com.mbclaw.root.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import com.mbclaw.root.agent.PermissionPolicy
import com.mbclaw.root.agent.RootBootstrap
import com.mbclaw.root.data.Account
import com.mbclaw.root.data.AccountManager
import com.mbclaw.root.data.UserSettings
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────────────
// 1. 账号头像组件 (供 SettingsPage 调用)
// ──────────────────────────────────────────────────────
@Composable
fun AccountAvatar(account: Account, size: Int) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var bmp by remember(account.qqId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(account.qqId) {
        val path = AccountManager.downloadAvatarIfNeeded(ctx, account)
        if (path != null) {
            bmp = android.graphics.BitmapFactory.decodeFile(path)
        }
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            val b = bmp
            if (b != null) {
                androidx.compose.foundation.Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = "头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else if (account.qqId.isNotBlank()) {
                Text("Q", fontWeight = FontWeight.Bold,
                     color = MaterialTheme.colorScheme.primary,
                     style = MaterialTheme.typography.titleLarge)
            } else if (account.weixinId.isNotBlank()) {
                Text("微", fontWeight = FontWeight.Bold,
                     color = Color(0xFF07C160),
                     style = MaterialTheme.typography.titleLarge)
            } else {
                Icon(Icons.Filled.PersonOutline, "未登录",
                     tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// asImageBitmap 已通过 import 提供

// ──────────────────────────────────────────────────────
// 2. 账号设置 Sheet
// ──────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(ctx: android.content.Context, onDismiss: () -> Unit) {
    var account by remember { mutableStateOf(AccountManager.load(ctx)) }
    var qq by remember { mutableStateOf(account.qqId) }
    var wx by remember { mutableStateOf(account.weixinId) }
    var nick by remember { mutableStateOf(account.nickname) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val serverUrl = remember { UserSettings(ctx).serverUrl }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(20.dp).fillMaxWidth()) {
            Text("我的账号", fontWeight = FontWeight.Bold,
                 style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text("优先 QQ，其次微信。账号同步至 $serverUrl",
                 style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))

            // 当前头像预览
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AccountAvatar(account, size = 80)
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = qq, onValueChange = { qq = it.filter { c -> c.isDigit() } },
                label = { Text("QQ 号 (头像自动读取)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = wx, onValueChange = { wx = it },
                label = { Text("微信 ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = nick, onValueChange = { nick = it },
                label = { Text("昵称 (可选)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        loading = true
                        scope.launch {
                            val key = qq.ifBlank { wx }
                            if (key.isBlank()) {
                                loading = false
                                return@launch
                            }
                            val remote = AccountManager.fetchFromServer(serverUrl, key)
                            if (remote != null) {
                                qq = remote.qqId; wx = remote.weixinId; nick = remote.nickname
                                android.widget.Toast.makeText(ctx, "已从云端恢复账号",
                                    android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(ctx, "云端未找到，请直接保存",
                                    android.widget.Toast.LENGTH_SHORT).show()
                            }
                            loading = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !loading,
                ) { Text("☁ 云端恢复") }

                Button(
                    onClick = {
                        loading = true
                        scope.launch {
                            val acc = Account(qqId = qq.trim(), weixinId = wx.trim(), nickname = nick.trim())
                            AccountManager.save(ctx, acc)
                            account = acc
                            AccountManager.syncToServer(ctx, acc, serverUrl)
                            loading = false
                            android.widget.Toast.makeText(ctx, "已保存并同步",
                                android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !loading,
                ) { Text("💾 保存") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ──────────────────────────────────────────────────────
// 3. 权限详情对话框 (任务 5)
// ──────────────────────────────────────────────────────
@Composable
fun PermissionsDetailDialog(ctx: android.content.Context, onDismiss: () -> Unit) {
    val all = RootBootstrap.DANGEROUS
    var refresh by remember { mutableStateOf(0) }
    var picking by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("权限详情 (${all.size})") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(all) { perm ->
                    key(refresh, perm) {
                        val policy = PermissionPolicy.get(ctx, perm)
                        val granted = ctx.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        Row(
                            Modifier.fillMaxWidth().clickable { picking = perm }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(shape = CircleShape, modifier = Modifier.size(8.dp),
                                color = when {
                                    policy == PermissionPolicy.Policy.DENY_FOREVER -> MaterialTheme.colorScheme.error
                                    granted -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.outline
                                }) {}
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(perm.substringAfterLast('.'),
                                     style = MaterialTheme.typography.bodyMedium)
                                Text(perm,
                                     style = MaterialTheme.typography.labelSmall,
                                     color = MaterialTheme.colorScheme.outline,
                                     maxLines = 1)
                            }
                            Text(when (policy) {
                                PermissionPolicy.Policy.ALLOW -> if (granted) "✅" else "△"
                                PermissionPolicy.Policy.DENY_FOREVER -> "🚫"
                                PermissionPolicy.Policy.ASK_EACH_TIME -> "🔁"
                            }, style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                RootBootstrap.resetAndRerun(ctx)
                refresh++
            }) { Text("重新授予") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )

    picking?.let { perm ->
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(perm.substringAfterLast('.')) },
            text = { Text("选择此权限的处理方式：") },
            confirmButton = {
                Column {
                    listOf(
                        Triple("以后全部禁止", PermissionPolicy.Policy.DENY_FOREVER, MaterialTheme.colorScheme.error),
                        Triple("打开", PermissionPolicy.Policy.ALLOW, MaterialTheme.colorScheme.primary),
                        Triple("每次启动默认打开", PermissionPolicy.Policy.ASK_EACH_TIME, MaterialTheme.colorScheme.tertiary),
                    ).forEach { (label, pol, col) ->
                        TextButton(
                            onClick = {
                                PermissionPolicy.set(ctx, perm, pol)
                                picking = null
                                refresh++
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = col),
                        ) { Text(label) }
                    }
                }
            }
        )
    }
}

// ──────────────────────────────────────────────────────
// 4. MiClaw 算力桥接 (任务 11)
// ──────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiclawBridgeSheet(
    ctx: android.content.Context,
    settings: UserSettings,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("点击下方按钮，将打开服务器中转的 MiClaw 登录页") }
    var loading by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(20.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🎁", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("白嫖 MiClaw 算力", fontWeight = FontWeight.Bold,
                         style = MaterialTheme.typography.titleMedium)
                    Text("via NEORUAA/miclaw_api_bridge",
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("如果你拥有 MiClaw 内测权限，登录后系统会自动配置 Key 和 URL，整个流程隐藏在服务器，Key 不下发到本地。",
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.outline,
                 lineHeight = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp))
            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Info, null,
                              tint = MaterialTheme.colorScheme.primary,
                              modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(status, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    loading = true
                    status = "正在打开 MiClaw 登录页…"
                    // 跳到服务器代理的登录页
                    val u = "${settings.serverUrl.trimEnd('/')}/bridge/miclaw/login"
                    val i = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(u))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                    status = "登录后回到 app，几秒后自动同步 Key…"
                    // 等待轮询服务器，看是否配好
                    scope.launch {
                        repeat(20) {
                            kotlinx.coroutines.delay(3000)
                            val ok = checkBridgeReady(ctx, settings)
                            if (ok) {
                                status = "✅ 已自动配好 MiClaw Key (服务端代理, 本地隐藏)"
                                loading = false
                                return@launch
                            }
                        }
                        status = "⏰ 60s 内未检测到登录，可手动重试"
                        loading = false
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("🚀 打开 MiClaw 登录页")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private suspend fun checkBridgeReady(ctx: android.content.Context, settings: UserSettings): Boolean =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val acc = AccountManager.load(ctx)
            val key = acc.qqId.ifBlank { acc.weixinId }.ifBlank { return@withContext false }
            val url = "${settings.serverUrl.trimEnd('/')}/bridge/miclaw/status?user=$key"
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            if (conn.responseCode !in 200..299) return@withContext false
            val txt = conn.inputStream.bufferedReader().readText()
            val j = org.json.JSONObject(txt)
            if (j.optBoolean("ready")) {
                // 代理模式：客户端使用服务器作为 base_url, key 是 user_token
                val token = j.optString("user_token")
                if (token.isNotBlank()) {
                    settings.providerId = "miclaw-bridge"
                    settings.apiBaseUrl = "${settings.serverUrl.trimEnd('/')}/bridge/miclaw/v1"
                    settings.apiKey = token
                    settings.modelName = j.optString("model", "miclaw-default")
                    return@withContext true
                }
            }
            false
        } catch (_: Exception) { false }
    }
