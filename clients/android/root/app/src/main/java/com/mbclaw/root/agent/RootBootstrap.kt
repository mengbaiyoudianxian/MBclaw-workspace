package com.mbclaw.root.agent

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.*

/**
 * Root 启动器 — 应用首次启动时，如果有 root，自动完成以下操作：
 *
 * 1. 授予 *所有* 危险权限（绕开系统弹窗）
 *    使用 `pm grant <pkg> <perm>` 直接通过 root shell 写入授权
 *
 * 2. 自启动 / 关联启动 / 后台保活
 *    appops + 厂商 ROM 命令（小米/华为/oppo/vivo）
 *
 * 3. 电池优化白名单（无限制）
 *    dumpsys deviceidle whitelist +pkg
 *
 * 4. 升级为「系统应用」类别
 *    pm install -r --user 0 + 移动到 /system/priv-app/（如果 /system 可写）
 *    或者注入 device_admin / accessibility 让 OS 视为高优先级
 *
 * 5. 给自己绑定无障碍 + 通知监听 + 悬浮窗
 *
 * 整个过程一次成功，下次启动跳过（写 prefs 标记）
 */
object RootBootstrap {

    private const val TAG = "MBclaw-Boot"
    private const val PREF = "mbclaw_root_setup"
    private const val K_DONE = "setup_done_v3"
    private const val K_LAST_ATTEMPT = "last_attempt"

    /** 要授予的危险权限清单 (公开以供权限详情页读取) */
    val DANGEROUS = listOf(
        // 存储 / 媒体
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        // 通讯
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.GET_ACCOUNTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.READ_PHONE_STATE",
        "android.permission.READ_PHONE_NUMBERS",
        "android.permission.CALL_PHONE",
        "android.permission.ANSWER_PHONE_CALLS",
        "android.permission.ADD_VOICEMAIL",
        "android.permission.USE_SIP",
        "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.RECEIVE_WAP_PUSH",
        "android.permission.RECEIVE_MMS",
        // 位置
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        // 相机麦克风
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        // 身体传感器
        "android.permission.BODY_SENSORS",
        "android.permission.ACTIVITY_RECOGNITION",
        // 日历
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR",
        // 通知 / 蓝牙
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.BLUETOOTH_ADVERTISE",
        // 高级 (需要 root 才能 pm grant)
        "android.permission.SYSTEM_ALERT_WINDOW",         // 悬浮窗
        "android.permission.WRITE_SETTINGS",              // 系统设置
        "android.permission.WRITE_SECURE_SETTINGS",       // 安全设置(开 USB 调试等)
        "android.permission.PACKAGE_USAGE_STATS",         // 使用情况访问
        "android.permission.READ_LOGS",                   // 系统日志
        "android.permission.DUMP",                        // dumpsys
        "android.permission.MODIFY_PHONE_STATE",          // 电话状态修改
        "android.permission.CHANGE_CONFIGURATION",
        "android.permission.MODIFY_AUDIO_SETTINGS",
        "android.permission.MOUNT_UNMOUNT_FILESYSTEMS",   // 挂载
        "android.permission.INTERNAL_SYSTEM_WINDOW",
        "android.permission.MANAGE_USERS",
        "android.permission.INTERACT_ACROSS_USERS_FULL",
        "android.permission.REAL_GET_TASKS",
        "android.permission.READ_FRAME_BUFFER",           // 截屏不弹窗
        "android.permission.ACCESS_SURFACE_FLINGER",
        "android.permission.CAPTURE_AUDIO_OUTPUT",        // 录系统声音
        "android.permission.CAPTURE_VIDEO_OUTPUT",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.BIND_DEVICE_ADMIN",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.REQUEST_DELETE_PACKAGES",
        "android.permission.DELETE_PACKAGES",
        "android.permission.INSTALL_PACKAGES",
        "android.permission.FORCE_STOP_PACKAGES",
    )

    /** 在 Application.onCreate 调用。非阻塞。 */
    fun setupAsync(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefs.getBoolean(K_DONE, false)) {
            Log.i(TAG, "已经初始化过，跳过")
            return
        }
        // 避免短时间内重复尝试（如 root 拒绝）
        val last = prefs.getLong(K_LAST_ATTEMPT, 0)
        if (System.currentTimeMillis() - last < 60_000) return
        prefs.edit().putLong(K_LAST_ATTEMPT, System.currentTimeMillis()).apply()

