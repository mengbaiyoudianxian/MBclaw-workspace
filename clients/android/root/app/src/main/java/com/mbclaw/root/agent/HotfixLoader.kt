package com.mbclaw.root.agent

import android.content.Context
import dalvik.system.DexClassLoader
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * HotfixLoader — 热更新引擎 v2
 *
 * 在 attachBaseContext 前调用 loadPatch()，确保补丁类优先加载。
 * 补丁格式: zip文件含 classes.dex, classes2.dex...
 */
object HotfixLoader {

    private const val TAG = "MBclaw-Hotfix"
    private const val PREF = "mb_hotfix"
    @Volatile var patchLoaded = false; private set

    /** 同步加载补丁 (必须在 Application.onCreate 之前从 attachBaseContext 调用) */
    fun loadPatch(ctx: Context) {
        if (patchLoaded) return
        try {
            val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val patchVersion = prefs.getInt("patch_version", 0)
            val patchDir = File(ctx.filesDir, "hotfix")
            val versionFile = File(patchDir, "version.txt")

            if (patchVersion <= 0 || !versionFile.exists()) return
            val installedVersion = versionFile.readText().trim().toIntOrNull() ?: 0
            if (installedVersion < patchVersion) return

            // 用 DexClassLoader 加载补丁，以系统 ClassLoader 为 parent
            val optimizedDir = File(ctx.cacheDir, "hotfix_opt").also { it.mkdirs() }
            val patchZip = File(patchDir, "patch.zip")

            if (!patchZip.exists()) return

            val dexLoader = DexClassLoader(
                patchZip.absolutePath,
                optimizedDir.absolutePath,
                null,
                ctx.classLoader
            )

            // 通过反射将补丁dex插入到 PathClassLoader 的前面
            val pathListField = Class.forName("dalvik.system.BaseDexClassLoader")
                .getDeclaredField("pathList").apply { isAccessible = true }
            val patchPathList = pathListField.get(dexLoader)
            val systemPathList = pathListField.get(ctx.classLoader)

            val dexElementsField = Class.forName("dalvik.system.DexPathList")
                .getDeclaredField("dexElements").apply { isAccessible = true }
            val patchElements = dexElementsField.get(patchPathList) as Array<*>
            val systemElements = dexElementsField.get(systemPathList) as Array<*>

            // 合并: patch在前, system在后
            val merged = java.lang.reflect.Array.newInstance(
                patchElements.javaClass.componentType!!,
                patchElements.size + systemElements.size
            )
            System.arraycopy(patchElements, 0, merged, 0, patchElements.size)
            System.arraycopy(systemElements, 0, merged, patchElements.size, systemElements.size)
            dexElementsField.set(systemPathList, merged)

            patchLoaded = true
            android.util.Log.i(TAG, "热更新 v$installedVersion 已激活 (${patchElements.size} + ${systemElements.size} dex)")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "热更新加载失败: ${e.message}")
        }
    }

    /** 异步检查+下载补丁 (在 onCreate 中调用) */
    suspend fun checkAndDownload(ctx: Context) = withContext(Dispatchers.IO) {
        try {
            val backend = com.mbclaw.root.data.Endpoints.backend(ctx)
            val info = fetchLatest("${backend.trimEnd('/')}/hotfix/latest.json") ?: return@withContext

            val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val currentPatch = prefs.getInt("patch_version", 0)
            if (info.version <= currentPatch) return@withContext

            android.util.Log.i(TAG, "下载热更新 v${info.version}: ${info.desc}")

            val patchDir = File(ctx.filesDir, "hotfix").also { it.mkdirs() }
            val patchZip = File(patchDir, "patch.zip")

            // 下载
            val conn = URL(info.patchUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000; conn.readTimeout = 60000
            if (conn.responseCode != 200) return@withContext
            conn.inputStream.use { FileOutputStream(patchZip).use { out -> it.copyTo(out) } }

            // 验证
            if (patchZip.length() < 1000) { patchZip.delete(); return@withContext }
            try { ZipFile(patchZip).use { it.entries() } } catch (e: Exception) { patchZip.delete(); return@withContext }

            // 记录版本
            File(patchDir, "version.txt").writeText(info.version.toString())
            prefs.edit().putInt("patch_version", info.version).putString("patch_desc", info.desc).apply()

            android.util.Log.i(TAG, "热更新 v${info.version} 下载完成 (${patchZip.length()} bytes)，下次启动生效")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "热更新下载失败: ${e.message}")
        }
    }

    private fun fetchLatest(url: String): HotfixInfo? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            if (conn.responseCode != 200) return null
            val j = JSONObject(conn.inputStream.bufferedReader().readText())
            HotfixInfo(j.optInt("version", 0), j.optString("patch_url", ""), j.optString("desc", ""))
        } catch (_: Exception) { null }
    }

    data class HotfixInfo(val version: Int, val patchUrl: String, val desc: String)
}
