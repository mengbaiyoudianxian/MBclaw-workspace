package com.mbclaw.root.agent

import android.content.Context
import com.mbclaw.root.api.DirectApiClient
import com.mbclaw.root.data.LocalDB
import com.mbclaw.root.data.UserSettings
import com.mbclaw.root.hermes.RealEngine
import com.mbclaw.root.hermes.LayeredSearch
import com.mbclaw.root.model.ProviderCatalog
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
    private val layeredSearch = LayeredSearch(db, com.mbclaw.root.hermes.TranscriptLogger(context))
    private val enforcer = MBclawEnforcer(db, layeredSearch)
    private val gson = Gson()
    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

    // 当前任务状态（UI 顶部可观察）
    @Volatile var running: Boolean = false; private set
    @Volatile var currentTurn: Int = 0; private set
    @Volatile var currentTool: String = ""; private set
    @Volatile var totalTurns: Int = 20; private set
    private val cancelFlag = java.util.concurrent.atomic.AtomicBoolean(false)

    /** UI 调用：终止当前 agent 循环 */
    fun cancel() { cancelFlag.set(true) }

    /** UI 调用：实时状态 */
    fun statusLine(): String = when {
        !running -> ""
        currentTool.isNotBlank() -> "🤖 第 $currentTurn/$totalTurns 轮 · 调用 $currentTool"
        else -> "🤖 第 $currentTurn/$totalTurns 轮 · 思考中…"
    }

    suspend fun run(
        userMessage: String,
        sessionId: String,
        maxTurns: Int = 20,
        onStatus: ((String) -> Unit)? = null,
    ): String = withContext(Dispatchers.IO) {
        running = true
        cancelFlag.set(false)
        totalTurns = maxTurns
        currentTurn = 0
        currentTool = ""

        try {
        // 蓝图P7: 保存checkpoint
        val taskId = System.currentTimeMillis()
        com.mbclaw.root.hermes.BlueprintComplete(context, db).taskEnqueue("agent_loop", 50, userMessage)

        // 蓝图08 P0: 检测上下文长度,接近限制触发Memory Flush
        val history = db.getMessages(sessionId, 20)
        if (history.size > 15) {
            memoryFlush(sessionId, history)
        }
        // ═══ PRE: 代码强制构建上下文 (不等LLM请求) ═══
        val ctx = enforcer.buildContext(userMessage, sessionId)
        val hadMemories = ctx.memoryInjection.isNotBlank()

        // ★ 实时权限状态注入 (修复: agent 不知道自己有 root)
        val tier = PermissionTier.get(context)
        val rootStatus = buildString {
            append("[设备权限状态 - 每次都看这个再决策]\n")
            append("• ROOT: ${if (tier.hasRoot) "✅ 已授权 (UID 0, 全部 capabilities)" else "❌ 未授权"}\n")
            append("• ADB(Shizuku): ${if (tier.hasAdb) "✅ 已就绪" else "❌ 未就绪"}\n")
            append("• 无障碍服务: ${if (tier.hasAccessibility) "✅ 已绑定" else "❌ 未绑定"}\n")
            append("最高可用层: ${tier.bestTier().name}\n")
            if (tier.hasRoot) {
                append("\n★ 你有 ROOT! 所有需要系统权限的操作 (飞行模式/亮度/卸载等), 工具会自动用 root 完成, 不要拒绝, 直接调。\n")
            }
            append("[/状态]\n")
        }

        val messages = mutableListOf<AgentMsg>()
        // 身份约束 (含 root 提醒)
        messages.add(AgentMsg("system", ctx.identityConstraint))
        // 实时权限状态
        messages.add(AgentMsg("system", rootStatus))
        // 强制能力声明
        messages.add(AgentMsg("system", ctx.capabilityInjection))
        // 强制记忆注入
        if (hadMemories) {
            messages.add(AgentMsg("system", ctx.memoryInjection))
        }

        // 历史已加载
        for (msg in history.takeLast(10)) {
            messages.add(AgentMsg(msg.role, msg.content))
        }
        messages.add(AgentMsg("user", userMessage))

        var lastResponse = ""
        var turns = 0

        while (turns < maxTurns) {
            if (cancelFlag.get()) { lastResponse = "⏹ 已手动终止 (第 $turns 轮)"; break }
            turns++
            currentTurn = turns
            currentTool = ""
            onStatus?.invoke(statusLine())
            // 最后一轮: 强制 LLM 给最终答案，不再执行工具
            if (turns >= maxTurns) {
                messages.add(AgentMsg("system", "已达最大轮次。请直接给出最终回答，不要再调用工具。"))
            }
            val result = callWithTools(messages)
            if (cancelFlag.get()) { lastResponse = "⏹ 已手动终止 (第 $turns 轮)"; break }
            if (result.toolCall != null && turns < maxTurns) {
                currentTool = result.toolCall.name
                onStatus?.invoke(statusLine())
                val toolResult = toolExecutor.execute(result.toolCall.name, result.toolCall.arguments)
                messages.add(AgentMsg("assistant", null, listOf(result.toolCall)))
                messages.add(AgentMsg("tool", toolResult, toolCallId = result.toolCall.id))
                continue
            } else {
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

        // P1: 记录thinking到messages
        db.writableDatabase.execSQL("UPDATE messages SET thinking=?, message_type='thinking' WHERE id=(SELECT id FROM messages WHERE session_id=? AND role='assistant' ORDER BY id DESC LIMIT 1)", arrayOf("agent_loop_${turns}turns", sessionId))

        return@withContext lastResponse
        } finally {
            running = false
            currentTool = ""
            onStatus?.invoke("")
        }
    }

    // 蓝图08 P0: Memory Flush — 上下文接近限制时静默保存
    private suspend fun memoryFlush(sessionId: String, history: List<com.mbclaw.root.data.MessageRow>) {
        val summary = history.takeLast(10).joinToString("; ") { it.content.take(100) }
        db.saveMemory("flush_${sessionId}_${System.currentTimeMillis()}", summary, "memory_flush")
        // 通知enforcer下次注入时包含flush内容
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

        // X-Utopia: 算力分账标识
        // X-User-Id: 用户标识 (服务端统计/追踪)
        val account = com.mbclaw.root.data.AccountManager.load(context)
        val userId = account.qqId.ifBlank { account.weixinId }.ifBlank { "anon-${com.mbclaw.root.agent.AntiTamper.deviceFingerprint(context).take(8)}" }

        val request = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Utopia", if (settings.utopiaEnabled) "1" else "0")
            .addHeader("X-User-Id", userId)
            .addHeader("X-Client-Version", com.mbclaw.root.BuildConfig.VERSION_NAME)
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
