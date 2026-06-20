package com.mbclaw.nonroot.hermes

import com.mbclaw.nonroot.api.DirectApiClient
import com.mbclaw.nonroot.api.ChatMessage as ApiMsg
import com.mbclaw.nonroot.data.LocalDB
import com.mbclaw.nonroot.data.UserSettings
import com.mbclaw.nonroot.model.ProviderCatalog
import kotlinx.coroutines.*

/**
 * 真实引擎 — 所有"40%"本地实现全部调用配置的LLM
 *
 * 不假实现，每个方法都真的调AI做事
 */
class RealEngine(
    private val db: LocalDB,
    private val settings: UserSettings,
) {
    private fun isReady(): Boolean = settings.isConfigured()
    private fun baseUrl(): String = settings.apiBaseUrl.ifBlank {
        ProviderCatalog.find(settings.providerId)?.baseUrl ?: ""
    }

    // ═══════════════════════════════════════════
    // 🌙 梦想整合 — LLM 驱动
    // ═══════════════════════════════════════════
    suspend fun dream(sessionId: String = ""): String {
        if (!isReady()) return "❌ 请先配置AI提供商"

        val msgs = db.getMessages(sessionId.ifBlank { "" }, 50)
        if (msgs.isEmpty()) return "暂无对话记录，开始聊天后我会自动整理。"

        val history = msgs.joinToString("\n") { "[${it.role}] ${it.content.take(300)}" }
        val prompt = listOf(
            ApiMsg("system", "你是MBclaw的梦想整合引擎。从以下对话历史中提取：\n1. 今日主题(1行)\n2. 关键洞察(2-3条)\n3. 用户偏好变化\n4. 值得记住的经验教训\n5. 下一步建议\n\n用中文，简洁有力，每点不超过2行。"),
            ApiMsg("user", "对话历史:\n$history\n\n请生成梦想整合报告。")
        )

        return try {
            withContext(Dispatchers.IO) {
                DirectApiClient.chat(baseUrl(), settings.apiKey, settings.modelName, prompt)
            }
        } catch (e: Exception) { "🌙 梦想引擎异常: ${e.message}" }
    }

    // ═══════════════════════════════════════════
    // 🔍 双Key评审 — LLM自评
    // ═══════════════════════════════════════════
    suspend fun dualKeyReview(content: String): String {
        if (!isReady()) return "❌ 请先配置AI提供商"
        if (content.isBlank()) return "无可评审内容"

        val prompt = listOf(
            ApiMsg("system", "你是MBclaw的代码/内容评审引擎。对以下内容进行严格评审：\n1. 质量评分 (1-10)\n2. 逻辑问题 (如有)\n3. 安全性问题 (如有)\n4. 改进建议 (1-2条)\n\n用中文，直接输出，不要客套话。"),
            ApiMsg("user", "请评审以下内容:\n---\n${content.take(3000)}\n---")
        )

        return try {
            withContext(Dispatchers.IO) {
                DirectApiClient.chat(baseUrl(), settings.apiKey, settings.modelName, prompt)
            }
        } catch (e: Exception) { "🔍 评审引擎异常: ${e.message}" }
    }

    // ═══════════════════════════════════════════
    // 💥 思维碰撞 — LLM 创新
    // ═══════════════════════════════════════════
    suspend fun collision(keywords: List<String>): String {
        if (!isReady()) return "❌ 请先配置AI提供商"
        if (keywords.size < 2) return "需要至少2个关键词进行思维碰撞"

        val kwStr = keywords.joinToString(" + ")
        val prompt = listOf(
            ApiMsg("system", "你是MBclaw的创新引擎。将给定的关键词进行思维碰撞，生成3-5个新颖的交叉创新点子。每个点子：名称(1句话) + 简要说明(2-3句话)。要有想象力和实用价值。用中文。"),
            ApiMsg("user", "对以下关键词进行思维碰撞: $kwStr")
        )

        return try {
            withContext(Dispatchers.IO) {
                DirectApiClient.chat(baseUrl(), settings.apiKey, settings.modelName, prompt)
            }
        } catch (e: Exception) { "💥 碰撞引擎异常: ${e.message}" }
    }

    // ═══════════════════════════════════════════
    // 📝 会话总结 — LLM 生成
    // ═══════════════════════════════════════════
    suspend fun summarizeSession(sessionId: String): String {
        if (!isReady()) return "未配置AI"

        val msgs = db.getMessages(sessionId, 100)
        if (msgs.isEmpty()) return "无对话内容"

        val history = msgs.joinToString("\n") { "[${it.role}] ${it.content.take(300)}" }
        val prompt = listOf(
            ApiMsg("system", "你是MBclaw的总结引擎。对对话做简洁总结：主题(1行) + 结论(2-3条) + 下一步(1条)。用中文。"),
            ApiMsg("user", "对话:\n$history\n\n请总结。")
        )

        return try {
            withContext(Dispatchers.IO) {
                DirectApiClient.chat(baseUrl(), settings.apiKey, settings.modelName, prompt)
            }
        } catch (e: Exception) { "📝 总结异常: ${e.message}" }
    }

    // ═══════════════════════════════════════════
    // 🏷 关键词提取 — LLM 提取
    // ═══════════════════════════════════════════
    suspend fun extractKeywords(sessionId: String): List<String> {
        if (!isReady()) return emptyList()

        val msgs = db.getMessages(sessionId, 50)
        val text = msgs.joinToString(" ") { it.content.take(200) }
        if (text.isBlank()) return emptyList()

        val prompt = listOf(
            ApiMsg("system", "提取对话中的关键技术关键词。只输出关键词，逗号分隔，不超过15个。用中文。"),
            ApiMsg("user", "对话: ${text.take(2000)}\n\n提取关键词:")
        )

        return try {
            val resp = withContext(Dispatchers.IO) {
                DirectApiClient.chat(baseUrl(), settings.apiKey, settings.modelName, prompt)
            }
            resp.split(Regex("[,，、\\s]+")).filter { it.length in 2..15 }.take(15)
        } catch (e: Exception) { emptyList() }
    }

    // ═══════════════════════════════════════════
    // 🎯 智能分类 — LLM 语义分类
    // ═══════════════════════════════════════════
    suspend fun classifyContent(text: String, existingCategories: List<String>): Pair<String, Float> {
        if (!isReady()) return "未分类" to 0f

        val catStr = existingCategories.joinToString(", ").ifBlank { "无现有分类" }
        val prompt = listOf(
            ApiMsg("system", "你是MBclaw的分类引擎。将以下内容归入最合适的类别。输出格式: \"类别名|0.0-1.0的置信度\"。如果现有类别都不合适，创建一个新类别名。用中文。"),
            ApiMsg("user", "现有类别: $catStr\n\n内容: ${text.take(1000)}\n\n分类结果:")
        )

        return try {
            val resp = withContext(Dispatchers.IO) {
                DirectApiClient.chat(baseUrl(), settings.apiKey, settings.modelName, prompt)
            }
            val parts = resp.split("|")
            if (parts.size >= 2) {
                parts[0].trim() to (parts[1].trim().toFloatOrNull() ?: 0.7f)
            } else {
                resp.trim().take(30) to 0.5f
            }
        } catch (e: Exception) { "分类失败" to 0f }
    }

    // ═══════════════════════════════════════════
    // 📊 心理画像 — LLM 分析
    // ═══════════════════════════════════════════
    suspend fun psychologyProfile(recentInteractions: List<String>): String {
        if (!isReady() || recentInteractions.isEmpty()) return "数据不足"

        val history = recentInteractions.joinToString("\n")
        val prompt = listOf(
            ApiMsg("system", "你是MBclaw的用户画像引擎。基于用户的最近交互行为，分析：\n1. 当前兴趣领域\n2. 技术偏好\n3. 使用模式(频率/时段/类型)\n4. 可能的潜在需求\n\n用中文，简洁。不要过度推测。"),
            ApiMsg("user", "最近交互:\n$history\n\n请生成用户画像分析。")
        )

        return try {
            withContext(Dispatchers.IO) {
                DirectApiClient.chat(baseUrl(), settings.apiKey, settings.modelName, prompt)
            }
        } catch (e: Exception) { "📊 画像引擎异常: ${e.message}" }
    }

    // ═══════════════════════════════════════════
    // 🛠 工具推荐 — LLM 推荐
    // ═══════════════════════════════════════════
    suspend fun recommendTool(userIntent: String, availableTools: List<String>): String {
        if (!isReady()) return availableTools.firstOrNull() ?: "无可用工具"

        val tools = availableTools.joinToString(", ")
        val prompt = listOf(
            ApiMsg("system", "你是MBclaw的工具推荐引擎。根据用户意图，从可用工具列表中选择最合适的工具并说明理由。用中文，一句话。"),
            ApiMsg("user", "用户意图: $userIntent\n可用工具: $tools\n推荐:")
        )

        return try {
            withContext(Dispatchers.IO) {
                DirectApiClient.chat(baseUrl(), settings.apiKey, settings.modelName, prompt)
            }
        } catch (e: Exception) { availableTools.first() }
    }
}
