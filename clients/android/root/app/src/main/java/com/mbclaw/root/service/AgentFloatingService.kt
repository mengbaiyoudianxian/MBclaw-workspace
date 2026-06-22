package com.mbclaw.root.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.mbclaw.root.MainActivity

/**
 * AgentFloatingService — AI 运行时的悬浮窗 + 常驻通知
 *
 * 启动: ChatViewModel.send() 时
 * 停止: ChatViewModel.cancel() / 完成时
 *
 * 悬浮窗:
 *  - 屏幕下半 1/3 位置
 *  - 滚动播放 "AI运行中" 字样
 *  - 点击立即终止 (发广播给 ChatViewModel)
 *
 * 通知栏:
 *  - 显示 AI 当前在干啥 (调什么工具)
 *  - 不可清除
 */
class AgentFloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var statusText: TextView? = null

    companion object {
        const val ACTION_START = "com.mbclaw.action.AGENT_START"
        const val ACTION_UPDATE = "com.mbclaw.action.AGENT_UPDATE"
        const val ACTION_STOP = "com.mbclaw.action.AGENT_STOP"
        const val ACTION_CANCEL_FROM_FLOAT = "com.mbclaw.action.CANCEL_FROM_FLOAT"
        const val EXTRA_TEXT = "text"
        const val EXTRA_TOOL = "tool"

        private const val NOTIF_CHANNEL = "mbclaw_agent_running"
        private const val NOTIF_ID = 7788

        /** APP 内调用 */
        fun start(ctx: Context, status: String) {
            val i = Intent(ctx, AgentFloatingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TEXT, status)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun update(ctx: Context, status: String, tool: String = "") {
            val i = Intent(ctx, AgentFloatingService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TEXT, status)
                putExtra(EXTRA_TOOL, tool)
            }
            ctx.startService(i)
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, AgentFloatingService::class.java).apply { action = ACTION_STOP }
            ctx.startService(i)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val txt = intent.getStringExtra(EXTRA_TEXT) ?: "AI 运行中"
                startForeground(NOTIF_ID, buildNotification(txt, ""))
                showFloating(txt)
            }
            ACTION_UPDATE -> {
                val txt = intent.getStringExtra(EXTRA_TEXT) ?: "AI 思考中"
                val tool = intent.getStringExtra(EXTRA_TOOL) ?: ""
                updateNotification(txt, tool)
                statusText?.text = txt.take(2)   // 悬浮窗只显示 2 字
            }
            ACTION_STOP -> {
                removeFloating()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_CANCEL_FROM_FLOAT -> {
                // 广播给 ChatViewModel
                sendBroadcast(Intent("com.mbclaw.action.USER_CANCEL_AGENT"))
                removeFloating()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(NOTIF_CHANNEL) == null) {
                val ch = NotificationChannel(NOTIF_CHANNEL, "AI 运行状态",
                    NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                    enableVibration(false)
                    description = "Agent 运行时常驻"
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(text: String, tool: String): Notification {
        val openApp = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancelIntent = PendingIntent.getService(this, 1,
            Intent(this, AgentFloatingService::class.java).apply { action = ACTION_CANCEL_FROM_FLOAT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("🤖 MBclaw 运行中")
            .setContentText(if (tool.isNotEmpty()) "正在调用: $tool" else text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                if (tool.isNotEmpty()) "正在调用: $tool\n$text" else text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_delete, "终止", cancelIntent)
            .build()
    }

    private fun updateNotification(text: String, tool: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text, tool))
    }

    private fun showFloating(initial: String) {
        if (!canDrawOverlays()) return
        if (floatingView != null) return

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC1A2434.toInt())   // 半透明深蓝
            setPadding(28, 16, 28, 16)
        }
        statusText = TextView(this).apply {
            text = initial.take(2)
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(8, 0, 8, 0)
            setOnClickListener {
                // 点击 = 终止
                val i = Intent(this@AgentFloatingService, AgentFloatingService::class.java).apply {
                    action = ACTION_CANCEL_FROM_FLOAT
                }
                startService(i)
            }
        }
        layout.addView(statusText)
        layout.setOnClickListener { statusText?.callOnClick() }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // 屏幕下半 1/3 处 = y 偏移 = 屏高 * 2/3 - 一点
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            val displayMetrics = resources.displayMetrics
            y = (displayMetrics.heightPixels * 0.66).toInt()
        }
        try {
            windowManager?.addView(layout, params)
            floatingView = layout
            // 滚动动画 (透明度脉动)
            statusText?.let { tv ->
                val anim = android.animation.ObjectAnimator.ofFloat(tv, "alpha", 1f, 0.4f, 1f)
                anim.duration = 1500
                anim.repeatCount = android.animation.ObjectAnimator.INFINITE
                anim.start()
            }
        } catch (e: Exception) {
            android.util.Log.e("MBclaw-Float", "悬浮窗显示失败: ${e.message}")
        }
    }

    private fun removeFloating() {
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            floatingView = null
            statusText = null
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true
    }

    override fun onDestroy() {
        removeFloating()
        super.onDestroy()
    }
}
