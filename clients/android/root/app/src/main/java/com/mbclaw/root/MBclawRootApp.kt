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

    // MBclaw 服务器（可配置） — 80端口走 nginx 反代，不用敲 :8000
    var serverUrl = "http://47.83.2.188"
    var serverApiKey = ""

    override fun onCreate() {
        super.onCreate()
        instance = this

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
