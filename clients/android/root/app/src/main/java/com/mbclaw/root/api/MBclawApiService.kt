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
