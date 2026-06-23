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

    /** 最小权限阈值: 低于此数不标记完成，下次启动重试 */
    private const val MIN_GRANTED = 30

    /** 在 Application.onCreate 调用。非阻塞。
     *  v4.7修复:
     *   1. 未达到 MIN_GRANTED 不标记完成, 下次启动自动重试
     *   2. 大命令拆成小批次, 避免 shell 长度限制
     *   3. 启动无障碍服务 (settings put 之后还要 am startservice)
     */
    fun setupAsync(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val (currentGranted, currentTotal) = status(context)
        if (prefs.getBoolean(K_DONE, false) && currentGranted >= MIN_GRANTED) {
            Log.i(TAG, "已完成初始化 ($currentGranted/$currentTotal), 跳过")
            return
        }
        if (currentGranted < MIN_GRANTED) {
            Log.i(TAG, "权限不足 ($currentGranted/$currentTotal < $MIN_GRANTED), 需要初始化")
        }
        val last = prefs.getLong(K_LAST_ATTEMPT, 0)
        if (System.currentTimeMillis() - last < 10_000) {
            // 10秒内重试: 延迟后重试 (root可能还没就绪)
            CoroutineScope(Dispatchers.IO).launch {
                delay(10_000)
                setupAsync(context)
            }
            return
        }
        prefs.edit().putLong(K_LAST_ATTEMPT, System.currentTimeMillis()).apply()

        CoroutineScope(Dispatchers.IO).launch {
            // 等 root 就绪: 最多尝试 5 次, 每次间隔 3 秒
            var tier = PermissionTier.get(context)
            var attempts = 0
            while (!tier.hasRoot && attempts < 5) {
                delay(3000)
                tier = PermissionTier.get(context) // 重新获取(触发新的root探测)
                attempts++
            }
            if (!tier.hasRoot) {
                Log.w(TAG, "5次尝试后仍无 root, 跳过")
                return@launch
            }
            val pkg = context.packageName
            Log.i(TAG, "RootBootstrap 开始 pkg=$pkg (已有 $currentGranted/$currentTotal)")

            var granted = currentGranted
            var failed = 0

            // 1. pm grant 分批 (每批 10 个，避免 shell 命令过长)
            val grantable = PermissionPolicy.filterGrantable(context, DANGEROUS)
                .filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            Log.i(TAG, "待授予 ${grantable.size} 个权限")

            grantable.chunked(10).forEachIndexed { batchIdx, batch ->
                val cmds = batch.joinToString("\n") { perm ->
                    "pm grant --user 0 $pkg $perm 2>/dev/null && echo G:$perm || echo F:$perm"
                }
                val out = tier.shellRoot(cmds, timeoutMs = 30_000) ?: ""
                out.lines().forEach { ln ->
                    if (ln.startsWith("G:")) granted++ else if (ln.startsWith("F:")) failed++
                }
                Log.i(TAG, "批次$batchIdx: ${batch.size}个, 累计 $granted/$failed")
            }

            // 2. appops 特殊权限 (分批, 每批 10 个命令)
            val appopsCmds = listOf(
                "appops set --user 0 $pkg SYSTEM_ALERT_WINDOW allow",
                "cmd appops set --user 0 $pkg SYSTEM_ALERT_WINDOW allow",
                "cmd appops set --user 0 $pkg POST_NOTIFICATION allow",
                "appops set --user 0 $pkg WRITE_SETTINGS allow",
                "appops set --user 0 $pkg GET_USAGE_STATS allow",
                "appops set --user 0 $pkg PROJECT_MEDIA allow",
                "appops set --user 0 $pkg ACCESS_NOTIFICATIONS allow",
                "appops set --user 0 $pkg RUN_IN_BACKGROUND allow",
                "appops set --user 0 $pkg RUN_ANY_IN_BACKGROUND allow",
                "appops set --user 0 $pkg WAKE_LOCK allow",
                "appops set --user 0 $pkg START_FOREGROUND allow",
                "appops set --user 0 $pkg REQUEST_INSTALL_PACKAGES allow",
                "appops set --user 0 $pkg REQUEST_DELETE_PACKAGES allow",
                "appops set --user 0 $pkg AUTO_START allow",
                "appops set --user 0 $pkg BOOT_COMPLETED allow",
            )
            appopsCmds.chunked(8).forEach { batch ->
                tier.shellRoot(batch.joinToString("\n"), timeoutMs = 15_000)
            }
            Log.i(TAG, "appops 完成")

            // 3. 电池优化
            tier.shellRoot("dumpsys deviceidle whitelist +$pkg; cmd deviceidle whitelist +$pkg", timeoutMs = 10_000)

            // 4. 无障碍 (settings put + am startservice)
            tier.shellRoot(
                "cur=\$(settings get secure enabled_accessibility_services 2>/dev/null); " +
                "if ! echo \"\$cur\" | grep -q \"MBclawAccessibilityService\"; then " +
                "settings put secure enabled_accessibility_services \"\$cur:com.mbclaw.root/com.mbclaw.root.service.MBclawAccessibilityService\"; fi; " +
                "settings put secure accessibility_enabled 1",
                timeoutMs = 10_000
            )
            // 尝试启动无障碍服务
            tier.shellRoot(
                "am startservice com.mbclaw.root/com.mbclaw.root.service.MBclawAccessibilityService 2>/dev/null || true",
                timeoutMs = 5000
            )

            // 5. 厂商自启动
            tier.shellRoot("""
                cmd appops set $pkg AUTO_START allow 2>/dev/null || true
                cmd appops set $pkg BOOT_COMPLETED allow 2>/dev/null || true
                pm enable $pkg/.service.BootReceiver 2>/dev/null || true
                am broadcast -a coloros.app.action.ADD_AUTO_BOOT --es pkg $pkg 2>/dev/null || true
                am broadcast -a com.vivo.permissionmanager.AUTO_START --es pkg $pkg 2>/dev/null || true
                am broadcast -a huawei.intent.action.SUPER_AUTO_LAUNCH --es pkg $pkg 2>/dev/null || true
            """.trimIndent(), timeoutMs = 10_000)

            // 检查最终结果
            val (finalGranted, finalTotal) = status(context)
            if (finalGranted >= MIN_GRANTED) {
                prefs.edit().putBoolean(K_DONE, true).apply()
                Log.i(TAG, "✅ RootBootstrap 完成: $finalGranted/$finalTotal")
            } else {
                // 不标记完成，下次启动自动重试
                prefs.edit().putBoolean(K_DONE, false).apply()
                Log.w(TAG, "⚠️ RootBootstrap 未达标: $finalGranted/$finalTotal < $MIN_GRANTED, 下次重试")
            }
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
