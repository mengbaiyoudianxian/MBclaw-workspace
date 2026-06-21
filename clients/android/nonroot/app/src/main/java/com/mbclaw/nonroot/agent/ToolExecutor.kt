package com.mbclaw.nonroot.agent

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SmsManager
import com.mbclaw.nonroot.data.LocalDB
import com.mbclaw.nonroot.data.UserSettings
import com.mbclaw.nonroot.hermes.RealEngine
import com.mbclaw.nonroot.service.MBclawAccessibilityService
import com.mbclaw.nonroot.service.ShizukuManager
import org.json.JSONObject
import kotlinx.coroutines.*

/**
 * NonRoot 工具执行器 — 系统API优先
 *
 * 四层: 系统SDK → 语音助手 → 无障碍 → Shizuku(可选)
 * 90%的操作不需要Shizuku
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
    private val cameraManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager else null

    init { shizuku.init() }

    /** 通过系统API执行 am start (无需Shizuku) */
    private fun systemAmStart(action: String, data: String? = null, extras: Map<String, String> = emptyMap()): Boolean {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data?.let { setData(Uri.parse(it)) }
                extras.forEach { (k, v) -> putExtra(k, v) }
            }
            context.startActivity(intent); true
        } catch (_: Exception) { false }
    }

    /** 调用手机自带语音助手执行命令 */
    private fun viaVoiceAssistant(command: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_TEXT, command)
                // 小爱同学 / Google Assistant / Bixby 都会响应这个Intent
            }
            context.startActivity(intent); true
        } catch (_: Exception) { false }
    }

    suspend fun execute(toolName: String, args: JSONObject): String {
        val result = withContext(Dispatchers.IO) {
            try {
                when (toolName) {
                // ═══ 设备控制 — 系统SDK直调 ═══
                "toggle_wifi" -> {
                    val enable = args.optBoolean("enable")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+: 系统设置面板 (无需任何权限)
                        systemAmStart(Settings.Panel.ACTION_WIFI)
                        "WiFi 面板已打开，请手动${if (enable) "打开" else "关闭"}"
                    } else {
                        wifiManager?.isWifiEnabled = enable
                        "WiFi 已${if (enable) "打开" else "关闭"}"
                    }
                }
                "toggle_bluetooth" -> {
                    val enable = args.optBoolean("enable")
                    if (enable) bluetoothAdapter?.enable() else bluetoothAdapter?.disable()
                    "蓝牙 ${if (enable) "正在打开" else "正在关闭"}"
                }
                "toggle_flashlight" -> {
                    // CameraManager API — 无需Shizuku! (Android 6+)
                    val enable = args.optBoolean("enable")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && cameraManager != null) {
                        val cameraId = cameraManager!!.cameraIdList.firstOrNull()
                        if (cameraId != null) {
                            cameraManager!!.setTorchMode(cameraId, enable)
                            "手电筒 已${if (enable) "打开" else "关闭"} (CameraManager)"
                        } else "无后置摄像头"
                    } else "需要 Android 6.0+"
                }
                "toggle_airplane_mode" -> {
                    // 系统广播 — 无需Shizuku (但可能需要系统权限)
                    val enable = args.optBoolean("enable")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        // Android 9以下: 可以通过广播
                        val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
                            putExtra("state", enable)
                        }
                        context.sendBroadcast(intent)
                        "飞行模式广播已发送"
                    } else {
                        // Android 10+: 系统限制，尝试Settings面板或不求Shizuku
                        if (shizuku.isReady()) {
                            shizuku.exec("settings put global airplane_mode_on ${if (enable) 1 else 0}")
                            shizuku.exec("am broadcast -a android.intent.action.AIRPLANE_MODE")
                            "飞行模式 已${if (enable) "打开" else "关闭"} (Shizuku)"
                        } else {
                            "Android 10+ 飞行模式需Shizuku。请说「打开Shizuku」或手动操作。"
                        }
                    }
                }
                "set_brightness" -> {
                    val level = args.optInt("level", 128)
                    // 需要 WRITE_SETTINGS 权限(用户可在设置中授予)
                    if (Settings.System.canWrite(context)) {
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
                        "亮度已设为 $level/255"
                    } else {
                        // 引导用户授权
                        systemAmStart(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                        "需要「修改系统设置」权限，请在弹出的设置中允许MBclaw"
                    }
                }
                "set_volume" -> {
                    val type = when (args.optString("type", "media")) {
                        "ring" -> AudioManager.STREAM_RING; "alarm" -> AudioManager.STREAM_ALARM
                        else -> AudioManager.STREAM_MUSIC
                    }
                    audioManager?.setStreamVolume(type, args.optInt("level", 7), 0)
                    "音量已设"
                }
                "get_battery" -> {
                    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = intent?.getIntExtra("level", -1) ?: -1
                    val scale = intent?.getIntExtra("scale", 100) ?: 100
                    "电池: ${level * 100 / scale}%"
                }

                // ═══ 通信 — 系统Intent ═══
                "send_sms" -> {
                    val phone = args.optString("phone")
                    val message = args.optString("message")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        // 检查SMS权限
                        if (context.checkSelfPermission(android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            val smsManager = SmsManager.getDefault()
                            smsManager.sendTextMessage(phone, null, message, null, null)
                            "短信已发送到 $phone"
                        }
                    }
                    // 兜底: 打开短信界面
                    systemAmStart(Intent.ACTION_SENDTO, "sms:$phone", mapOf("sms_body" to message))
                    "短信界面已打开，消息已预填"
                }
                "read_sms" -> "读取短信需READ_SMS权限，请在设置中授权"
                "make_call" -> {
                    if (context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        systemAmStart(Intent.ACTION_CALL, "tel:${args.optString("phone")}")
                        "正在拨打 ${args.optString("phone")}"
                    } else {
                        systemAmStart(Intent.ACTION_DIAL, "tel:${args.optString("phone")}")
                        "拨号界面已打开"
                    }
                }

                // ═══ 屏幕 — 无障碍优先 (系统级) ═══
                "take_screenshot" -> {
                    val svc = MBclawAccessibilityService.instance
                    if (shizuku.isReady()) {
                        shizuku.screenshot("/sdcard/mbclaw_screenshot_${System.currentTimeMillis()}.png")
                        "截图已保存 (Shizuku)"
                    } else {
                        "截图需要Shizuku"
                    }
                }
                "screen_record" -> {
                    if (shizuku.isReady()) {
                        shizuku.screenRecord("/sdcard/mbclaw_record_${System.currentTimeMillis()}.mp4", args.optInt("duration", 10))
                        "录屏中..."
                    } else "录屏需要Shizuku"
                }
                "click_at" -> {
                    val svc = MBclawAccessibilityService.instance
                    if (svc?.clickAt(args.optInt("x").toFloat(), args.optInt("y").toFloat()) == true)
                        "点击 (无障碍API)"
                    else if (shizuku.isReady()) { shizuku.inputTap(args.optInt("x"), args.optInt("y")); "点击 (Shizuku)" }
                    else "点击需要先开启无障碍服务"
                }
                "long_press_at" -> {
                    val svc = MBclawAccessibilityService.instance
                    if (svc?.longClickAt(args.optInt("x").toFloat(), args.optInt("y").toFloat(), args.optLong("duration_ms", 800)) == true) "长按完成"
                    else "长按需要无障碍服务"
                }
                "swipe" -> {
                    val svc = MBclawAccessibilityService.instance
                    if (svc?.swipe(args.optInt("x1").toFloat(), args.optInt("y1").toFloat(), args.optInt("x2").toFloat(), args.optInt("y2").toFloat(), args.optLong("duration_ms", 300)) == true) "滑动完成"
                    else if (shizuku.isReady()) { shizuku.inputSwipe(args.optInt("x1"), args.optInt("y1"), args.optInt("x2"), args.optInt("y2")); "滑动 (Shizuku)" }
                    else "滑动需要无障碍或Shizuku"
                }
                "input_text" -> {
                    val svc = MBclawAccessibilityService.instance
                    if (svc?.inputText(args.optString("text")) == true) "输入完成"
                    else "输入需要无障碍服务"
                }
                "press_key" -> {
                    val svc = MBclawAccessibilityService.instance
                    val keyCode = when (args.optString("key")) {
                        "BACK" -> android.view.KeyEvent.KEYCODE_BACK; "HOME" -> android.view.KeyEvent.KEYCODE_HOME
                        "RECENTS" -> android.view.KeyEvent.KEYCODE_APP_SWITCH; "ENTER" -> android.view.KeyEvent.KEYCODE_ENTER
                        "DELETE" -> android.view.KeyEvent.KEYCODE_DEL; "VOLUME_UP" -> android.view.KeyEvent.KEYCODE_VOLUME_UP
                        "VOLUME_DOWN" -> android.view.KeyEvent.KEYCODE_VOLUME_DOWN; "POWER" -> android.view.KeyEvent.KEYCODE_POWER
                        else -> 0
                    }
                    if (keyCode > 0) {
                        val globalAction = when (keyCode) {
                            android.view.KeyEvent.KEYCODE_BACK -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                            android.view.KeyEvent.KEYCODE_HOME -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                            android.view.KeyEvent.KEYCODE_APP_SWITCH -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
                            else -> -1
                        }
                        if (globalAction >= 0 && svc?.performGlobalAction(globalAction) == true) "按键完成"
                        else "按键需要无障碍服务"
                    } else "未知按键"
                }

                // ═══ App管理 ═══
                "open_app" -> {
                    val pkg = args.optString("package_name")
                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        "已打开 $pkg"
                    } else "未找到App: $pkg"
                }
                "list_apps" -> {
                    val filter = args.optString("filter", "")
                    context.packageManager.getInstalledApplications(0)
                        .filter { it.packageName.contains(filter, ignoreCase = true) }
                        .take(10).joinToString("\n") { "  ${it.packageName}" }
                        .let { "已安装($filter):\n$it" }
                }
                "uninstall_app" -> {
                    systemAmStart(Intent.ACTION_DELETE, "package:${args.optString("package_name")}")
                    "卸载界面已打开"
                }
                "force_stop_app" -> {
                    if (shizuku.isReady()) {
                        shizuku.forceStopApp(args.optString("package_name"))
                    } else {
                        systemAmStart(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${args.optString("package_name")}")
                        "应用详情已打开，请手动强制停止"
                    }
                }

                // ═══ 语音助手 ═══
                "trigger_voice_assistant" -> {
                    val cmd = args.optString("command", "")
                    viaVoiceAssistant(cmd)
                    if (cmd.isNotBlank()) "已唤起语音助手并发送: $cmd"
                    else "语音助手已唤起"
                }

                // ═══ 系统信息 ═══
                "get_system_info" -> "设备: ${Build.MODEL} | Android ${Build.VERSION.RELEASE} | SDK ${Build.VERSION.SDK_INT} | Shizuku: ${if (shizuku.isReady()) "✅" else "❌"}"
                "get_clipboard" -> {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.text?.toString() ?: "剪贴板为空"
                }
                "set_clipboard" -> {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("MBclaw", args.optString("text")))
                    "已复制"
                }
                "get_notifications" -> {
                    val monitor = com.mbclaw.nonroot.service.NotificationMonitor.instance
                    if (monitor != null) "通知监听已激活" else "通知监听未开启，请在设置→通知使用权中开启MBclaw"
                }

                // ═══ MBclaw内部 ═══
                "search_memory" -> db.searchMemory(args.optString("query"), args.optInt("limit", 5)).joinToString("\n") { "• ${it.key}: ${it.value.take(150)}" }.ifBlank { "无相关记忆" }
                "dream_memory" -> realEngine.dream(args.optString("session_id", ""))
                "classify_conversation" -> realEngine.classifyContent(args.optString("text"), emptyList()).first
                "dual_key_review" -> realEngine.dualKeyReview(args.optString("content"))
                "collision_think" -> {
                    val kws = args.optJSONArray("keywords") ?: return@withContext "无关键词"
                    realEngine.collision((0 until kws.length()).map { kws.getString(it) })
                }
                "get_capability" -> "📱 NonRoot | 系统API + ${if (shizuku.isReady()) "Shizuku" else "无障碍"} | ${if (settings.canUploadKey()) "乌托邦100%" else "本地40%"}"

                else -> "未知工具: $toolName"
                }
            } catch (e: Exception) { "❌ ${toolName}: ${e.message}" }
        }
        return result.toString()
    }
}
