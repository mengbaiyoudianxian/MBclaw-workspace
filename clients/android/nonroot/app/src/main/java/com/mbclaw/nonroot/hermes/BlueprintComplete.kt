package com.mbclaw.nonroot.hermes

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mbclaw.nonroot.data.LocalDB
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 蓝图 10-memory-system-blueprint.md 完整补齐
 *
 * 每个组件在蓝图中有定义，Android端实现40%本地版本
 */
class BlueprintComplete(private val context: Context, private val db: LocalDB) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ═══════════════════════════════════════════
    // P2: 4.1 Session Complete 完整10步流程
    // ═══════════════════════════════════════════

    suspend fun sessionCompleteFullFlow(sessionId: String) {
        withContext(Dispatchers.IO) {
            // Step 1: generate_summary
            generateSummary(sessionId)
            // Step 2: extract_keywords
            extractKeywordsToTable(sessionId)
            // Step 3: update_dna_from_session
            updateProjectDna(sessionId)
            // Step 4: memory_flush → daily note
            flushToDailyNote(sessionId)
            // Step 5: write_final_transcript (TranscriptLogger handles this)
            // Step 6: extract_action_memories
            extractActionMemories(sessionId)
            // Step 7: classify_session → topic_tree
            classifyToTopicTree(sessionId)
            // Step 8: update_keyword_index
            updateKeywordIndex(sessionId)
            // Step 9: check_breakthrough
            checkBreakthrough(sessionId)
            // Step 10: publish_to_shared_channel
            publishReflection(sessionId)
        }
    }

    // ── Step 1: generate_summary ──
    private fun generateSummary(sessionId: String) {
        val msgs = db.getMessages(sessionId, 100)
        if (msgs.isEmpty()) return
        val topic = msgs.first().content.take(60)
        val conclusions = msgs.filter { it.role == "assistant" }.joinToString("; ") { it.content.take(100) }
        val cv = ContentValues().apply {
            put("session_id", sessionId); put("topic", topic)
            put("conclusions", conclusions); put("created_at", System.currentTimeMillis())
        }
        db.writableDatabase.insertWithOnConflict("summaries", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ── Step 2: extract_keywords ──
    private fun extractKeywordsToTable(sessionId: String) {
        val msgs = db.getMessages(sessionId, 100)
        val text = msgs.joinToString(" ") { it.content }
        val words = text.lowercase().split(Regex("[\\s，。！？、：；\"'（）\\[\\]{}]")).filter { it.length in 2..10 }
        val freq = words.groupingBy { it }.eachCount()
        freq.entries.sortedByDescending { it.value }.take(10).forEach { (kw, w) ->
            db.writableDatabase.insert("keywords", null, ContentValues().apply {
                put("session_id", sessionId); put("keyword", kw); put("weight", w.toFloat())
            })
        }
    }

    // ── Step 3: update_project_dna ──
    private fun updateProjectDna(sessionId: String) {
        val existing = db.readableDatabase.rawQuery("SELECT * FROM project_dna WHERE project_id='default'", null)
        val now = System.currentTimeMillis()
        if (!existing.moveToFirst()) {
            db.writableDatabase.insert("project_dna", null, ContentValues().apply {
                put("project_id", "default"); put("updated_at", now)
            })
        }; existing.close()
        db.writableDatabase.execSQL("UPDATE project_dna SET updated_at=? WHERE project_id='default'", arrayOf(now.toString()))
    }

    // ── Step 4: flush_to_daily_note ──
    private fun flushToDailyNote(sessionId: String) {
        val msgs = db.getMessages(sessionId, 30)
        val note = msgs.joinToString("\n") { "[${it.role}] ${it.content.take(200)}" }
        db.saveMemory("daily_${System.currentTimeMillis() / 86400000}", note, "daily_flush")
    }

    // ── Step 5: write_final_transcript (handled by TranscriptLogger) ──

    // ── Step 6: extract_action_memories ──
    private fun extractActionMemories(sessionId: String) {
        val msgs = db.getMessages(sessionId, 50)
        val actions = mutableListOf<Triple<String, String, String>>() // action, timing, expiry
        for (msg in msgs) {
            if (msg.content.contains("执行") || msg.content.contains("运行") || msg.content.contains("调用")) {
                actions.add(Triple(msg.content.take(100), "on_demand", ""))
            }
            if (msg.content.contains("每天") || msg.content.contains("定时")) {
                actions.add(Triple(msg.content.take(100), "daily", "24h"))
            }
        }
        actions.take(10).forEach { (action, timing, expiry) ->
            db.writableDatabase.insert("action_memories", null, ContentValues().apply {
                put("session_id", sessionId); put("action", action)
                put("timing", timing); put("expiry", expiry)
            })
        }
    }

    // ── Step 7: classify_to_topic_tree (蓝图 7.1 算法) ──
    private fun classifyToTopicTree(sessionId: String) {
        val summaryRow = db.readableDatabase.rawQuery("SELECT topic, conclusions FROM summaries WHERE session_id=?", arrayOf(sessionId))
        val summary = if (summaryRow.moveToFirst()) "${summaryRow.getString(0)} ${summaryRow.getString(1)}" else ""
        summaryRow.close()
        if (summary.isBlank()) return

        // 在 topic_tree 中找最匹配节点
        val allNodes = db.readableDatabase.rawQuery("SELECT id, name, summary FROM topic_tree", null)
        var bestId: Long? = null; var bestScore = 0f
        while (allNodes.moveToNext()) {
            val nodeSummary = allNodes.getString(2) ?: ""
            val score = keywordOverlap(summary, nodeSummary)
            if (score > bestScore) { bestScore = score; bestId = allNodes.getLong(0) }
        }; allNodes.close()

        val now = System.currentTimeMillis()
        if (bestScore > 0.5f && bestId != null) {
            // 归入已有节点
            val current = db.readableDatabase.rawQuery("SELECT session_refs FROM topic_tree WHERE id=?", arrayOf(bestId.toString()))
            val refs = if (current.moveToFirst()) JSONArray(current.getString(0) ?: "[]") else JSONArray()
            current.close(); refs.put(sessionId)
            db.writableDatabase.execSQL("UPDATE topic_tree SET session_refs=?, updated_at=? WHERE id=?", arrayOf(refs.toString(), now.toString(), bestId.toString()))
        } else {
            // 新建节点
            db.writableDatabase.insert("topic_tree", null, ContentValues().apply {
                put("name", summary.take(50)); put("node_type", "session_ref")
                put("summary", summary.take(200)); put("session_refs", JSONArray(listOf(sessionId)).toString())
                put("created_at", now); put("updated_at", now)
            })
        }
    }

    // ── Step 8: update_keyword_index ──
    private fun updateKeywordIndex(sessionId: String) {
        val kws = db.readableDatabase.rawQuery("SELECT keyword, weight FROM keywords WHERE session_id=?", arrayOf(sessionId))
        val now = System.currentTimeMillis()
        while (kws.moveToNext()) {
            val kw = kws.getString(0); val weight = kws.getDouble(1)
            val existing = db.readableDatabase.rawQuery("SELECT session_ids FROM keyword_index WHERE keyword=?", arrayOf(kw))
            val ids = if (existing.moveToFirst()) JSONArray(existing.getString(0) ?: "[]") else JSONArray()
            existing.close()
            if (!ids.toString().contains(sessionId)) ids.put(sessionId)
            val cv = ContentValues().apply {
                put("keyword", kw); put("session_ids", ids.toString()); put("weight", weight.toFloat()); put("updated_at", now)
            }
            db.writableDatabase.insertWithOnConflict("keyword_index", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }; kws.close()
    }

    // ── Step 9: check_breakthrough (蓝图 7.2 算法) ──
    private fun checkBreakthrough(sessionId: String) {
        val dna = db.readableDatabase.rawQuery("SELECT successful_approaches, failed_approaches FROM project_dna WHERE project_id='default'", null)
        if (!dna.moveToFirst()) { dna.close(); return }
        val oldSuccess = JSONArray(dna.getString(0) ?: "[]"); dna.close()

        val msgs = db.getMessages(sessionId, 20)
        val text = msgs.joinToString(" ") { it.content }
        val triggers = listOf("突破", "bug fixed", "解决了", "终于", "搞定了", "fixed", "solved", "完成", "实现")
        val matched = triggers.any { text.contains(it, ignoreCase = true) }

        if (matched) {
            val newApproach = text.take(200)
            val newSuccess = JSONArray(oldSuccess.toString()); newSuccess.put(newApproach)
            db.writableDatabase.execSQL("UPDATE project_dna SET successful_approaches=? WHERE project_id='default'", arrayOf(newSuccess.toString()))
            // Trigger snapshot
            db.writableDatabase.insert("snapshots", null, ContentValues().apply {
                put("tag", "breakthrough_${System.currentTimeMillis()}"); put("trigger_reason", "keyword_match")
                put("created_at", System.currentTimeMillis())
            })
        }
    }

    // ── Step 10: publish_reflection ──
    private fun publishReflection(sessionId: String) {
        val msgs = db.getMessages(sessionId, 30)
        val findings = msgs.filter { it.role == "assistant" }.map { it.content.take(150) }
        val problems = msgs.filter { it.content.contains("错误") || it.content.contains("失败") || it.content.contains("error") }.map { it.content.take(100) }
        if (findings.isEmpty()) return
        db.writableDatabase.insert("shared_channel", null, ContentValues().apply {
            put("agent_id", "session_$sessionId"); put("task", "反思: ${msgs.firstOrNull()?.content?.take(80) ?: "未知"}")
            put("findings", JSONArray(findings).toString())
            put("problems", JSONArray(problems).toString()); put("created_at", System.currentTimeMillis())
        })
    }

    // ═══════════════════════════════════════════
    // P11: 蓝图 4.6 三层工具索引
    // ═══════════════════════════════════════════

    fun registerTool(name: String, summary: String, fullDescription: String, tags: List<String> = emptyList()) {
        db.writableDatabase.insert("tools", null, ContentValues().apply {
            put("name", name); put("summary", summary.take(100))
            put("full_description", fullDescription); put("tags", JSONArray(tags).toString())
            put("created_at", System.currentTimeMillis())
        })
    }

    fun getToolsL1(): List<Pair<String, String>> { // L1: 摘要 (Token极低)
        val c = db.readableDatabase.rawQuery("SELECT name, summary FROM tools ORDER BY usage_count DESC LIMIT 20", null)
        val list = mutableListOf<Pair<String, String>>()
        while (c.moveToNext()) list.add(c.getString(0) to c.getString(1))
        c.close(); return list
    }

    fun getToolDetail(name: String): String { // L3: 完整描述
        val c = db.readableDatabase.rawQuery("SELECT full_description FROM tools WHERE name=?", arrayOf(name))
        val desc = if (c.moveToFirst()) c.getString(0) else "未找到工具"
        c.close()
        db.writableDatabase.execSQL("UPDATE tools SET usage_count=usage_count+1 WHERE name=?", arrayOf(name))
        return desc
    }

    fun searchToolsByTag(tag: String): List<String> { // L2: 标签匹配
        val c = db.readableDatabase.rawQuery("SELECT name, tags FROM tools", null)
        val list = mutableListOf<String>()
        while (c.moveToNext()) {
            val tags = c.getString(1) ?: "[]"
            if (tags.contains(tag, ignoreCase = true)) list.add(c.getString(0))
        }; c.close(); return list
    }

    // ═══════════════════════════════════════════
    // P12: 蓝图 4.7 模型联合优化调度
    // ═══════════════════════════════════════════

    fun registerModel(name: String, provider: String, apiKeyRef: String, capabilities: Map<String, Float>, costIn: Float = 0f, costOut: Float = 0f) {
        db.writableDatabase.insert("model_profiles", null, ContentValues().apply {
            put("name", name); put("provider", provider); put("api_key_ref", apiKeyRef)
            put("capabilities", JSONObject(capabilities as Map<*, *>).toString())
            put("cost_per_1k_input", costIn); put("cost_per_1k_output", costOut)
            put("created_at", System.currentTimeMillis())
        })
    }

    /** 蓝图 7.4: 为任务类型自动选择最优模型 */
    fun selectOptimalModel(taskType: String, budget: Float = 10f): String {
        val c = db.readableDatabase.rawQuery("SELECT name, capabilities, cost_per_1k_input, cost_per_1k_output FROM model_profiles WHERE is_available=1", null)
        var bestName = "deepseek-chat"; var bestScore = -1f
        while (c.moveToNext()) {
            val caps = JSONObject(c.getString(1))
            val cost = c.getFloat(2) + c.getFloat(3)
            val score = when (taskType) {
                "coding" -> 0.5f * caps.optDouble("coding", 0.5).toFloat() + 0.2f * caps.optDouble("reasoning", 0.5).toFloat()
                "analysis" -> 0.1f * caps.optDouble("coding", 0.5).toFloat() + 0.6f * caps.optDouble("reasoning", 0.5).toFloat()
                else -> caps.optDouble("general", 0.5).toFloat()
            }
            val finalScore = score * 0.7f - (cost / budget) * 0.3f
            if (finalScore > bestScore) { bestScore = finalScore; bestName = c.getString(0) }
        }; c.close(); return bestName
    }

    // ═══════════════════════════════════════════
    // P7: 蓝图 4.3 任务优先级队列 + checkpoint
    // ═══════════════════════════════════════════

    fun taskEnqueue(taskType: String, priority: Int, payload: String) {
        db.writableDatabase.insert("task_queue", null, ContentValues().apply {
            put("task_type", taskType); put("priority", priority)
            put("payload", payload); put("status", "queued")
            put("created_at", System.currentTimeMillis()); put("updated_at", System.currentTimeMillis())
        })
    }

    fun taskSaveCheckpoint(taskId: Long, state: String) {
        db.writableDatabase.execSQL("UPDATE task_queue SET checkpoint=?, status='paused', updated_at=? WHERE id=?", arrayOf(state, System.currentTimeMillis().toString(), taskId.toString()))
    }

    fun taskRestoreCheckpoint(taskId: Long): String? {
        val c = db.readableDatabase.rawQuery("SELECT checkpoint FROM task_queue WHERE id=?", arrayOf(taskId.toString()))
        val cp = if (c.moveToFirst()) c.getString(0) else null; c.close()
        db.writableDatabase.execSQL("UPDATE task_queue SET status='running' WHERE id=?", arrayOf(taskId.toString()))
        return cp
    }

    fun taskDequeue(): Triple<Long, Int, String>? {
        val c = db.readableDatabase.rawQuery("SELECT id, priority, payload FROM task_queue WHERE status='queued' ORDER BY priority DESC LIMIT 1", null)
        if (!c.moveToFirst()) { c.close(); return null }
        val id = c.getLong(0); val pri = c.getInt(1); val payload = c.getString(2); c.close()
        db.writableDatabase.execSQL("UPDATE task_queue SET status='running' WHERE id=?", arrayOf(id.toString()))
        return Triple(id, pri, payload)
    }

    // ═══════════════════════════════════════════
    // embedding cache (蓝图五: embeddings/)
    // ═══════════════════════════════════════════

    private val cacheDir = File(context.filesDir, "hermes/embeddings")

    fun cacheEmbedding(type: String, id: String, vector: FloatArray) {
        cacheDir.mkdirs()
        File(cacheDir, "${type}_${id}.json").writeText(JSONArray(vector.toList()).toString())
    }

    fun getCachedEmbedding(type: String, id: String): FloatArray? {
        val file = File(cacheDir, "${type}_${id}.json")
        if (!file.exists()) return null
        val arr = JSONArray(file.readText())
        return FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
    }

    // ═══════════════════════════════════════════
    // Scheduler (蓝图 4.1: idle_scheduler)
    // ═══════════════════════════════════════════

    @Volatile var lastIdleTrigger: Long = 0

    fun startIdleScheduler() {
        scope.launch {
            while (isActive) {
                delay(60_000)
                if (System.currentTimeMillis() - lastIdleTrigger > 120_000) { // 2分钟空闲
                    idleMaintenance()
                }
            }
        }
    }

    private fun idleMaintenance() {
        lastIdleTrigger = System.currentTimeMillis()
        // Curator: 30天清理
        val stale = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        db.writableDatabase.execSQL("DELETE FROM memory WHERE accessed_at < ? AND access_count < 2", arrayOf(stale.toString()))
        // 归档旧transcripts
        val old = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
        db.writableDatabase.execSQL("DELETE FROM messages WHERE created_at < ?", arrayOf(old.toString()))
    }

    // ═══════════════════════════════════════════
    // 蓝图八: LLM Adapter 系统
    // ═══════════════════════════════════════════

    data class LLMAdapter(val name: String, val baseUrl: String, val isOpenAICompatible: Boolean = true)

    private val adapters = mutableListOf<LLMAdapter>()

    fun registerAdapter(adapter: LLMAdapter) { adapters.add(adapter) }

    fun getAdapter(provider: String): LLMAdapter? = adapters.find { it.name.equals(provider, ignoreCase = true) }

    fun listAdapters(): List<LLMAdapter> = adapters.toList()

    // ═══════════════════════════════════════════
    // 统计
    // ═══════════════════════════════════════════

    fun getFullStats(): Map<String, Any> {
        fun count(table: String): Int {
            val c = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null)
            val n = if (c.moveToFirst()) c.getInt(0) else 0; c.close(); return n
        }
        return mapOf(
            "messages" to count("messages"), "sessions" to count("sessions"),
            "memory" to count("memory"), "skills" to count("skills"),
            "summaries" to count("summaries"), "keywords" to count("keywords"),
            "topic_tree_nodes" to count("topic_tree"), "keyword_index_entries" to count("keyword_index"),
            "tools" to count("tools"), "model_profiles" to count("model_profiles"),
            "action_memories" to count("action_memories"), "task_queue" to count("task_queue"),
            "snapshots" to count("snapshots"), "shared_channel" to count("shared_channel"),
            "embedding_cache" to (cacheDir.listFiles()?.size ?: 0),
        )
    }

    private fun keywordOverlap(a: String, b: String): Float {
        val setA = a.split(Regex("[\\s，。！？、：；]+")).filter { it.length >= 2 }.toSet()
        val setB = b.split(Regex("[\\s，。！？、：；]+")).filter { it.length >= 2 }.toSet()
        if (setA.isEmpty() || setB.isEmpty()) return 0f
        return setA.intersect(setB).size.toFloat() / maxOf(setA.size, setB.size)
    }
}
