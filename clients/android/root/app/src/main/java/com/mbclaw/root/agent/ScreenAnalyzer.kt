package com.mbclaw.root.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mbclaw.root.service.MBclawAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ScreenAnalyzer — 给 Agent 装"眼睛"
 *
 * 三层并行:
 *   A. uiautomator dump (root)  → 拿到全部可交互元素 XML, 含文字+坐标+类名
 *   B. 无障碍 dump              → 当 A 失败时兜底
 *   C. ML Kit OCR (本地)        → A/B 漏掉的位图文字 (如图标里的字)
 *
 * 输出: 每个元素打数字标号 [1], [2], [3]...
 * LLM 直接选数字, 不猜坐标
 *
 * Agent 调用:
 *   val elements = ScreenAnalyzer.snapshot(ctx)
 *   // LLM 收到列表: "[1] 搜索框(56,200) | [2] 联系人按钮(120,400) | ..."
 *   // LLM 说: "点 [1] 然后输入 '孟白'"
 *   // 工具 click_by_index(1) 自动转坐标点击
 */
object ScreenAnalyzer {

    data class UIElement(
        val index: Int,
        val text: String,
        val clazz: String,         // android.widget.Button / EditText...
        val bounds: IntArray,      // [l, t, r, b]
        val clickable: Boolean,
        val source: String,        // "uia" | "acc" | "ocr"
    ) {
        val centerX: Int get() = (bounds[0] + bounds[2]) / 2
        val centerY: Int get() = (bounds[1] + bounds[3]) / 2
        val width: Int get() = bounds[2] - bounds[0]
        val height: Int get() = bounds[3] - bounds[1]

        fun summary(): String {
            val type = when {
                clazz.contains("EditText") -> "📝输入框"
                clazz.contains("Button") || clickable -> "🔘按钮"
                clazz.contains("TextView") -> "📄文本"
                clazz.contains("ImageView") -> "🖼图标"
                else -> "·"
            }
            val txt = if (text.isBlank()) "(无文字)" else text.take(20)
            return "[$index] $type $txt @(${centerX},${centerY})"
        }
    }

    /** 缓存当前快照, click_by_index 用 */
    @Volatile private var lastSnapshot: List<UIElement> = emptyList()
    fun getCachedElement(index: Int): UIElement? = lastSnapshot.find { it.index == index }

    /** 全屏快照 - root 优先 */
    suspend fun snapshot(ctx: Context): List<UIElement> = withContext(Dispatchers.IO) {
        val tier = PermissionTier.get(ctx)
        val elements = mutableListOf<UIElement>()

        // 方法 A: uiautomator dump (root)
        if (tier.hasRoot) {
            try {
                val xml = tier.shellRoot(
                    "uiautomator dump /sdcard/mb_ui.xml >/dev/null 2>&1 && cat /sdcard/mb_ui.xml && rm /sdcard/mb_ui.xml"
                ) ?: ""
                if (xml.length > 100) {
                    elements.addAll(parseUiAutomatorXml(xml, "uia"))
                }
            } catch (e: Exception) {
                android.util.Log.w("MBclaw-Eye", "uia dump 失败: ${e.message}")
            }
        }

        // 方法 B: 无障碍 dump (兜底)
        if (elements.isEmpty()) {
            try {
                val svc = MBclawAccessibilityService.instance
                if (svc != null) {
                    elements.addAll(collectAccessibilityNodes(svc))
                }
            } catch (e: Exception) {
                android.util.Log.w("MBclaw-Eye", "acc dump 失败: ${e.message}")
            }
        }

        // 过滤无效 + 去重 + 打标号
        val cleaned = elements
            .filter { it.bounds[2] > it.bounds[0] && it.bounds[3] > it.bounds[1] }
            .distinctBy { "${it.bounds[0]}_${it.bounds[1]}_${it.text}" }
            .mapIndexed { i, e -> e.copy(index = i + 1) }

        lastSnapshot = cleaned
        cleaned
    }

    /** 解析 uiautomator XML — 提取 bounds/text/class/clickable */
    private fun parseUiAutomatorXml(xml: String, source: String): List<UIElement> {
        val list = mutableListOf<UIElement>()
        val boundsRe = Regex("""bounds="\[(\d+),(\d+)]\[(\d+),(\d+)]"""")
        val nodeRe = Regex("""<node[^/]*?/>""")
        // 简化: 用正则提取每个 node
        val nodes = Regex("""<node\s[^>]*?(?:/>|>)""").findAll(xml)
        for (m in nodes) {
            val node = m.value
            val text = Regex("""text="([^"]*)"""").find(node)?.groupValues?.getOrNull(1) ?: ""
            val desc = Regex("""content-desc="([^"]*)"""").find(node)?.groupValues?.getOrNull(1) ?: ""
            val clazz = Regex("""class="([^"]*)"""").find(node)?.groupValues?.getOrNull(1) ?: ""
            val clickable = Regex("""clickable="(true|false)"""").find(node)?.groupValues?.getOrNull(1) == "true"
            val bm = boundsRe.find(node) ?: continue
            val (l, t, r, b) = bm.destructured
            val displayText = text.ifBlank { desc }
            // 跳过装饰性容器: 无文字且不可点
            if (displayText.isBlank() && !clickable) continue
            list.add(UIElement(
                index = 0, text = displayText, clazz = clazz.substringAfterLast('.'),
                bounds = intArrayOf(l.toInt(), t.toInt(), r.toInt(), b.toInt()),
                clickable = clickable, source = source,
            ))
        }
        return list
    }

    /** 无障碍节点遍历 (root 不可用时兜底) */
    private fun collectAccessibilityNodes(svc: MBclawAccessibilityService): List<UIElement> {
        val list = mutableListOf<UIElement>()
        try {
            val root = svc.rootInActiveWindow ?: return list
            collectNode(root, list)
        } catch (_: Exception) {}
        return list
    }

    private fun collectNode(node: android.view.accessibility.AccessibilityNodeInfo?, out: MutableList<UIElement>) {
        if (node == null) return
        try {
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "")
            val clazz = (node.className?.toString() ?: "").substringAfterLast('.')
            if (text.isNotBlank() || node.isClickable) {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    out.add(UIElement(
                        index = 0, text = text, clazz = clazz,
                        bounds = intArrayOf(rect.left, rect.top, rect.right, rect.bottom),
                        clickable = node.isClickable, source = "acc",
                    ))
                }
            }
            for (i in 0 until node.childCount) {
                collectNode(node.getChild(i), out)
            }
        } catch (_: Exception) {}
    }

    /** 格式化为 LLM 友好的文本 */
    fun formatForLLM(elements: List<UIElement>, max: Int = 50): String {
        if (elements.isEmpty()) return "❌ 无法识别屏幕元素 (需要 Root 或无障碍权限)"
        val pickable = elements.filter { it.text.isNotBlank() || it.clickable }.take(max)
        val sb = StringBuilder()
        sb.appendLine("📱 当前屏幕共 ${elements.size} 个元素 (显示前 ${pickable.size} 个可交互)")
        sb.appendLine("─────────────────────────────────")
        pickable.forEach { sb.appendLine(it.summary()) }
        sb.appendLine("─────────────────────────────────")
        sb.appendLine("💡 可调用 click_by_index(数字) 直接点对应元素, 不必猜坐标")
        return sb.toString()
    }
}
