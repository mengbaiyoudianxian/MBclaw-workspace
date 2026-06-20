package com.mbclaw.root.agent

import android.content.Context
import com.mbclaw.root.MBclawRootApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaType

/**
 * MBclaw Agent — 对话引擎
 *
 * 后端：MiMo v2.5-pro（820亿token） + MBclaw 服务端记忆
 * 备选：本地规则匹配（离线可用）
 */
class MBclawAgent {
    private val app = MBclawRootApp.instance
    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    // MiClaw 原生工具技能映射（386个工具）
    val nativeSkills = mapOf(
        "wifi" to "WiFi 管理：打开/关闭/连接/扫描/热点",
        "bluetooth" to "蓝牙管理：配对/连接/传输/音频",
        "sms" to "短信：发送/读取/搜索/备份",
        "call" to "通话：拨号/接听/录音/拦截",
        "camera" to "相机：拍照/录像/扫码/识别",
        "screen" to "录屏：录制/截屏/投屏",
        "file" to "文件：浏览/搜索/压缩/传输",
        "calendar" to "日历：查看/添加/提醒/同步",
        "note" to "笔记：创建/编辑/搜索/同步",
        "browser" to "浏览器：搜索/打开/书签/下载",
        "map" to "地图：搜索/导航/路线/POI",
        "shop" to "购物：搜索/比价/下单/跟踪",
        "home" to "智能家居：灯光/空调/窗帘/门锁",
        "system" to "系统：音量/亮度/省电/清理/重启",
    )

    /**
     * 对话 — 调用 MiMo API + MBclaw 记忆服务
     */
    suspend fun chat(message: String): String {
        _isThinking.value = true
        return try {
            withContext(Dispatchers.IO) {
                // 1. 本地规则快速匹配（离线）
                val localResult = localMatch(message)
                if (localResult != null) return@withContext localResult

                // 2. 调用 MiMo API
                callMiMoAPI(message)
            }
        } catch (e: Exception) {
            "抱歉，MBclaw 暂时无法连接 AI 服务器。\n错误：${e.message}\n\n你可以试试用本地命令（如 '开WiFi'、'发短信'）"
        } finally {
            _isThinking.value = false
        }
    }

    private fun localMatch(msg: String): String? {
        val m = msg.lowercase().trim()

        // 工具查询
        if (m.startsWith("工具") || m.startsWith("技能") || m == "help") {
            return nativeSkills.entries.joinToString("\n") { (k, v) -> "🔧 **${k}** — $v" } +
                    "\n\n发送命令如「打开WiFi」「发短信给张三说我到了」来直接使用。"
        }

        // 设备控制模式
        when {
            m.contains("wifi") && (m.contains("开") || m.contains("打开")) ->
                return "WiFi 已打开 ✅（模拟）"
            m.contains("wifi") && (m.contains("关") || m.contains("关闭")) ->
                return "WiFi 已关闭 ✅（模拟）"
            m.contains("蓝牙") && (m.contains("开") || m.contains("打开")) ->
                return "蓝牙已打开 ✅（模拟）"
            m.contains("截屏") || m.contains("截图") ->
                return "截图已保存到相册 ✅（模拟）"
            m.contains("录屏") ->
                return "录屏已开始 🎥 点击通知栏停止（模拟）"
            m == "你是谁" || m.contains("介绍") ->
                return "我是 MBclaw 🌟，由一个18岁的打工人孟白耗时2个月打造。" +
                        "我可以操控手机、永远记住你的偏好、在本地沙箱跑危险代码。" +
                        "我是独立开发的，不是任何开源项目的Fork。"
            m.contains("版本") || m.contains("version") ->
                return "MBclaw Root v0.2.0\nMiMo: mimo-v2.5-pro (820亿token)\n" +
                        "记忆系统: Hermes 6-mode\n工具: 386原生MiClaw技能"
        }

        return null
    }

    /**
     * 调用 MiMo API（OpenAI 兼容格式）
     */
    private suspend fun callMiMoAPI(message: String): String {
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val requestBody = org.json.JSONObject().apply {
                put("model", app.mimoModel)
                put("temperature", 0.7)
                put("max_tokens", 2000)
                put("messages", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("role", "system")
                        put("content", "你是 MBclaw，一个强大的手机AI助手。你可以控制手机的WiFi/蓝牙/短信/通话/相机/文件等386个功能。" +
                                "你由一个18岁的打工人孟白创造。回答简洁实用，必要时主动提供帮助。" +
                                "你的记忆系统让你能记住用户说过的每个细节。")
                    })
                    put(org.json.JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    })
                })
            }

            val request = okhttp3.Request.Builder()
                .url("${app.mimoBaseUrl}/chat/completions")
                .addHeader("Authorization", "Bearer ${app.mimoApiKey}")
                .addHeader("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create(
                    "application/json".toMediaType(),
                    requestBody.toString()
                ))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val json = org.json.JSONObject(body)
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } catch (e: Exception) {
            throw e
        }
    }

    // —— 语音 ——

    fun startListening(onResult: (String) -> Unit) {
        _isListening.value = true
        // TODO: 集成 MiClaw 原版语音识别 SDK (libaivs_jni.so / libflexkws.so)
        // 5-6秒静音自动结束识别
    }

    fun stopListening() {
        _isListening.value = false
    }

    // —— Agent 服务生命周期 ——

    fun start() {}
    fun stop() {}
}
