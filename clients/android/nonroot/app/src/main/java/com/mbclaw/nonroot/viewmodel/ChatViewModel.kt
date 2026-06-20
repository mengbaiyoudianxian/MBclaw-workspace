package com.mbclaw.nonroot.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mbclaw.nonroot.api.DirectApiClient
import com.mbclaw.nonroot.api.ChatMessage
import com.mbclaw.nonroot.data.LocalDB
import com.mbclaw.nonroot.data.MemoryRow
import com.mbclaw.nonroot.data.SessionRow
import com.mbclaw.nonroot.data.UserSettings
import com.mbclaw.nonroot.hermes.HermesMemory
import com.mbclaw.nonroot.hermes.HybridEngine
import com.mbclaw.nonroot.hermes.BlueprintComplete
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

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    // 系统提示词 — MBclaw 身份
    private val systemPrompt = ChatMessage(
        role = "system",
        content = "你是 MBclaw，由18岁的打工人孟白独立创造的 AI 助手。\n" +
            "核心能力：\n" +
            "- 记住用户说过的每一句话，跨会话回忆\n" +
            "- 搜索本地记忆库，引用过去的讨论\n" +
            "- 主动提供帮助，不是被动等待指令\n" +
            "- 简洁、精准、有温度的回复风格\n" +
            "你不是 ChatGPT，你不是 Claude，你是独一无二的 MBclaw。"
    )

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

                // 构建消息列表: system + history + new
                val apiMessages = mutableListOf(systemPrompt)
                // 取最近 20 条历史作为上下文
                val history = db.getMessages(sessionId, limit = 20)
                for (msg in history.takeLast(20).dropLast(1)) { // 去掉刚加的 user msg
                    apiMessages.add(ChatMessage(role = msg.role, content = msg.content))
                }
                // 注入 Hermes 记忆 (P6)
                val memoryCtx = hermes.layeredSearch.formatForInjection(memories)
                if (memoryCtx.isNotBlank()) {
                    apiMessages.add(ChatMessage(role = "system", content = memoryCtx))
                }
                apiMessages.add(ChatMessage(role = "user", content = text))

                // 调用配置的 API
                val baseUrl = if (settings.apiBaseUrl.isNotBlank())
                    settings.apiBaseUrl
                else
                    ProviderCatalog.find(settings.providerId)?.baseUrl ?: settings.apiBaseUrl

                val reply = DirectApiClient.chat(
                    baseUrl = baseUrl,
                    apiKey = settings.apiKey,
                    model = settings.modelName,
                    messages = apiMessages,
                )

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

    /** 触发梦想整合 */
    fun runDream() {
        viewModelScope.launch {
            val cap = hybrid.capability()
            val dreamResult = hybrid.route(
                local = { hybrid.localDream("default") },
                server = { hybrid.serverDream("default") }
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + UIMessage("system", "🌙 ${cap}\n\n$dreamResult")
            )
        }
    }

    /** 触发双key评审 */
    fun runDualKeyReview(content: String) {
        viewModelScope.launch {
            val (score, feedback) = hybrid.localDualKeyReview(content)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + UIMessage("system", "🔍 双Key评审: ${"%.1f".format(score)}/10\n$feedback")
            )
        }
    }

    /** 思维碰撞 */
    fun runCollision(keywords: List<String>) {
        viewModelScope.launch {
            val ideas = hybrid.localCollision(keywords)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + UIMessage("system", "💥 思维碰撞:\n" + ideas.joinToString("\n"))
            )
        }
    }

    /** 分类树查询 */
    fun showClassificationTree() {
        val tree = hermes.getClassificationTree()
        val failed = hermes.getFailedApproaches()
        val stats = blueprint.getFullStats()
        val msg = buildString {
            appendLine("🌳 分类树 (${tree.size} 根节点)")
            tree.take(5).forEach { node ->
                appendLine("  ├ ${node.title} (${node.relatedSessions.size}会话, ${node.keywords.size}关键词)")
            }
            if (failed.isNotEmpty()) {
                appendLine("❌ 失败方案 (${failed.size}):")
                failed.take(3).forEach { appendLine("  ├ ${it.title}: ${it.summary.take(60)}") }
            }
            appendLine("\n📊 数据库统计:")
            stats.forEach { (k, v) -> appendLine("  $k: $v") }
        }
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + UIMessage("system", msg)
        )
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
