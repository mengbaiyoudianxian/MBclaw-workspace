package com.mbclaw.root.api

import com.mbclaw.root.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * MBclaw-Lite 服务端 API 接口
 *
 * 全部端点：26 个 Router，客户端对接核心 4 个
 */
interface MBclawApiService {

    // ── Agent 对话 ──

    @POST("/agent/run")
    suspend fun agentChat(
        @Query("project_id") projectId: Int,
        @Body payload: AgentRequest,
    ): Response<AgentResponse>

    // ── 记忆搜索 (L1/L2/L3 分层) ──

    @GET("/search")
    suspend fun searchMemory(
        @Query("q") query: String,
        @Query("project_id") projectId: Int = 1,
    ): Response<List<SearchResult>>

    // ── 健康检查 ──

    @GET("/health/health")
    suspend fun healthCheck(): Response<HealthResponse>

    // ── Agent 状态 ──

    @GET("/agent/status")
    suspend fun agentStatus(
        @Query("project_id") projectId: Int,
    ): Response<AgentResponse>
}


    // ── 工具系统 (R0-ext) ──

    @GET("/tools")
    suspend fun listTools(
        @Query("category") category: String? = null,
    ): Response<List<ToolInfo>>

    @GET("/tools/search")
    suspend fun searchTools(
        @Query("q") query: String,
    ): Response<List<ToolInfo>>

    @POST("/tools/execute")
    suspend fun executeTool(
        @Body payload: ToolExecuteRequest,
    ): Response<ToolExecuteResponse>

    @GET("/agent/run")
    suspend fun agentRun(
        @Query("project_id") projectId: Int? = null,
        @Body payload: AgentRequest,
    ): Response<AgentResponse>

    data class ToolInfo(
        val id: Int,
        val name: String,
        val category: String,
        val summary: String,
        val tags: List<String>,
        val usage_count: Int
    )

    data class ToolExecuteRequest(
        val name: String,
        val content: String
    )

    data class ToolExecuteResponse(
        val name: String,
        val result: String
    )
