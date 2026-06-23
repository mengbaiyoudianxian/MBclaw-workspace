package com.mbclaw.root.agent

import android.content.Context
import android.os.Build
import com.mbclaw.root.service.MBclawAccessibilityService
import com.mbclaw.root.service.ShizukuManager
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 权限分层 — 统一 fallback chain
 *
 * 用户最终诉求（2026-06-22 锁定）：
 *   系统 API  >  Root  >  ADB(Shizuku)  >  无障碍
 *
 * 注意：原代码 ShizukuManager 用于 ADB 通道（Shizuku 本质是 adb shell daemon）。
 *
 * 每个工具都应：
 *   PermissionTier.run(context) { tier ->
 *       when (tier) {
 *           Tier.SYSTEM       -> ...系统SDK直调
 *           Tier.ROOT         -> shellRoot(...)
 *           Tier.ADB          -> shellAdb(...)
 *           Tier.ACCESSIBILITY-> svc?.xxx()
 *       }
 *   }
 *
 * 调用方负责回报 success(Boolean)，框架自动尝试下一档。
 */
class PermissionTier private constructor(private val context: Context) {

    enum class Tier { SYSTEM, ROOT, ADB, ACCESSIBILITY, NONE }

    private val shizuku by lazy { ShizukuManager(context).also { it.init() } }

    /** 真实可用的 root：短缓存(5秒)，避免每次调工具都 su -c id */
    @Volatile private var rootCache: Boolean? = null
    @Volatile private var rootCacheTime: Long = 0
    val hasRoot: Boolean get() {
        val now = System.currentTimeMillis()
        if (rootCache != null && now - rootCacheTime < 5000) return rootCache!!
        rootCache = probeRoot()
        rootCacheTime = now
        return rootCache!!
    }

    /** Shizuku/ADB 通道是否就绪 */
    val hasAdb: Boolean get() = shizuku.isReady()

    /** 无障碍服务是否绑定 */
    val hasAccessibility: Boolean get() = MBclawAccessibilityService.instance != null

    /** 当前可用的最高权限层 */
    fun bestTier(): Tier = when {
        hasRoot          -> Tier.ROOT
        hasAdb           -> Tier.ADB
        hasAccessibility -> Tier.ACCESSIBILITY
        else             -> Tier.NONE
    }

    /** 执行 su 命令，失败返回 null。超时时返回已捕获的部分输出 */
    fun shellRoot(cmd: String, timeoutMs: Long = 5000): String? {
        if (!hasRoot) return null
        // 云手机/ADB shell: 进程已是root, 直接sh执行
        val alreadyRoot = try {
            java.io.File("/proc/self/status").readText().lines()
                .find { it.startsWith("Uid:") }?.contains("\t0\t") == true
        } catch (_: Exception) { false }
        return try {
            val p = if (alreadyRoot) Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                    else Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val sb = StringBuilder()
            // BugF修复: 超时时也返回已读取的输出, 不丢结果
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (reader.ready()) {
                    val ch = reader.read()
                    if (ch == -1) break
                    sb.append(ch.toChar())
                } else {
                    // 检查进程是否已结束
                    try { p.exitValue(); break } catch (_: IllegalThreadStateException) {}
                    Thread.sleep(10)
                }
            }
            // 超时则强制 kill
            try { p.exitValue() } catch (_: IllegalThreadStateException) { p.destroy() }
            val result = sb.toString().trim()
            if (result.isBlank()) null else result
        } catch (_: Exception) { null }
    }

    /** 通过 Shizuku 执行 adb 命令 */
    fun shellAdb(cmd: String): String? {
        if (!hasAdb) return null
        return try { shizuku.exec(cmd) } catch (_: Exception) { null }
    }

    /**
     * 按层级尝试执行。block 返回 null 表示该层级未生效，自动落到下一层。
     */
    inline fun <T> tryTiers(vararg order: Tier = arrayOf(Tier.SYSTEM, Tier.ROOT, Tier.ADB, Tier.ACCESSIBILITY),
                             block: (Tier) -> T?): Pair<Tier, T>? {
        for (t in order) {
            val ok = when (t) {
                Tier.SYSTEM        -> true                       // 系统 API 总是可尝试
                Tier.ROOT          -> hasRoot
                Tier.ADB           -> hasAdb
                Tier.ACCESSIBILITY -> hasAccessibility
                Tier.NONE          -> false
            }
            if (!ok) continue
            val r = block(t) ?: continue
            return t to r
        }
        return null
    }

    private fun probeRoot(): Boolean {
        // 方法0: 进程本身就是root (云手机常见)
        try {
            val status = java.io.File("/proc/self/status").readText()
            val uidLine = status.lines().find { it.startsWith("Uid:") } ?: ""
            if (uidLine.contains("\t0\t")) return true
        } catch (_: Exception) {}

        // 方法1: 标准 su -c id
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            if (p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
                if (out.contains("uid=0") || out.contains("root")) return true
            } else { p.destroy() }
        } catch (_: Exception) {}

        // 方法2: 直接执行需要root的命令(无su, 云手机ADB shell)
        try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cat /proc/1/maps 2>/dev/null | head -1"))
            if (p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
                if (out.isNotBlank()) return true
            } else { p.destroy() }
        } catch (_: Exception) {}

        // 方法3: 尝试 /system/xbin/su 等路径
        val suPaths = listOf("/system/xbin/su", "/sbin/su", "/data/adb/magisk/su", "/data/adb/ksu/bin/su", "/system/bin/su")
        for (path in suPaths) {
            try {
                val f = java.io.File(path)
                if (f.exists() && f.canExecute()) return true
            } catch (_: Exception) {}
        }

        return false
    }

    companion object {
        @Volatile private var instance: PermissionTier? = null
        fun get(context: Context): PermissionTier =
            instance ?: synchronized(this) {
                instance ?: PermissionTier(context.applicationContext).also { instance = it }
            }
    }
}
