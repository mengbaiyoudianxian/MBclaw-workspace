package com.mbclaw.root.data

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 本地存储 — 完全离线可用
 *
 * 服务器仅用于多端同步（可选）
 * 核心数据都在本地 SQLite + SharedPreferences
 */

// ── 用户配置 (SharedPreferences) ──

class UserSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mbclaw_settings", Context.MODE_PRIVATE)

    var providerId: String
        get() = prefs.getString("provider_id", "deepseek-cn") ?: "deepseek-cn"
        set(v) = prefs.edit().putString("provider_id", v).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v).apply()

    var apiBaseUrl: String
        get() = prefs.getString("api_base_url", "") ?: ""
        set(v) = prefs.edit().putString("api_base_url", v).apply()

    var modelName: String
        get() = prefs.getString("model_name", "deepseek-chat") ?: "deepseek-chat"
        set(v) = prefs.edit().putString("model_name", v).apply()

    var serverSyncEnabled: Boolean
        get() = prefs.getBoolean("server_sync", false)
        set(v) = prefs.edit().putBoolean("server_sync", v).apply()

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(v) = prefs.edit().putString("server_url", v).apply()

    var utopiaEnabled: Boolean
        get() = prefs.getBoolean("utopia_enabled", false)
        set(v) = prefs.edit().putBoolean("utopia_enabled", v).apply()

    fun canUploadKey(): Boolean = utopiaEnabled && serverSyncEnabled && serverUrl.isNotBlank()
    fun isConfigured(): Boolean = apiKey.isNotBlank() && modelName.isNotBlank()
}

// ── 本地数据库 (SQLite) ──

class LocalDB(context: Context) : SQLiteOpenHelper(
    context, "mbclaw_local.db", null, 1
) {
    override fun onCreate(db: SQLiteDatabase) {
        // 聊天消息
        db.execSQL("""
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                memory_refs TEXT
            )
        """)
        // 会话
        db.execSQL("""
            CREATE TABLE sessions (
                id TEXT PRIMARY KEY,
                title TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """)
        // 记忆条目
        db.execSQL("""
            CREATE TABLE memory (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                key TEXT NOT NULL,
                value TEXT NOT NULL,
                source TEXT,
                created_at INTEGER NOT NULL,
                accessed_at INTEGER NOT NULL,
                access_count INTEGER DEFAULT 0
            )
        """)
        // 技能卡
        db.execSQL("""
            CREATE TABLE skills (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT,
                trigger_keywords TEXT,
                created_at INTEGER NOT NULL
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {}

    // ── 消息操作 ──

    fun saveMessage(sessionId: String, role: String, content: String, memoryRefs: String? = null) {
        val cv = ContentValues().apply {
            put("session_id", sessionId)
            put("role", role)
            put("content", content)
            put("created_at", System.currentTimeMillis())
            put("memory_refs", memoryRefs)
        }
        writableDatabase.insert("messages", null, cv)
    }

    fun getMessages(sessionId: String, limit: Int = 100): List<MessageRow> {
        val c = readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE session_id=? ORDER BY id DESC LIMIT ?",
            arrayOf(sessionId, limit.toString())
        )
        val list = mutableListOf<MessageRow>()
        while (c.moveToNext()) {
            list.add(MessageRow(
                id = c.getLong(0),
                sessionId = c.getString(1),
                role = c.getString(2),
                content = c.getString(3),
                createdAt = c.getLong(4),
                memoryRefs = c.getString(5),
            ))
        }
        c.close()
        return list.reversed()
    }

    // ── 记忆操作 ──

    fun saveMemory(key: String, value: String, source: String? = null) {
        // Upsert — 如果已存在则更新
        val existing = readableDatabase.rawQuery(
            "SELECT id FROM memory WHERE key=?", arrayOf(key)
        )
        if (existing.moveToFirst()) {
            val cv = ContentValues().apply {
                put("value", value)
                put("accessed_at", System.currentTimeMillis())
            }
            writableDatabase.update("memory", cv, "key=?", arrayOf(key))
        } else {
            val cv = ContentValues().apply {
                put("key", key)
                put("value", value)
                put("source", source)
                put("created_at", System.currentTimeMillis())
                put("accessed_at", System.currentTimeMillis())
            }
            writableDatabase.insert("memory", null, cv)
        }
        existing.close()
    }

    fun searchMemory(query: String, limit: Int = 10): List<MemoryRow> {
        val c = readableDatabase.rawQuery(
            "SELECT * FROM memory WHERE key LIKE ? OR value LIKE ? ORDER BY access_count DESC LIMIT ?",
            arrayOf("%$query%", "%$query%", limit.toString())
        )
        val list = mutableListOf<MemoryRow>()
        while (c.moveToNext()) {
            list.add(MemoryRow(
                id = c.getLong(0),
                key = c.getString(1),
                value = c.getString(2),
                source = c.getString(3),
                createdAt = c.getLong(4),
                accessedAt = c.getLong(5),
                accessCount = c.getInt(6),
            ))
        }
        c.close()
        // 更新访问计数
        if (list.isNotEmpty()) {
            writableDatabase.execSQL(
                "UPDATE memory SET access_count=access_count+1, accessed_at=? WHERE key LIKE ? OR value LIKE ?",
                arrayOf(System.currentTimeMillis().toString(), "%$query%", "%$query%")
            )
        }
        return list
    }

    fun getAllMemoryKeys(): List<String> {
        val c = readableDatabase.rawQuery("SELECT key FROM memory ORDER BY access_count DESC", null)
        val list = mutableListOf<String>()
        while (c.moveToNext()) list.add(c.getString(0))
        c.close()
        return list
    }

    // ── 会话操作 ──

    fun createSession(title: String? = null): String {
        val id = java.util.UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("id", id)
            put("title", title ?: "新对话")
            put("created_at", now)
            put("updated_at", now)
        }
        writableDatabase.insert("sessions", null, cv)
        return id
    }

    fun updateSessionTitle(id: String, title: String) {
        val cv = ContentValues().apply {
            put("title", title)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("sessions", cv, "id=?", arrayOf(id))
    }

    fun getSessions(): List<SessionRow> {
        val c = readableDatabase.rawQuery(
            "SELECT * FROM sessions ORDER BY updated_at DESC", null
        )
        val list = mutableListOf<SessionRow>()
        while (c.moveToNext()) {
            list.add(SessionRow(
                id = c.getString(0),
                title = c.getString(1),
                createdAt = c.getLong(2),
                updatedAt = c.getLong(3),
            ))
        }
        c.close()
        return list
    }
}

// ── 数据行 ──

data class MessageRow(
    val id: Long, val sessionId: String, val role: String,
    val content: String, val createdAt: Long, val memoryRefs: String?,
)

data class MemoryRow(
    val id: Long, val key: String, val value: String,
    val source: String?, val createdAt: Long, val accessedAt: Long, val accessCount: Int,
)

data class SessionRow(
    val id: String, val title: String?, val createdAt: Long, val updatedAt: Long,
)
