package com.mbclaw.root.ui.screens

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.mbclaw.root.agent.AgentLoop
import com.mbclaw.root.agent.MBclawAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ChatViewModel — 把 ChatScreen 的状态从 Composable 提升到普通对象
 *
 * 解决：
 *  • bug.4 — tab 切换时 Composable 销毁状态丢失
 *  • bug.5 — 重启 app 不接续上次会话
 *
 * 由 MBclawMainScreen 用 remember(agent) 持有，整个 app 生命周期不销毁
 */
class ChatViewModel(private val ctx: Context, val agent: MBclawAgent) {

    val messages = mutableStateListOf<ChatMsg>()
    val inputText = mutableStateOf("")
    val isThinking = mutableStateOf(false)
    val agentStatus = mutableStateOf("")
    val sessionId = mutableStateOf("")
    val tokenStats = mutableStateOf(TokenStats())   // bug.token.1: 每次对话的 token 统计

    private val agentLoop = AgentLoop(ctx, agent.db, agent.settings)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var initialized = false

    /** 首次进入 chat tab 时调（幂等） */
    fun initIfNeeded() {
        if (initialized) return
        initialized = true
        scope.launch(Dispatchers.IO) {
            agent.initSession()
            val prevSid = agent.db.getLastSessionId()
            if (prevSid != null) {
                sessionId.value = prevSid
                val rows = agent.db.getMessages(prevSid)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    rows.forEach { messages.add(ChatMsg(it.role, it.content)) }
                    if (messages.isEmpty()) {
                        messages.add(welcomeMsg())
                    }
                }
            } else {
                sessionId.value = agent.db.createSession("新对话")
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    messages.add(welcomeMsg())
                }
            }
        }
    }

    /** 切到某个历史会话 */
    fun openSession(sid: String) {
        sessionId.value = sid
        scope.launch(Dispatchers.IO) {
            val rows = agent.db.getMessages(sid)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                messages.clear()
                rows.forEach { messages.add(ChatMsg(it.role, it.content)) }
            }
        }
    }

    /** 新开会话 */
    fun newSession() {
        scope.launch(Dispatchers.IO) {
            val sid = agent.db.createSession("新对话")
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                sessionId.value = sid
                messages.clear()
                messages.add(welcomeMsg())
            }
        }
    }

    /** 删除某个会话（bug.7） */
    fun deleteSession(sid: String, onDone: () -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            try {
                agent.db.writableDatabase.execSQL("DELETE FROM messages WHERE session_id=?", arrayOf(sid))
                agent.db.writableDatabase.execSQL("DELETE FROM sessions WHERE id=?", arrayOf(sid))
                if (sessionId.value == sid) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        sessionId.value = ""
                        messages.clear()
                        initialized = false
                        initIfNeeded()
                    }
                }
            } catch (_: Exception) {}
            kotlinx.coroutines.withContext(Dispatchers.Main) { onDone() }
        }
    }

    /** 清空当前会话的对话内容（bug.7） */
    fun clearCurrentMessages() {
        val sid = sessionId.value
        if (sid.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                agent.db.writableDatabase.execSQL("DELETE FROM messages WHERE session_id=?", arrayOf(sid))
            } catch (_: Exception) {}
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                messages.clear()
                messages.add(welcomeMsg())
            }
        }
    }

    fun send() {
        if (inputText.value.isBlank() || isThinking.value) return
        val text = inputText.value
        inputText.value = ""
        messages.add(ChatMsg("user", text))
        isThinking.value = true
        agentStatus.value = "🤖 启动中…"
        scope.launch {
            try {
                val reply = agentLoop.run(text, sessionId.value, maxTurns = 20) { status ->
                    agentStatus.value = status
                }
                messages.add(ChatMsg("assistant", reply))
                // 更新 token 统计 (粗估)
                val inTokens = (text.length / 1.5).toInt()
                val outTokens = (reply.length / 1.5).toInt()
                tokenStats.value = tokenStats.value.copy(
                    sessionTokensIn = tokenStats.value.sessionTokensIn + inTokens,
                    sessionTokensOut = tokenStats.value.sessionTokensOut + outTokens,
                    lastTurnIn = inTokens,
                    lastTurnOut = outTokens,
                )
            } catch (e: Exception) {
                messages.add(ChatMsg("assistant", "❌ ${e.message}", isError = true))
            }
            isThinking.value = false
            agentStatus.value = ""
        }
    }

    fun cancel() {
        agentLoop.cancel()
        agentStatus.value = "⏹ 终止中…"
    }

    private fun welcomeMsg(): ChatMsg {
        val cfg = agent.settings.isConfigured()
        return ChatMsg("assistant",
            "🌟 MBclaw Root v3.6\n" +
            "当前模型: ${agent.settings.modelName.ifBlank { "(未配置)" }}\n" +
            (if (!cfg) "⚠️ 请先在「我的」中配置 API 提供商\n" else "") +
            "\n🛠 84 个工具就绪 | 🧠 长期记忆 | ✊ Root 通道\n" +
            "试试: 「打开飞行模式」、「截图」、「列出 WiFi」")
    }
}

data class TokenStats(
    val sessionTokensIn: Long = 0,
    val sessionTokensOut: Long = 0,
    val lastTurnIn: Int = 0,
    val lastTurnOut: Int = 0,
)
