package com.mbclaw.nonroot.agent

import android.content.Context
import com.mbclaw.nonroot.api.DirectApiClient
import com.mbclaw.nonroot.data.LocalDB
import com.mbclaw.nonroot.data.UserSettings
import com.mbclaw.nonroot.hermes.RealEngine
import com.mbclaw.nonroot.hermes.LayeredSearch
import com.mbclaw.nonroot.model.ProviderCatalog
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Agent 执行循环 — LLM决策 → 工具调用 → 观察结果 → 继续
 *
 * 这才是 OpenClaw 的核心: 不是聊天，是 agent loop
 */
class AgentLoop(
    private val context: Context,
    private val db: LocalDB,
    private val settings: UserSettings,
) {
    private val realEngine = RealEngine(db, settings)
    private val toolExecutor = ToolExecutor(context, db, settings, realEngine)
    private val layeredSearch = LayeredSearch(db, com.mbclaw.nonroot.hermes.TranscriptLogger(context))
    private val enforcer = MBclawEnforcer(db, layeredSearch)
    private val gson = Gson()
    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

    suspend fun run(userMessage: String, sessionId: String, maxTurns: Int = 5): String = withContext(Dispatchers.IO) {
        // ═══ PRE: 代码强制构建上下文 (不等LLM请求) ═══
        val ctx = enforcer.buildContext(userMessage, sessionId)
        val hadMemories = ctx.memoryInjection.isNotBlank()

        val messages = mutableListOf<AgentMsg>()
        // 身份约束 (极简)
        messages.add(AgentMsg("system", ctx.identityConstraint))
        // 强制能力声明
        messages.add(AgentMsg("system", ctx.capabilityInjection))
        // 强制记忆注入
        if (hadMemories) {
            messages.add(AgentMsg("system", ctx.memoryInjection))
        }

        // 加载历史
        val history = db.getMessages(sessionId, 20)
        for (msg in history.takeLast(10)) {
            messages.add(AgentMsg(msg.role, msg.content))
        }
        messages.add(AgentMsg("user", userMessage))

        var lastResponse = ""
        var turns = 0

        while (turns < maxTurns) {
            turns++
            val result = callWithTools(messages)
            if (result.toolCall != null) {
                // LLM 决定调工具
                val toolResult = toolExecutor.execute(result.toolCall.name, result.toolCall.arguments)
                messages.add(AgentMsg("assistant", null, listOf(result.toolCall)))
                messages.add(AgentMsg("tool", toolResult, toolCallId = result.toolCall.id))
                // 继续循环让LLM看工具结果
                continue
            } else {
                // LLM 直接回复用户
                lastResponse = result.content ?: "完成"
                messages.add(AgentMsg("assistant", lastResponse))
                // 保存到数据库
                db.saveMessage(sessionId, "user", userMessage)
                db.saveMessage(sessionId, "assistant", lastResponse)
                break
            }
        }

        if (lastResponse.isBlank()) lastResponse = "已达到最大轮次($maxTurns)，操作结束。"

        // ═══ POST: 代码强制验证 + 修正 ═══
        val check = enforcer.validateResponse(lastResponse, hadMemories)
        if (!check.passed) {
            lastResponse = enforcer.correctResponse(lastResponse)
        }

        // 自动记录 action_memory
        db.writableDatabase.execSQL(
            "INSERT INTO action_memories (session_id, action) VALUES (?, ?)",
            arrayOf(sessionId, "agent_loop: $userMessage → $lastResponse")
        )
        return@withContext lastResponse
    }

    // ── function calling API ──

    data class ToolCallRequest(val name: String, val arguments: JSONObject, val id: String = "call_${System.currentTimeMillis()}")
    data class LLMResult(val content: String? = null, val toolCall: ToolCallRequest? = null)

    private suspend fun callWithTools(messages: List<AgentMsg>): LLMResult {
        val baseUrl = settings.apiBaseUrl.ifBlank {
            ProviderCatalog.find(settings.providerId)?.baseUrl ?: ""
        }
        val url = "${baseUrl.trimEnd('/')}/chat/completions"

        // 构建请求体: messages + tools + tool_choice
        val body = JSONObject()
        body.put("model", settings.modelName)
        body.put("temperature", 0.7)
        body.put("max_tokens", 2048)
        body.put("tools", ToolRegistry.toOpenAITools())
        body.put("tool_choice", "auto")

        val msgsArr = org.json.JSONArray()
        for (msg in messages) {
            val obj = JSONObject()
            obj.put("role", msg.role)
            if (msg.content != null) obj.put("content", msg.content)
            if (msg.toolCalls != null) {
                val tcArr = org.json.JSONArray()
                for (tc in msg.toolCalls) {
                    tcArr.put(JSONObject().apply {
                        put("id", tc.id); put("type", "function")
                        put("function", JSONObject().apply { put("name", tc.name); put("arguments", tc.arguments.toString()) })
                    })
                }
                obj.put("tool_calls", tcArr)
            }
            if (msg.toolCallId != null) obj.put("tool_call_id", msg.toolCallId)
            msgsArr.put(obj)
        }
        body.put("messages", msgsArr)

        val request = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()

        val response = http.newCall(request).execute()
        val responseBody = response.body?.string() ?: "{}"
        val resp = JSONObject(responseBody)

        if (!response.isSuccessful) {
            val err = resp.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
            return LLMResult(content = "❌ $err")
        }

        val choices = resp.optJSONArray("choices")
        val choice = choices?.optJSONObject(0) ?: return LLMResult(content = "无响应")
        val msg = choice.optJSONObject("message")

        // 检查 tool_calls
        val toolCalls = msg?.optJSONArray("tool_calls")
        if (toolCalls != null && toolCalls.length() > 0) {
            val tc = toolCalls.getJSONObject(0)
            val func = tc.optJSONObject("function")
            val toolName = func?.optString("name") ?: ""
            val toolArgs = try { JSONObject(func?.optString("arguments") ?: "{}") } catch (_: Exception) { JSONObject() }
            return LLMResult(toolCall = ToolCallRequest(toolName, toolArgs, tc.optString("id", "call_0")))
        }

        // 纯文本回复
        return LLMResult(content = msg?.optString("content") ?: "无回复")
    }
}

// ── Agent专用消息 (不与api.ChatMessage冲突) ──
data class AgentMsg(
    val role: String,
    val content: String? = null,
    val toolCalls: List<AgentLoop.ToolCallRequest>? = null,
    val toolCallId: String? = null,
)