        CoroutineScope(Dispatchers.IO).launch {
            val tier = PermissionTier.get(context)
            if (!tier.hasRoot) {
                Log.w(TAG, "无 root, 跳过自动权限授予")
                return@launch
            }
            val pkg = context.packageName
            Log.i(TAG, "开始 root 自动配置 pkg=$pkg")

            var granted = 0
            var failed = 0
            // 1. 批量 pm grant — 跳过用户在权限页设为「以后全部禁止」的权限
            val grantable = PermissionPolicy.filterGrantable(context, DANGEROUS)
            Log.i(TAG, "应授予 ${grantable.size}/${DANGEROUS.size} (其余被禁止)")
            val sb = StringBuilder()
            grantable.forEach { perm -> sb.appendLine("pm grant $pkg $perm 2>/dev/null && echo G:$perm || echo F:$perm") }
            val out = tier.shellRoot(sb.toString(), timeoutMs = 30_000) ?: ""
            out.lines().forEach { ln ->
                if (ln.startsWith("G:")) granted++ else if (ln.startsWith("F:")) failed++
            }
            Log.i(TAG, "权限授予: $granted 成功, $failed 失败")

            // 2. 电池优化白名单（无限制）
            tier.shellRoot("""
                dumpsys deviceidle whitelist +$pkg
                cmd appops set $pkg RUN_IN_BACKGROUND allow
                cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow
                cmd appops set $pkg WAKE_LOCK allow
                cmd appops set $pkg START_FOREGROUND allow
            """.trimIndent())

            // 3. 厂商 ROM 自启动（白名单 / 关联启动 / 锁屏后保活）
            tier.shellRoot("""
                # 通用
                cmd appops set $pkg AUTO_START allow 2>/dev/null
                cmd appops set $pkg BOOT_COMPLETED allow 2>/dev/null
                # 小米 MIUI/HyperOS
                pm enable $pkg/.service.BootReceiver 2>/dev/null
                # OPPO/realme
                am broadcast -a coloros.app.action.ADD_AUTO_BOOT --es pkg $pkg 2>/dev/null
                # VIVO
                am broadcast -a com.vivo.permissionmanager.AUTO_START --es pkg $pkg 2>/dev/null
                # 华为
                am broadcast -a huawei.intent.action.SUPER_AUTO_LAUNCH --es pkg $pkg 2>/dev/null
            """.trimIndent())

            // 4. 取消所有 appops 限制（让 app 视为系统级）
            tier.shellRoot("""
                cmd appops set $pkg SYSTEM_ALERT_WINDOW allow
                cmd appops set $pkg PROJECT_MEDIA allow
                cmd appops set $pkg GET_USAGE_STATS allow
                cmd appops set $pkg ACTIVATE_VPN allow
                cmd appops set $pkg WRITE_SETTINGS allow
                cmd appops set $pkg ACCESS_NOTIFICATIONS allow
                cmd appops set $pkg MANAGE_EXTERNAL_STORAGE allow
                cmd appops set $pkg MANAGE_MEDIA allow
                cmd appops set $pkg LEGACY_STORAGE allow
                cmd appops set $pkg BIND_ACCESSIBILITY_SERVICE allow
            """.trimIndent())

            // 5. 绑定无障碍 + 通知监听（绕过用户手动设置）
            val accCls = "com.mbclaw.root/.service.MBclawAccessibilityService"
            val notifCls = "com.mbclaw.root/.service.NotificationMonitor"
            // \$ 转义让 shell 自己解析变量；Kotlin 字符串模板就不会消费 $
            tier.shellRoot(
                "cur=\$(settings get secure enabled_accessibility_services 2>/dev/null); " +
                "if ! echo \"\$cur\" | grep -q \"$accCls\"; then " +
                "settings put secure enabled_accessibility_services \"\$cur:$accCls\"; fi; " +
                "settings put secure accessibility_enabled 1; " +
                "cur2=\$(settings get secure enabled_notification_listeners 2>/dev/null); " +
                "if ! echo \"\$cur2\" | grep -q \"$notifCls\"; then " +
                "settings put secure enabled_notification_listeners \"\$cur2:$notifCls\"; fi"
            )

            // 6. （可选）尝试升级为系统应用 — /system 通常只读，KernelSU 可写 magisk overlay
            // 这里只标记应用为系统优先级，不做物理 move
            tier.shellRoot("""
                # 在 /data/system/packages.xml 中添加 system flag 需要 reboot，先跳过
                # 但可以通过 device_owner 让 app 视为最高优先级
                dpm set-device-owner --user 0 $pkg/.service.MBclawDeviceAdmin 2>/dev/null
            """.trimIndent())

            // 标记完成
            prefs.edit().putBoolean(K_DONE, true).apply()
            Log.i(TAG, "✅ Root 初始化完成: $granted/${DANGEROUS.size} 权限授予")
        }
    }

    /** 强制重新跑一遍（用户在设置里点"重新初始化"时调） */
    fun resetAndRerun(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        setupAsync(context)
    }

    /** 检查报告：返回当前权限授予数 / 总数 */
    fun status(context: Context): Pair<Int, Int> {
        val pm = context.packageManager
        var granted = 0
        DANGEROUS.forEach { perm ->
            if (pm.checkPermission(perm, context.packageName) == PackageManager.PERMISSION_GRANTED) granted++
        }
        return granted to DANGEROUS.size
    }
}
