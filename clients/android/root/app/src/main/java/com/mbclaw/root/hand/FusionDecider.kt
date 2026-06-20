package com.mbclaw.root.hand

import android.graphics.Point
import kotlin.math.abs

/**
 * 三维融合决策 — 三通道结果 → 最优坐标
 *
 * 优先级: 模糊点击 > 精定位 > 粗筛
 * 冲突检测: 坐标差距>屏幕10% → 二选一验证
 */
class FusionDecider(
    private val config: HandConfig,
    private val screenWidth: Int,
    private val screenHeight: Int,
) {

    data class ChannelResult(
        val normalizedX: Int, val normalizedY: Int,
        val confidence: Float, val method: String,
        // method: "fuzzy" | "fine" | "coarse" | "fallback_fullscreen"
    )

    data class FinalDecision(
        val normalizedX: Int, val normalizedY: Int,
        val confidence: Float, val method: String,
        val executed: Boolean,
        val reason: String = "",
    )

    /** 融合三通道结果 → 最终决策 */
    fun decide(
        fuzzy: FuzzyClicker.FuzzyResult?,
        fineResults: List<BlockRecognizer.FineResult>,
        coarseResults: List<BlockRecognizer.CoarseResult>,
    ): FinalDecision {
        val channels = mutableListOf<ChannelResult>()

        // 通道1: 模糊点击 (最高权重)
        if (config.fuzzyEnabled && fuzzy != null && fuzzy.confidence >= config.fuzzyThreshold) {
            channels.add(ChannelResult(
                fuzzy.bounds.centerX(), fuzzy.bounds.centerY(),
                fuzzy.confidence, "fuzzy"
            ))
        }

        // 通道2: 精定位结果
        fineResults.forEach {
            channels.add(ChannelResult(it.normalizedX, it.normalizedY, it.confidence, "fine"))
        }

        // 通道3: 粗筛结果 (降级)
        coarseResults.forEach {
            val cols = config.coarseGridCols; val rows = config.coarseGridRows
            val cx = (it.gridCol * 1000 / cols) + (500 / cols)
            val cy = (it.gridRow * 1000 / rows) + (500 / rows)
            channels.add(ChannelResult(cx, cy, it.confidence * 0.6f, "coarse"))
        }

        if (channels.isEmpty()) {
            return FinalDecision(0, 0, 0f, "none", false, "无可用通道")
        }

        // 优先级排序: fuzzy > fine > coarse
        val priority = mapOf("fuzzy" to 3, "fine" to 2, "coarse" to 1, "fallback_fullscreen" to 2)
        val sorted = channels.sortedByDescending { priority[it.method] ?: 0 }

        // 模糊点击直接命中高置信度 → 直接执行
        val top = sorted.first()
        if (top.method == "fuzzy" && top.confidence >= config.fuzzyThreshold) {
            return FinalDecision(top.normalizedX, top.normalizedY, top.confidence, top.method, true, "模糊点击直接命中")
        }

        // 多通道加权平均
        if (sorted.size >= 2) {
            // 检查冲突: 两通道坐标差距 > 屏幕宽度的10%
            val conflict = detectConflict(sorted)
            if (conflict) {
                return FinalDecision(
                    top.normalizedX, top.normalizedY, top.confidence, top.method,
                    false, "多通道冲突，需要二选一验证"
                )
            }

            // 加权融合
            val weighted = weightedAverage(sorted)
            if (weighted.confidence >= config.minConfidence) {
                return FinalDecision(weighted.normalizedX, weighted.normalizedY, weighted.confidence, "fusion", true, "加权融合")
            }
        }

        // 单通道判断
        if (top.confidence >= config.minConfidence) {
            return FinalDecision(top.normalizedX, top.normalizedY, top.confidence, top.method, true, "单通道达标")
        }

        return FinalDecision(top.normalizedX, top.normalizedY, top.confidence, top.method, false, "置信度不足 (${top.confidence} < ${config.minConfidence})")
    }

    private fun detectConflict(channels: List<ChannelResult>): Boolean {
        if (channels.size < 2) return false
        val threshold = (screenWidth * 0.1).toInt() // 屏幕宽度10%
        val c1 = channels[0]; val c2 = channels[1]
        val dx = abs(c1.normalizedX - c2.normalizedX)
        val dy = abs(c1.normalizedY - c2.normalizedY)
        return dx > threshold || dy > threshold
    }

    private fun weightedAverage(channels: List<ChannelResult>): ChannelResult {
        val weights = mapOf("fuzzy" to 0.5f, "fine" to 0.35f, "coarse" to 0.15f)
        var totalWeight = 0f; var wx = 0f; var wy = 0f; var wc = 0f
        for (ch in channels) {
            val w = weights[ch.method] ?: 0.2f
            wx += ch.normalizedX * w; wy += ch.normalizedY * w
            wc += ch.confidence * w; totalWeight += w
        }
        return ChannelResult((wx / totalWeight).toInt(), (wy / totalWeight).toInt(), wc / totalWeight, "fusion")
    }
}
