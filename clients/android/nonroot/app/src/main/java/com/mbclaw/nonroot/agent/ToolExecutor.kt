package com.mbclaw.nonroot.agent

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import com.mbclaw.nonroot.data.LocalDB
import com.mbclaw.nonroot.data.UserSettings
import com.mbclaw.nonroot.hermes.RealEngine
import com.mbclaw.nonroot.service.ShizukuManager
import com.mbclaw.nonroot.service.MBclawAccessibilityService
import org.json.JSONObject
import kotlinx.coroutines.*

/**
 * 工具执行器 — 真正执行手机操作
 *
 * 三层执行:
 *   1. Android SDK API (所有设备)
 *   2. AccessibilityService (需要用户授权)
 *   3. Shizuku ADB (需要启动Shizuku)
 */
class ToolExecutor(
    private val context: Context,
    private val db: LocalDB,
    private val settings: UserSettings,
    private val realEngine: RealEngine,
) {
    private val shizuku = ShizukuManager(context)
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()

    init { shizuku.init() }

    suspend fun execute(toolName: String, args: JSONObject): String = withContext(Dispatchers.IO) {
        try {
            when (toolName) {
                // ═══ 设备控制 ═══
                "toggle_wifi" -> {
                    val enable = args.optBoolean("enable")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !wifiManager?.isWifiEnabled!! && enable) {
                        // Android 10+ 需要通过设置面板
                        context.startActivity(Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        "WiFi 设置面板已打开"
                    } else {
                        wifiManager?.isWifiEnabled = enable
                        "WiFi 已${if (enable) "打开" else "关闭"}"
                    }
                }
                "toggle_bluetooth" -> {
                    val enable = args.optBoolean("enable")
                    if (enable) bluetoothAdapter?.enable() else bluetoothAdapter?.disable()
                    "蓝牙 已${if (enable) "打开" else "关闭"}"
                }
                "toggle_flashlight" -> {
                    // 通过Shizuku或相机API
                    shizuku.exec("cmd flashlight ${if (args.optBoolean("enable")) "on" else "off"}")
                    "手电筒 已${if (args.optBoolean("enable")) "打开" else "关闭"}"
                }
                "toggle_airplane_mode" -> {
                    val enable = args.optBoolean("enable")
                    shizuku.exec("settings put global airplane_mode_on ${if (enable) 1 else 0}")
                    shizuku.exec("am broadcast -a android.intent.action.AIRPLANE_MODE")
                    "飞行模式 已${if (enable) "打开" else "关闭"}"
                }
                "set_brightness" -> {
                    val level = args.optInt("level", 128)
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
                    "亮度已设为 $level/255"
                }
                "set_volume" -> {
                    val type = when (args.optString("type", "media")) {
                        "ring" -> AudioManager.STREAM_RING; "alarm" -> AudioManager.STREAM_ALARM
                        else -> AudioManager.STREAM_MUSIC
                    }
                    val level = args.optInt("level", 7)
                    audioManager?.setStreamVolume(type, level, 0)
                    "音量已设为 $level/15"
                }
                "get_battery" -> {
                    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = intent?.getIntExtra("level", -1) ?: -1
                    val scale = intent?.getIntExtra("scale", 100) ?: 100
                    val pct = level * 100 / scale
                    "电池: $pct%"
                }

                // ═══ 通信 ═══
                "send_sms" -> { shizuku.exec("am start -a android.intent.action.SENDTO -d sms:${args.optString("phone")} --es sms_body \"${args.optString("message")}\""); "短信界面已打开" }
                "read_sms" -> { shizuku.exec("content query --uri content://sms/inbox --projection address,body,date --sort date DESC"); "短信列表已获取" }
                "make_call" -> { shizuku.exec("am start -a android.intent.action.CALL -d tel:${args.optString("phone")}"); "正在拨打 ${args.optString("phone")}" }

                // ═══ 屏幕 ═══
                "take_screenshot" -> { shizuku.screenshot("/sdcard/mbclaw_screenshot_${System.currentTimeMillis()}.png"); "截图已保存" }
                "screen_record" -> { shizuku.screenRecord("/sdcard/mbclaw_record_${System.currentTimeMillis()}.mp4", args.optInt("duration", 10)); "录屏中..." }
                "click_at" -> {
                    val x = args.optInt("x"); val y = args.optInt("y")
                    val svc = MBclawAccessibilityService.instance
                    if (svc?.clickAt(x.toFloat(), y.toFloat()) == true) "点击 ($x,$y) 成功" else shizuku.inputTap(x, y).let { "点击 ($x,$y) (Shizuku)" }
                }
                "long_press_at" -> {
                    val svc = MBclawAccessibilityService.instance
                    if (svc?.longClickAt(args.optInt("x").toFloat(), args.optInt("y").toFloat(), args.optLong("duration_ms", 800)) == true) "长按成功" else "长按失败"
                }
                "swipe" -> {
                    val svc = MBclawAccessibilityService.instance
                    if (svc?.swipe(args.optInt("x1").toFloat(), args.optInt("y1").toFloat(), args.optInt("x2").toFloat(), args.optInt("y2").toFloat(), args.optLong("duration_ms", 300)) == true) "滑动成功" else shizuku.inputSwipe(args.optInt("x1"), args.optInt("y1"), args.optInt("x2"), args.optInt("y2")).let { "滑动 (Shizuku)" }
                }
                "input_text" -> {
                    val svc = MBclawAccessibilityService.instance
                    if (svc?.inputText(args.optString("text")) == true) "输入成功" else shizuku.inputText(args.optString("text")).let { "输入 (Shizuku)" }
                }
                "press_key" -> {
                    when (args.optString("key")) {
                        "BACK" -> { val svc = MBclawAccessibilityService.instance; svc?.pressBack() ?: shizuku.inputKey(4); "返回" }
                        "HOME" -> { shizuku.inputKey(3); "主页" }
                        "RECENTS" -> { shizuku.inputKey(187); "最近任务" }
                        "ENTER" -> { shizuku.inputKey(66); "回车" }
                        "DELETE" -> { shizuku.inputKey(67); "删除" }
                        "VOLUME_UP" -> { shizuku.inputKey(24); "音量+" }
                        "VOLUME_DOWN" -> { shizuku.inputKey(25); "音量-" }
                        "POWER" -> { shizuku.inputKey(26); "电源" }
                        else -> "未知按键"
                    }
                }

                // ═══ App管理 ═══
                "open_app" -> {
                    val pkg = args.optString("package_name")
                    val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return@withContext "未找到App: $pkg"
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "已打开 $pkg"
                }
                "list_apps" -> {
                    val filter = args.optString("filter", "")
                    val apps = context.packageManager.getInstalledApplications(0)
                    apps.filter { it.packageName.contains(filter, ignoreCase = true) }.take(10).joinToString("\n") { "  ${it.packageName}" }.let { "已安装($filter):\n$it" }
                }
                "uninstall_app" -> shizuku.uninstallApp(args.optString("package_name"))
                "force_stop_app" -> shizuku.forceStopApp(args.optString("package_name"))

                // ═══ 系统信息 ═══
                "get_system_info" -> "设备: ${Build.MODEL} | 系统: Android ${Build.VERSION.RELEASE} | SDK: ${Build.VERSION.SDK_INT}"
                "get_clipboard" -> {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.text?.toString() ?: "剪贴板为空"
                }
                "set_clipboard" -> {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("MBclaw", args.optString("text")))
                    "已复制到剪贴板"
                }
                "get_notifications" -> {
                    val monitor = com.mbclaw.nonroot.service.NotificationMonitor.instance
                    if (monitor != null) "通知监听已激活" else "通知监听未开启"
                }

                // ═══ MBclaw内部 ═══
                "search_memory" -> {
                    val results = db.searchMemory(args.optString("query"), args.optInt("limit", 5))
                    results.joinToString("\n") { "• ${it.key}: ${it.value.take(150)}" }.ifBlank { "无相关记忆" }
                }
                "dream_memory" -> realEngine.dream(args.optString("session_id", ""))
                "classify_conversation" -> realEngine.classifyContent(args.optString("text"), emptyList()).first
                "dual_key_review" -> realEngine.dualKeyReview(args.optString("content"))
                "collision_think" -> {
                    val kws = args.optJSONArray("keywords") ?: JSONObject().put("keywords", listOf("MBclaw")).optJSONArray("keywords")!!
                    realEngine.collision((0 until kws.length()).map { kws.getString(it) })
                }
                "get_capability" -> if (settings.canUploadKey()) "☁ SERVER(100%)" else "📱 LOCAL(40%)"
                "read_file" -> {
                    val path = args.optString("path")
                    try { java.io.File(path).readText().take(2000) } catch (_: Exception) { "文件不存在" }
                }

                else -> "未知工具: $toolName"
            }
        } catch (e: Exception) { "❌ ${toolName} 执行失败: ${e.message}" }
    }
}
