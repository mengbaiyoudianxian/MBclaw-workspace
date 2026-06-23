package com.mbclaw.root.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mbclaw.root.agent.PermissionTier
import com.mbclaw.root.data.Endpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * 权限授予全屏页 (v4.8)
 *
 * Root检测通过后显示: 权限清单 + 品牌/版本检测 + 服务器模板 + 逐项授予+验证
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGrantScreen(
    ctx: android.content.Context,
    onDone: () -> Unit,
    onSkip: () -> Unit,
) {
    val tier = remember { PermissionTier.get(ctx) }
    val scope = rememberCoroutineScope()
    val pkg = ctx.packageName

    data class PermState(val name: String, val zh: String, val essential: Boolean, var granted: Boolean = false, var checking: Boolean = false)

    val perms = remember {
        mutableStateListOf<PermState>().apply {
            com.mbclaw.root.agent.RootBootstrap.DANGEROUS.forEach { perm ->
                val info = com.mbclaw.root.agent.PermissionLabels.get(perm)
                add(PermState(perm, info.zh, info.essential))
            }
        }
    }

    var phase by remember { mutableStateOf("ready") } // ready | granting | done
    var statusText by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var androidVer by remember { mutableStateOf("") }
    var sdkInt by remember { mutableIntStateOf(0) }
    var template by remember { mutableStateOf<JSONObject?>(null) }
    var showSkipWarning by remember { mutableStateOf(false) }

    // 设备信息
    LaunchedEffect(Unit) {
        brand = Build.BRAND
        androidVer = Build.VERSION.RELEASE
        sdkInt = Build.VERSION.SDK_INT
    }

    fun grantOne(perm: PermState) {
        scope.launch {
            perm.checking = true
            withContext(Dispatchers.IO) {
                // 尝试用 su 和直接 sh 两种方式
                val alreadyGranted = ctx.checkSelfPermission(perm.name) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (alreadyGranted) {
                    perm.granted = true
                    perm.checking = false
                    return@withContext
                }
                val result = tier.shellRoot("pm grant --user 0 $pkg ${perm.name} 2>&1", timeoutMs = 8000)
                // 验证是否真的授予了
                kotlinx.coroutines.delay(300)
                val verified = ctx.checkSelfPermission(perm.name) == android.content.pm.PackageManager.PERMISSION_GRANTED
                withContext(Dispatchers.Main) {
                    perm.granted = verified
                    perm.checking = false
                }
            }
        }
    }

    if (showSkipWarning) {
        AlertDialog(
            onDismissRequest = { showSkipWarning = false; onSkip() },
            title = { Text("⚠️ 功能受限") },
            text = { Text("由于权限缺失，95% 功能将无法使用。\n\n如需重新授权，请到软件的设置页面操作。") },
            confirmButton = { TextButton(onClick = { showSkipWarning = false; onSkip() }) { Text("我知道了") } },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统权限授予") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, "关闭") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            // 设备信息
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("$brand · Android $androidVer (SDK $sdkInt)", fontWeight = FontWeight.SemiBold)
                        Text("包名: $pkg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 进度
            if (phase == "granting") {
                val done = perms.count { it.granted }
                val total = perms.size
                LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text("$done / $total 已授权", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(8.dp))

            // 权限清单
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(perms.toList()) { perm ->
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (perm.granted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            // 状态图标
                            when {
                                perm.checking -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                perm.granted -> Icon(Icons.Filled.CheckCircle, null, tint = androidx.compose.ui.graphics.Color(0xFF34C759), modifier = Modifier.size(20.dp))
                                else -> Icon(Icons.Filled.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(perm.zh, style = MaterialTheme.typography.bodyMedium)
                                    if (perm.essential) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.errorContainer) {
                                            Text("必备", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 按钮
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showSkipWarning = true },
                    modifier = Modifier.weight(1f),
                ) { Text("暂时跳过，稍后授予") }

                Button(
                    onClick = {
                        phase = "granting"
                        statusText = "正在获取 ${brand} Android $androidVer 权限模板..."
                        scope.launch {
                            // 从服务器拉模板
                            withContext(Dispatchers.IO) {
                                try {
                                    val url = "${Endpoints.backend(ctx).trimEnd('/')}/admin/client/perm-template?brand=$brand&model=${Build.MODEL}&sdk=$sdkInt"
                                    val conn = URL(url).openConnection() as java.net.HttpURLConnection
                                    conn.connectTimeout = 8000; conn.readTimeout = 8000
                                    template = JSONObject(conn.inputStream.bufferedReader().readText())
                                } catch (_: Exception) { template = null }
                            }
                            // 逐项授予
                            for (perm in perms) {
                                grantOne(perm)
                                kotlinx.coroutines.delay(200)
                            }
                            phase = "done"
                            statusText = "完成！${perms.count { it.granted }}/${perms.size} 权限已授予"
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = phase != "granting",
                ) { Text(if (phase == "granting") "授予中..." else "Root 权限一键授予") }
            }

            if (statusText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }

            AnimatedVisibility(visible = phase == "done") {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("✅ 完成，开始使用")
                }
            }
        }
    }
}
