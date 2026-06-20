package com.mbclaw.root.hand

import android.content.Context
import android.graphics.Point
import com.mbclaw.root.data.UserSettings
import kotlinx.coroutines.*
import kotlin.math.abs

/**
 * 智能体之手 — 主编排器
 *
 * 完整工作流:
 *   输入: (截图base64, 操作描述, 精度要求)
 *     → 检查DNA记忆 → 模糊点击 → 粗筛 → 精定位 → 融合决策 → 执行 → 验证 → 记录
 *   输出: (操作类型, 目标坐标, 置信度)
 */

class AgentHand(
    private val context: Context,
    private val settings: UserSettings,
    private val mode: HandMode = HandMode.BALANCE,
    private val executor: (Point) -> Boolean,  // 点击执行器: 接收物理坐标, 返回是否成功
) {

    private val config = when (mode) {
        HandMode.SPEED -> HandConfig.speed()
        HandMode.BALANCE -> HandConfig.balance()
        HandMode.PRECISE -> HandConfig.precise()
    }

    val calibration = ScreenCalibration(context)
    private val fuzzyClicker = FuzzyClicker()
    val memory = OperationMemory(context)
    private val blockRecognizer = BlockRecognizer(settings, config)
    private val fusionDecider = FusionDecider(config, calibration.screenSize.x, calibration.screenSize.y)

    data class HandInput(
        val screenshotBase64: String,
        val operationDesc: String,
        val packageName: String = "unknown",
        val layoutHash: String = "default",
    )

    data class HandOutput(
        val success: Boolean,
        val operationType: String,     // "tap" | "long_press" | "swipe" | "input"
        val normalizedX: Int,
        val normalizedY: Int,
        val physicalX: Int,
        val physicalY: Int,
        val confidence: Float,
        val methodUsed: String,        // "memory" | "fuzzy" | "fine" | "coarse" | "fusion" | "fallback"
        val duration: Long,            // 总耗时ms
        val errorReason: String = "",  // 失败时填
    )

    // ── 主编排 ──

    suspend fun execute(input: HandInput): HandOutput = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val screenSig = memory.screenSignature(input.packageName, calibration.screenSize.x, calibration.screenSize.y, input.layoutHash)

        try {
            // ═══ STEP 0: 检查DNA记忆 ═══
            val historicalOp = memory.findSimilar(screenSig, input.operationDesc)
            if (historicalOp != null && historicalOp.confidence >= 0.85f) {
                val phys = calibration.normalizedToPhysical(historicalOp.x, historicalOp.y)
                val ok = executor(phys)
                val elapsed = System.currentTimeMillis() - startTime
                memory.record(OperationMemory.OpRecord(
                    sessionId = "hand_${startTime}", deviceId = calibration.screenSize.toString(),
                    screenSignature = screenSig, operationDesc = input.operationDesc,
                    methodUsed = "memory", x = historicalOp.x, y = historicalOp.y,
                    success = ok, confidence = historicalOp.confidence,
                ))
                return@withContext HandOutput(ok, "tap", historicalOp.x, historicalOp.y, phys.x, phys.y,
                    historicalOp.confidence, "memory", elapsed)
            }

            // ═══ STEP 1: 三条通道并行启动 ═══

            // 通道A: 快速模糊点击
            val fuzzyResult = if (config.fuzzyEnabled) {
                var fuzzy = fuzzyClicker.matchKeyword(input.operationDesc)
                if (fuzzy == null || fuzzy.confidence < config.fuzzyThreshold) {
                    fuzzy = fuzzyClicker.matchHistorical(screenSig, input.operationDesc, memory)
                }
                fuzzy
            } else null

            // 通道B: 区块识别
            val coarseResults = blockRecognizer.coarseLocate(input.screenshotBase64, input.operationDesc)

            // 通道C: 精定位
            val fineResults = if (coarseResults.isNotEmpty()) {
                blockRecognizer.fineLocate(input.screenshotBase64, input.operationDesc, coarseResults, config.fineRounds)
            } else {
                // 粗筛失败 → 降级: 全屏直接定位
                val fallback = blockRecognizer.fullScreenLocate(input.screenshotBase64, input.operationDesc)
                if (fallback != null) listOf(fallback) else emptyList()
            }

            // ═══ STEP 2: 三维融合决策 ═══
            val decision = fusionDecider.decide(fuzzyResult, fineResults, coarseResults)
            if (!decision.executed) {
                val elapsed = System.currentTimeMillis() - startTime
                return@withContext HandOutput(false, "tap", 0, 0, 0, 0, 0f, "fusion_failed", elapsed, decision.reason)
            }

            // ═══ STEP 3: 执行 + 验证 + 重试 ═══
            val normPoint = Point(decision.normalizedX, decision.normalizedY)
            val physPoint = calibration.normalizedToPhysical(normPoint.x, normPoint.y)

            var success = executor(physPoint)
            var retries = 0
            var retryPoint = physPoint

            while (!success && retries < config.maxRetries) {
                // 小范围偏移重试
                val offset = config.retryOffsetPx * (retries + 1)
                val offsets = listOf(
                    Point(retryPoint.x + offset, retryPoint.y),
                    Point(retryPoint.x - offset, retryPoint.y),
                    Point(retryPoint.x, retryPoint.y + offset),
                    Point(retryPoint.x, retryPoint.y - offset),
                )
                for (off in offsets) {
                    success = executor(off)
                    if (success) { retryPoint = off; break }
                }
                retries++
            }

            // ═══ STEP 4: 记录 + 反馈 ═══
            val elapsed = System.currentTimeMillis() - startTime
            val recordX = calibration.physicalToNormalized(retryPoint.x, retryPoint.y)
            memory.record(OperationMemory.OpRecord(
                sessionId = "hand_$startTime", deviceId = calibration.screenSize.toString(),
                screenSignature = screenSig, operationDesc = input.operationDesc,
                methodUsed = decision.method, x = recordX.x, y = recordX.y,
                success = success, confidence = decision.confidence,
            ))

            // 成功 → 学习关键词
            if (success) {
                fuzzyClicker.learnKeyword(input.operationDesc)
                // 自动微调偏移
                if (config.autoCalibrate) {
                    calibration.recordDeviation(normPoint.x, normPoint.y, retryPoint.x, retryPoint.y)
                }
            }

            HandOutput(
                success = success,
                operationType = "tap",
                normalizedX = recordX.x, normalizedY = recordX.y,
                physicalX = retryPoint.x, physicalY = retryPoint.y,
                confidence = decision.confidence,
                methodUsed = "${decision.method}_r${retries}",
                duration = elapsed,
                errorReason = if (!success) "重试${retries}次后仍失败" else "",
            )
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            HandOutput(false, "tap", 0, 0, 0, 0, 0f, "exception", elapsed, e.message ?: "未知错误")
        }
    }

    // ── 工具方法 ──

    /** 切换模式 */
    fun setMode(newMode: HandMode) {
        val newConfig = when (newMode) {
            HandMode.SPEED -> HandConfig.speed()
            HandMode.BALANCE -> HandConfig.balance()
            HandMode.PRECISE -> HandConfig.precise()
        }
        config.coarseGridCols = newConfig.coarseGridCols
        config.coarseGridRows = newConfig.coarseGridRows
        config.fineRounds = newConfig.fineRounds
        config.fuzzyThreshold = newConfig.fuzzyThreshold
        config.minConfidence = newConfig.minConfidence
        config.fuzzyEnabled = newConfig.fuzzyEnabled
        config.autoCalibrate = newConfig.autoCalibrate
        config.maxRetries = newConfig.maxRetries
    }

    /** 获取统计信息 */
    fun getStats(): Map<String, Any> = mapOf(
        "success_rate" to memory.getSuccessRate(),
        "method_stats" to memory.getMethodStats(),
        "screen_calibrated" to calibration.isCalibrated(),
        "screen_size" to "${calibration.screenSize.x}x${calibration.screenSize.y}",
        "keywords_count" to fuzzyClicker.getKeywordLibrary().values.sumOf { it.size },
    )
}
