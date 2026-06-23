package com.mbclaw.nonroot.agent

import android.content.Context
import com.mbclaw.nonroot.service.MBclawAccessibilityService

/**
 * TouchInjector — 非Root版: 无障碍通道
 *
 * 无Root环境下只走 AccessibilityService + Shizuku(可选)
 */
object TouchInjector {

    fun tap(ctx: Context, x: Int, y: Int): Boolean {
        val svc = MBclawAccessibilityService.instance
        return svc?.clickAt(x.toFloat(), y.toFloat()) == true
    }

    fun longPress(ctx: Context, x: Int, y: Int, durationMs: Long = 800): Boolean {
        val svc = MBclawAccessibilityService.instance
        return svc?.longClickAt(x.toFloat(), y.toFloat(), durationMs) == true
    }

    fun swipe(ctx: Context, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300): Boolean {
        val svc = MBclawAccessibilityService.instance
        return svc?.swipe(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), durationMs) == true
    }

    fun inputText(ctx: Context, text: String): Boolean {
        val svc = MBclawAccessibilityService.instance
        return svc?.inputText(text) == true
    }

    fun keyEvent(ctx: Context, keyCode: Int): Boolean {
        val svc = MBclawAccessibilityService.instance
        val globalAction = when (keyCode) {
            4 -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            3 -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
            187 -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
            else -> -1
        }
        return if (globalAction >= 0) svc?.performGlobalAction(globalAction) == true else false
    }

    fun selfTest(ctx: Context): String {
        val svc = MBclawAccessibilityService.instance
        return buildString {
            appendLine("非Root版触摸注入自检:")
            appendLine("  无障碍服务: ${if (svc != null) "✅" else "❌ 未开启"}")
            if (svc != null) {
                appendLine("  点击: ✅ 可用(无障碍手势)")
                appendLine("  滑动: ✅ 可用")
                appendLine("  输入: ✅ 可用")
            } else {
                appendLine("  ⚠️ 请在 设置→无障碍→MBclaw 中开启")
            }
        }
    }
}
