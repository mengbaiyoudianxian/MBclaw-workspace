package com.mbclaw.nonroot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mbclaw.nonroot.agent.AgentService

/** 开机自启 AgentService */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, AgentService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}

/** 主动建议广播 */
class ProactiveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 由 AgentService 处理
        context.startForegroundService(Intent(context, AgentService::class.java).apply {
            putExtra("proactive", true)
            putExtra("message", intent.getStringExtra("message") ?: "")
        })
    }
}
