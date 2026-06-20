package com.mbclaw.root.hand

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.mbclaw.root.api.DirectApiClient
import com.mbclaw.root.api.ChatMessage as ApiMsg
import com.mbclaw.root.data.UserSettings
import com.mbclaw.root.model.ProviderCatalog

/**
 * 区块识别 — 两阶段: 粗筛(网格) → 精定位(裁剪放大)
 *
 * 不暴力分割，先看整体再盯局部
 */
class BlockRecognizer(
    private val settings: UserSettings,
    private val config: HandConfig,
) {

    data class CoarseResult(val gridCol: Int, val gridRow: Int, val confidence: Float)
    data class FineResult(val normalizedX: Int, val normalizedY: Int, val confidence: Float)
    data class RecognizedTarget(
        val normalizedX: Int, val normalizedY: Int,
        val confidence: Float, val method: String,
    )

    // ── 粗筛阶段 ──

    /**
     * 网格化判断: "这个格子里有没有我要找的东西？"
     * 返回置信度最高的1-2个格子
     */
    suspend fun coarseLocate(
        screenshotBase64: String,
        operationDesc: String,
    ): List<CoarseResult> {
        val cols = config.coarseGridCols
        val rows = config.coarseGridRows
        val total = cols * rows

        // 每格做轻量判断 (并行)
        val results = mutableListOf<CoarseResult>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val confidence = askGridCell(r, c, rows, cols, operationDesc, screenshotBase64, total)
                if (confidence > 0.3f) { // 过滤掉完全不相关的
                    results.add(CoarseResult(c, r, confidence))
                }
            }
        }

        // 取置信度最高的1-2个
        val sorted = results.sortedByDescending { it.confidence }
        val top = sorted.take(if (sorted.size == 1) 1 else 2)

        // 极端情况: 全部格子置信度都很低 → 返回空 (触发全屏直接定位)
        if (top.isEmpty() || top.first().confidence < 0.2f) {
            return emptyList()
        }
        return top
    }

    // ── 精定位阶段 ──

    /**
     * 裁剪候选区域，放大后精确锁定坐标
     * @param rounds 精定位轮次 (每多一轮放大再精修)
     */
    suspend fun fineLocate(
        screenshotBase64: String,
        operationDesc: String,
        candidateRegions: List<CoarseResult>,
        rounds: Int = config.fineRounds,
    ): List<FineResult> {
        if (rounds <= 0 || candidateRegions.isEmpty()) return emptyList()

        val results = mutableListOf<FineResult>()
        val cols = config.coarseGridCols; val rows = config.coarseGridRows

        for (region in candidateRegions) {
            // 裁剪候选区并放大
            val cropDesc = buildCropPrompt(region, cols, rows, screenshotBase64, operationDesc)
            var currentConfidence = region.confidence
            var currentNX = (region.gridCol * 1000 / cols) + (500 / cols)
            var currentNY = (region.gridRow * 1000 / rows) + (500 / rows)

            // 多轮精修
            for (round in 0 until rounds) {
                val fine = askPreciseCoordinate(cropDesc, operationDesc, screenshotBase64)
                if (fine != null && fine.confidence > currentConfidence) {
                    currentNX = fine.normalizedX; currentNY = fine.normalizedY
                    currentConfidence = fine.confidence
                }
            }

            if (currentConfidence > config.minConfidence) {
                results.add(FineResult(currentNX, currentNY, currentConfidence))
            }
        }
        return results.sortedByDescending { it.confidence }
    }

    // ── 降级: 全屏直接定位 ──

    /** 粗筛失败时降级: 整张图直接丢给大模型 */
    suspend fun fullScreenLocate(
        screenshotBase64: String,
        operationDesc: String,
    ): FineResult? {
        return askPreciseCoordinate("全屏定位", operationDesc, screenshotBase64)
    }

    // ── 模型调用 ──

    private suspend fun askGridCell(r: Int, c: Int, rows: Int, cols: Int, desc: String, img: String, total: Int): Float {
        // 简化为基于模型名称选择调用策略，实际可调用多模态模型
        // 这里做启发式: 关键词匹配度 = 初始置信度
        val keywords = desc.split(Regex("[\\s的到一个了]")).filter { it.length >= 2 }
        val cellDesc = "第${r+1}行第${c+1}列 (共${rows}行${cols}列, 总计${total}格)"
        // 返回基于关键词+位置的启发式分数
        // 实际应调用: 轻量多模态模型判断 "这个格子里有没有目标"
        return 0.5f + (Math.random().toFloat() * 0.3f - 0.3f) // placeholder heuristic
    }

    private suspend fun askPreciseCoordinate(
        cropContext: String,
        desc: String,
        img: String,
    ): FineResult? {
        // 实际应调用高精度多模态模型 (如 Qwen2.5-VL / UI-TARS)
        // 模型输出格式: "目标坐标: (nx, ny), 置信度: 0.92"
        // 这里做占位实现，后续对接真实视觉模型
        return FineResult(
            normalizedX = (300 + Math.random() * 400).toInt(),
            normalizedY = (300 + Math.random() * 400).toInt(),
            confidence = 0.7f + (Math.random().toFloat() * 0.25f),
        )
    }

    private fun buildCropPrompt(region: CoarseResult, cols: Int, rows: Int, img: String, desc: String): String {
        return "在屏幕第${region.gridRow+1}行第${region.gridCol+1}列(共${rows}行${cols}列)的区域中，找到[$desc]并返回精确坐标"
    }
}
