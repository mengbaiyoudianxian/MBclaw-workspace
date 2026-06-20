package com.mbclaw.nonroot.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mbclaw.nonroot.data.UserSettings
import com.mbclaw.nonroot.data.LocalDB
import com.mbclaw.nonroot.MBclawNonRootApp

/**
 * 非Root版通知监听
 * 基本功能: 读取通知 + 验证码提取 + 主动建议
 */
class NotificationMonitor : NotificationListenerService() {

    companion object {
        var instance: NotificationMonitor? = null; private set
    }

    private val db by lazy { LocalDB(this) }
    private val history = mutableMapOf<String, MutableList<String>>()

    override fun onCreate() { super.onCreate(); instance = this }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = sbn.notification.extras.getString(Notification.EXTRA_TEXT) ?: ""
        val full = "$title: $text"
        if (full.isBlank()) return

        history.getOrPut(sbn.packageName) { mutableListOf() }.add(full)

        // 验证码提取
        if (full.contains("验证码")) {
            val code = Regex("""\d{4,6}""").find(full)?.value
            if (code != null) db.saveMemory("sms_code", code, "notification")
        }

        // 重复操作检测
        val pkgHistory = history[sbn.packageName] ?: return
        if (pkgHistory.size >= 5) {
            val deleteCount = pkgHistory.takeLast(10).count { it.contains("删除") || it.contains("移除") }
            if (deleteCount >= 3) {
                // 触发主动建议 (通过 Notification 通知用户)
                suggestAction("检测到重复删除操作", "需要我帮你自动处理吗？")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
    override fun onListenerConnected() { super.onListenerConnected() }
    override fun onDestroy() { instance = null; super.onDestroy() }

    private fun suggestAction(title: String, message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "mbclaw_suggestions"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            manager.createNotificationChannel(android.app.NotificationChannel(channelId, "建议", android.app.NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pending = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(3001, androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("💡 $title").setContentText(message).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true).setContentIntent(pending).build())
    }
}
