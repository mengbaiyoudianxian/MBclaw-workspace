package com.mbclaw.nonroot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mbclaw.nonroot.api.NetworkModule
import com.mbclaw.nonroot.model.AgentRequest
import com.mbclaw.nonroot.model.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val role: String,           // "user" | "assistant" | "system"
    val content: String,
    val memoryRefs: List<SearchResult> = emptyList(),
    val isError: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isThinking: Boolean = false,
    val serverConnected: Boolean = false,
    val serverUrl: String = NetworkModule.getCurrentUrl(),
    val errorMessage: String? = null,
)

/**
 * 聊天 ViewModel — 对接 MBclaw 服务端 API
 */
class ChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private val api get() = NetworkModule.getService()

    init {
        // 启动问候语 + 健康检查
        checkServerHealth()
    }

    fun updateServerUrl(url: String) {
        NetworkModule.updateServerUrl(url)
        _uiState.value = _uiState.value.copy(serverUrl = url)
        checkServerHealth()
    }

    /** 健康检查 — 确认服务端可达 */
    fun checkServerHealth() {
        viewModelScope.launch {
            try {
                val resp = api.healthCheck()
                _uiState.value = _uiState.value.copy(
                    serverConnected = resp.isSuccessful,
                    errorMessage = if (resp.isSuccessful) null else "服务器返回 ${resp.code()}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    serverConnected = false,
                    errorMessage = "无法连接服务器: ${e.message}"
                )
            }
        }
    }

    /** 发送消息 — 调用 Agent API */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val state = _uiState.value
        // 添加用户消息
        val userMsg = ChatMessage(role = "user", content = text)
        _uiState.value = state.copy(
            messages = state.messages + userMsg,
            isThinking = true,
            errorMessage = null,
        )

        viewModelScope.launch {
            try {
                val resp = api.agentChat(
                    projectId = 1,
                    payload = AgentRequest(message = text),
                )

                if (resp.isSuccessful) {
                    val body = resp.body()
                    val reply = body?.response
                        ?: body?.result
                        ?: "MBclaw 返回了空响应"

                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + ChatMessage(
                            role = "assistant",
                            content = reply,
                        ),
                        isThinking = false,
                        serverConnected = true,
                    )
                } else {
                    val errBody = resp.errorBody()?.string() ?: "未知错误 (${resp.code()})"
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + ChatMessage(
                            role = "assistant",
                            content = "⚠️ 服务器错误: $errBody\n\n请检查服务器地址和网络连接。",
                            isError = true,
                        ),
                        isThinking = false,
                        serverConnected = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + ChatMessage(
                        role = "assistant",
                        content = "⚠️ 网络异常: ${e.message}\n\n请确认服务器 ${_uiState.value.serverUrl} 已启动并可访问。",
                        isError = true,
                    ),
                    isThinking = false,
                    serverConnected = false,
                )
            }
        }
    }

    /** 记忆搜索 */
    fun searchMemory(query: String) {
        viewModelScope.launch {
            try {
                val results = api.searchMemory(query = query)
                if (results.isSuccessful && results.body().isNullOrEmpty().not()) {
                    val refs = results.body()!!
                    val summary = buildString {
                        appendLine("🧠 记忆搜索结果 (${refs.size} 条):")
                        refs.take(5).forEach { r ->
                            appendLine("  • ${r.title ?: "无标题"}: ${r.content?.take(100) ?: ""}")
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + ChatMessage(
                            role = "system", content = summary, memoryRefs = refs
                        ),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + ChatMessage(
                            role = "system", content = "🧠 未找到相关记忆",
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + ChatMessage(
                        role = "system", content = "⚠️ 记忆搜索失败: ${e.message}", isError = true,
                    ),
                )
            }
        }
    }

    /** 清空对话 */
    fun clearChat() {
        _uiState.value = _uiState.value.copy(messages = emptyList())
    }
}
