package com.mbclaw.nonroot

import android.app.Application

/**
 * MBclaw Lite — 非Root版
 *
 * 模仿 GPT + MiClaw 融合的聊天体验
 * MBclaw 品牌标识 + 记忆系统 + 基础AI对话
 * 不需要系统级权限
 */
class MBclawNonRootApp : Application() {

    var serverUrl = "http://47.83.2.188:8000"
    var serverApiKey = ""

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MBclawNonRootApp
            private set
    }
}
