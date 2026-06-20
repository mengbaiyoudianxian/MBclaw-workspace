package com.mbclaw.root

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.mbclaw.root.agent.AgentManager
import com.mbclaw.root.sandbox.LocalSandbox
import com.mbclaw.root.service.MBclawServerClient

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
    lateinit var agentManager: AgentManager
        private set
    lateinit var localSandbox: LocalSandbox
        private set

    // MiMo 配置（820亿 token 生产 Key）
    val mimoApiKey = "tp-s6rzaqvs5q5rbxg05r8cohcf22hzhdsjonzmmunx3u0bveql"
    val mimoBaseUrl = "https://token-plan-sgp.xiaomimimo.com/v1"
    val mimoModel = "mimo-v2.5-pro"

    // MBclaw 服务器（可配置）
    var serverUrl = "http://47.83.2.188:8000"
    var serverApiKey = ""

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannels()
        serverClient = MBclawServerClient(serverUrl, serverApiKey)
        agentManager = AgentManager(this)
        localSandbox = LocalSandbox(this)
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
