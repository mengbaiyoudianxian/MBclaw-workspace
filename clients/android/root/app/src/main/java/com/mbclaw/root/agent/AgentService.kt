package com.mbclaw.root.agent

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*

/**
 * MBclaw 后台 Agent 服务
 *
 * 保持后台运行，处理：
 *  - 主动建议（通知栏推送）
 *  - 定时记忆整理（Dreaming / Curator）
 *  - 乌托邦计划数据上报
 */
class AgentService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val agent by lazy { MBclawAgent(application as android.app.Application) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            while (isActive) {
                delay(60_000) // 每分钟检查一次
                // Curator: 整理过期记忆
                // Dreaming: 整合每日笔记
                // Proactive: 检测用户重复操作 → 主动建议
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

class AgentManager(private val context: android.content.Context) {
    fun start() {}
    fun stop() {}
}
