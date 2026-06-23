package com.mbclaw.root.agent

import android.content.Context
import dalvik.system.DexClassLoader
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * HotfixLoader — 热更新引擎
 *
 * 启动时从服务器拉 hotfix.json，有新版就下载 patch.dex，
 * 通过 DexClassLoader 加载，覆盖原有类。
 *
 * 服务器目录结构:
 *   /hotfix/latest.json  — {"version": 17, "patch_url": ".../patch_v17.dex", "desc": "修复xx"}
 *   /hotfix/patch_v17.dex — 编译好的补丁dex
 */
object HotfixLoader {

    private const val PREF = "mb_hotfix"
    private const val TAG = "MBclaw-Hotfix"

    data class HotfixInfo(
        val version: Int,
        val patchUrl: String,
        val desc: String,
    )

    /** 启动时调用，返回是否加载了新补丁 */
    suspend fun checkAndApply(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val backend = com.mbclaw.root.data.Endpoints.backend(ctx)
            val currentVersion = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
                .takeIf { it > 0 } ?: com.mbclaw.root.BuildConfig.VERSION_CODE

            // 获取最新补丁信息
            val info = fetchLatest("${backend.trimEnd('/')}/hotfix/latest.json") ?: return@withContext false

            // 版本相同或更旧，跳过
            if (info.version <= currentVersion) return@withContext false

            // 已加载过同版本，跳过
            val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            if (prefs.getInt("loaded_version", 0) >= info.version) return@withContext false

            android.util.Log.i(TAG, "发现热更新 v${info.version}: ${info.desc}")

            // 下载补丁dex
            val patchFile = File(ctx.cacheDir, "hotfix_v${info.version}.dex")
            downloadPatch(info.patchUrl, patchFile)
            if (!patchFile.exists() || patchFile.length() < 100) return@withContext false

            // 验证dex
            try {
                DexClassLoader(patchFile.absolutePath, ctx.cacheDir.absolutePath, null, ctx.classLoader)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "补丁dex无效: ${e.message}")
                patchFile.delete()
                return@withContext false
            }

            // 记录已加载
            prefs.edit().putInt("loaded_version", info.version).putString("loaded_desc", info.desc).apply()
            android.util.Log.i(TAG, "热更新 v${info.version} 已加载: ${info.desc}")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "热更新失败: ${e.message}")
            false
        }
    }

    private fun fetchLatest(url: String): HotfixInfo? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode != 200) return null
            val j = JSONObject(conn.inputStream.bufferedReader().readText())
            HotfixInfo(
                version = j.optInt("version", 0),
                patchUrl = j.optString("patch_url", ""),
                desc = j.optString("desc", ""),
            )
        } catch (e: Exception) { null }
    }

    private fun downloadPatch(url: String, dest: File) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            if (conn.responseCode != 200) return
            conn.inputStream.use { dest.outputStream().use { out -> it.copyTo(out) } }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "下载补丁失败: ${e.message}")
        }
    }
}
