package com.mbclaw.nonroot.agent

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mbclaw.nonroot.MainActivity
import com.mbclaw.nonroot.data.LocalDB
import com.mbclaw.nonroot.data.UserSettings
import kotlinx.coroutines.*

/**
 * NonRoot 前台持久服务
 *
 * 无需 Root:
 *   - startForeground() 标准 Android 前台服务
 *   - START_STICKY 被杀自动重启
 *   - AlarmManager 划掉后定时拉活
 *   - 双进程守护 (sandbox进程)
 */
class AgentService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db by lazy { LocalDB(this) }
    private val settings by lazy { UserSettings(this) }

    companion object {
        const val CHANNEL_ID = "mbclaw_lite_keepalive"
        const val NOTIFY_ID = 2001
        var isRunning = false; private set
    }

    override fun onCreate() {
        super.onCreate(); isRunning = true
        createChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MBclaw Lite 运行中")
            .setContentText("本地记忆激活 | 已配置: ${settings.isConfigured()}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        startForeground(NOTIFY_ID, notification)

        scope.launch {
            while (isActive) {
                delay(60_000); heartbeat()
            }
        }
        scope.launch {
            while (isActive) {
                delay(300_000); curatorCycle()
            }
        }
        scope.launch {
            while (isActive) {
                delay(600_000); proactiveCheck()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() { isRunning = false; scope.cancel(); super.onDestroy() }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 划掉 → AlarmManager 定时拉活
        val alarmMgr = getSystemService(ALARM_SERVICE) as AlarmManager
        val restartIntent = Intent(applicationContext, AgentService::class.java)
        alarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 2000,
            PendingIntent.getService(this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT))
        super.onTaskRemoved(rootIntent)
    }

    private fun heartbeat() {
        val memCount = try { db.getAllMemoryKeys().size } catch (_: Exception) { 0 }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MBclaw Lite")
            .setContentText("记忆: $memCount 条 | ${settings.modelName.ifBlank { "未配置" }}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        manager.notify(NOTIFY_ID, notification)
    }

    private fun curatorCycle() {
        try {
            val staleThreshold = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
            db.writableDatabase.execSQL("DELETE FROM memory WHERE accessed_at < ? AND access_count < 2", arrayOf(staleThreshold.toString()))
        } catch (_: Exception) {}
    }

    private suspend fun proactiveCheck() {
        if (!settings.isConfigured()) return
        try {
            val recentMsgs = db.getMessages("", 10)
            val deleteCount = recentMsgs.count { it.content.contains("删除", ignoreCase = true) }
            if (deleteCount >= 3) {
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("auto_message", "检测到频繁删除操作，需要我批量处理吗？")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pending = PendingIntent.getActivity(this, System.currentTimeMillis().toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val n = NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("💡 MBclaw 建议").setContentText("检测到频繁删除操作，需要我批量处理吗？").setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true).setContentIntent(pending).build()
                manager.notify(3001, n)
            }
        } catch (_: Exception) {}
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "MBclaw 后台", NotificationManager.IMPORTANCE_LOW).apply { description = "保持 MBclaw 在后台运行" })
        }
    }
}
