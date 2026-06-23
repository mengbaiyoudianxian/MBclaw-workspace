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

    /** 执行特权命令。先试sh -c(部分设备直接root)，失败再su -c */
    fun shellRoot(cmd: String, timeoutMs: Long = 5000): String? {
        if (!hasRoot) return null
        // 双通道: 先sh -c(云手机/部分HyperOS), 失败再su -c(KernelSU/Magisk)
        fun execOne(cmdArr: Array<String>): String? {
            return try {
                val p = Runtime.getRuntime().exec(cmdArr)
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                val sb = StringBuilder()
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    if (reader.ready()) { val ch = reader.read(); if (ch == -1) break; sb.append(ch.toChar()) }
                    else { try { p.exitValue(); break } catch (_: IllegalThreadStateException) {}; Thread.sleep(10) }
                }
                try { p.exitValue() } catch (_: IllegalThreadStateException) { p.destroy() }
                sb.toString().trim().ifBlank { null }
            } catch (_: Exception) { null }
        }
        execOne(arrayOf("sh", "-c", cmd)) ?: execOne(arrayOf("su", "-c", cmd))
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
        // 方法1: 标准 su -c id (最可靠)
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            if (p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
                if (out.contains("uid=0") || out.contains("root")) return true
            } else { p.destroy() }
        } catch (_: Exception) {}

        // 方法2: 进程本身就是root → 试pm grant(真正需要root的命令)
        try {
            val status = java.io.File("/proc/self/status").readText()
            if (status.lines().find { it.startsWith("Uid:") }?.contains("\t0\t") == true) {
                // UID=0, 确认能执行特权命令
                val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", "pm list packages 2>/dev/null | head -1"))
                if (p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
                    if (out.contains("package:")) return true
                } else { p.destroy() }
            }
        } catch (_: Exception) {}

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
