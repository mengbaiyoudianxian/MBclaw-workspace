package com.mbclaw.nonroot.agent

import android.content.Context
import com.mbclaw.nonroot.data.UserSettings
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * VisionLocator — 非Root版: 需要用户手动截图或无障碍
 *
 * 非Root环境限制: 无法自动截图, VLM定位不可用
 * 替代方案: see_screen (uiautomator dump via Shizuku 或无障碍)
 */
object VisionLocator {

    data class LocateResult(
        val success: Boolean,
        val x: Int = 0, val y: Int = 0,
        val action: String = "",
        val text: String = "",
        val confidence: Float = 0f,
        val thinking: String = "",
        val errorReason: String = "",
    )

    suspend fun locate(
        ctx: Context, settings: UserSettings, taskDescription: String,
    ): LocateResult = withContext(Dispatchers.IO) {
        if (!settings.visionEnabled || settings.visionApiKey.isBlank()) {
            return@withContext LocateResult(false, errorReason = "视觉模型未配置")
        }
        // 非Root版: 需要无障碍截图或用户手动提供截图
        LocateResult(false, errorReason =
            "非Root版暂不支持自动VLM定位。\n请使用 see_screen + click_by_index (基于元素索引)。\n" +
            "或安装 Root 版获得完整视觉定位能力。")
    }

    suspend fun probe(ctx: Context, settings: UserSettings): String = withContext(Dispatchers.IO) {
        if (!settings.visionEnabled) return@withContext "视觉模型未启用"
        if (settings.visionApiKey.isBlank()) return@withContext "未配置 API Key"
        try {
            val url = URL("${settings.visionBaseUrl.trimEnd('/')}/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            conn.setRequestProperty("Authorization", "Bearer ${settings.visionApiKey.trim()}")
            if (conn.responseCode == 200) "✅ 视觉模型连通 (${settings.visionModel})"
            else "⚠️ 视觉模型返回 HTTP ${conn.responseCode}"
        } catch (e: Exception) { "❌ 视觉模型不可达: ${e.message?.take(100)}" }
    }
}
