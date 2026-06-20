package com.mbclaw.nonroot.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mbclaw.nonroot.api.DirectApiClient
import com.mbclaw.nonroot.data.LocalDB
import com.mbclaw.nonroot.data.MemoryRow
import com.mbclaw.nonroot.data.SessionRow
import com.mbclaw.nonroot.data.UserSettings
import com.mbclaw.nonroot.hermes.HermesMemory
import com.mbclaw.nonroot.hermes.HybridEngine
import com.mbclaw.nonroot.hermes.BlueprintComplete
import com.mbclaw.nonroot.hermes.RealEngine
import com.mbclaw.nonroot.agent.AgentLoop
import com.mbclaw.nonroot.model.ProviderCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<UIMessage> = emptyList(),
    val isThinking: Boolean = false,
    val providerName: String = "未配置",
    val modelName: String = "",
    val errorMessage: String? = null,
    val sessions: List<SessionRow> = emptyList(),
    val currentSessionId: String = "",
    val capability: String = "LOCAL(40%)",
    val memoryStats: Map<String, Any> = emptyMap(),
)

data class UIMessage(
    val role: String,
    val content: String,
    val memoryRefs: List<com.mbclaw.nonroot.hermes.LayeredSearch.SearchResult> = emptyList(),
    val isError: Boolean = false,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val settings = UserSettings(app)
    val db = LocalDB(app)
    val hermes = HermesMemory(app, db, settings)
    val hybrid = HybridEngine(app, db, settings)
    val blueprint = BlueprintComplete(app, db)
    val real = RealEngine(db, settings)
    val agentLoop = AgentLoop(app, db, settings)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    // 系统提示词 — MBclaw 身份
    // 不再靠prompt祈祷 — AgentLoop 用 MBclawEnforcer 代码强制执行

    init {
        viewModelScope.launch {
            refreshState()
            _uiState.value = _uiState.value.copy(
                capability = hybrid.capability(),
                memoryStats = blueprint.getFullStats(),
            )
        }
    }

    private fun refreshState() {
        val provider = ProviderCatalog.find(settings.providerId)
        _uiState.value = _uiState.value.copy(
            providerName = provider?.name ?: settings.providerId,
            modelName = settings.modelName,
            sessions = db.getSessions(),
        )
    }

    /** 发送消息 */
    fun sendMessage(text: String) {
        if (text.isBlank() || !settings.isConfigured()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = if (!settings.isConfigured()) "请先配置 API 提供商和 Key" else null
            )
            return
        }

        val state = _uiState.value
        // 确保有会话
        var sessionId = state.currentSessionId
        if (sessionId.isBlank()) {
            sessionId = db.createSession(title = text.take(30))
            _uiState.value = state.copy(currentSessionId = sessionId)
        }

        val userMsg = UIMessage(role = "user", content = text)
        db.saveMessage(sessionId, "user", text)

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            isThinking = true,
            errorMessage = null,
        )

        viewModelScope.launch {
            try {
                // ── Hermes P6: 实时记忆预调用 (L1→L2→L3) ──
                val memoryInjection = hermes.bootstrapSession(sessionId, text)
                val memories = hermes.layeredSearch.search(
                    com.mbclaw.nonroot.hermes.LayeredSearch.SearchContext(
                        query = text, maxResults = 5,
                        enableL3 = settings.utopiaEnabled,
                        embeddingApiBaseUrl = settings.apiBaseUrl,
                        embeddingApiKey = settings.apiKey,
                    )
                )

                // AgentLoop 内置 MBclawEnforcer 代码级约束
                // PRE: 强制注入记忆+工具+身份
                // POST: 验证+修正响应
                val reply = agentLoop.run(text, sessionId, maxTurns = 5)

                db.saveMessage(sessionId, "assistant", reply, null)
                // Hermes: 记录+分类 (P1+P2)
                hermes.afterTurn(sessionId, text, reply)

                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + UIMessage(
                        role = "assistant", content = reply,
                        memoryRefs = memories,
                    ),
                    isThinking = false,
                    sessions = db.getSessions(),
                )

                // 自动更新会话标题
                if (db.getMessages(sessionId).size <= 3) {
                    db.updateSessionTitle(sessionId, text.take(30))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + UIMessage(
                        role = "system",
                        content = "❌ ${e.message}\n\n请检查：\n1. API Key 是否正确\n2. 模型名称是否正确\n3. 网络是否连通",
                        isError = true,
                    ),
                    isThinking = false,
                )
            }
        }
    }

    /** 搜索本地记忆 */
    fun searchMemory(query: String) {
        val results = db.searchMemory(query, limit = 10)
        if (results.isNotEmpty()) {
            val msg = buildString {
                appendLine("🧠 本地记忆 (${results.size} 条)")
                results.forEach { r ->
                    appendLine("  • ${r.key}: ${r.value.take(150)}")
                }
            }
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + UIMessage(role = "system", content = msg)
            )
        } else {
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + UIMessage(
                    role = "system", content = "🧠 未找到相关记忆\n\n每条对话会自动保存，积累越多越有用。"
                )
            )
        }
    }

    fun newSession() {
        val oldId = _uiState.value.currentSessionId
        if (oldId.isNotBlank()) {
            // 旧会话结束 → 触发完整10步流程
            viewModelScope.launch {
                blueprint.sessionCompleteFullFlow(oldId)
                hermes.onSessionEnd(oldId)
                refreshMemoryStats()
            }
        }
        val id = db.createSession()
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            currentSessionId = id,
            sessions = db.getSessions(),
        )
    }

    /** 🌙 梦想整合 — LLM真实调用 */
    fun runDream() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isThinking = true)
            val result = real.dream(_uiState.value.currentSessionId)
            _uiState.value = _uiState.value.copy(
                isThinking = false,
                messages = _uiState.value.messages + UIMessage("system", "🌙 梦想整合报告\n\n$result")
            )
        }
    }

    /** 🔍 双Key评审 — LLM真实调用 */
    fun runDualKeyReview(content: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isThinking = true)
            val review = real.dualKeyReview(content)
            _uiState.value = _uiState.value.copy(
                isThinking = false,
                messages = _uiState.value.messages + UIMessage("system", "🔍 评审报告\n\n$review")
            )
        }
    }

    /** 💥 思维碰撞 — LLM真实调用 */
    fun runCollision(keywords: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isThinking = true)
            val ideas = real.collision(keywords)
            _uiState.value = _uiState.value.copy(
                isThinking = false,
                messages = _uiState.value.messages + UIMessage("system", "💥 思维碰撞\n\n$ideas")
            )
        }
    }

    /** 📝 会话总结 — LLM真实调用 */
    fun runSummarize() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isThinking = true)
            val summary = real.summarizeSession(_uiState.value.currentSessionId)
            _uiState.value = _uiState.value.copy(
                isThinking = false,
                messages = _uiState.value.messages + UIMessage("system", "📝 会话总结\n\n$summary")
            )
        }
    }

    /** 🏷 关键词提取 — LLM真实调用 */
    fun runExtractKeywords() {
        viewModelScope.launch {
            val keywords = real.extractKeywords(_uiState.value.currentSessionId)
            if (keywords.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + UIMessage("system", "🏷 关键词: ${keywords.joinToString(", ")}")
                )
            }
        }
    }

    /** 📊 用户画像 — LLM真实调用 */
    fun runPsychology() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isThinking = true)
            val profile = real.psychologyProfile(
                _uiState.value.messages.filter { it.role == "user" }.map { it.content.take(200) }
            )
            _uiState.value = _uiState.value.copy(
                isThinking = false,
                messages = _uiState.value.messages + UIMessage("system", "📊 用户画像\n\n$profile")
            )
        }
    }

    /** 分类树查询 — 用LLM对当前会话分类 */
    fun showClassificationTree() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isThinking = true)
            val msgs = _uiState.value.messages.joinToString(" ") { it.content.take(200) }
            val existingCats = hermes.getClassificationTree().map { it.title }
            val (category, confidence) = real.classifyContent(msgs, existingCats)

            val tree = hermes.getClassificationTree()
            val failed = hermes.getFailedApproaches()
            val stats = blueprint.getFullStats()
            val msg = buildString {
                appendLine("🎯 LLM分类结果: \"$category\" (置信度: ${"%.0f".format(confidence * 100)}%)")
                appendLine()
                appendLine("🌳 分类树 (${tree.size} 节点)")
                tree.take(5).forEach { node ->
                    appendLine("  ├ ${node.title} (${node.relatedSessions.size}会话)")
                }
                if (failed.isNotEmpty()) {
                    appendLine("❌ 失败方案 (${failed.size}):")
                    failed.take(3).forEach { appendLine("  ├ ${it.title}") }
                }
                appendLine("📊 库: 🧠${stats["memory"]} 💬${stats["messages"]} 🌳${stats["topic_tree_nodes"]} 📸${stats["snapshots"]}")
            }
            _uiState.value = _uiState.value.copy(
                isThinking = false,
                messages = _uiState.value.messages + UIMessage("system", msg)
            )
        }
    }

    /** 刷新记忆统计 */
    suspend fun refreshMemoryStats() {
        _uiState.value = _uiState.value.copy(
            capability = hybrid.capability(),
            memoryStats = blueprint.getFullStats(),
        )
    }

    fun switchSession(sessionId: String) {
        val msgs = db.getMessages(sessionId).map {
            UIMessage(role = it.role, content = it.content)
        }
        _uiState.value = _uiState.value.copy(
            messages = msgs,
            currentSessionId = sessionId,
        )
    }

    fun refreshProvider() {
        refreshState()
    }
}
