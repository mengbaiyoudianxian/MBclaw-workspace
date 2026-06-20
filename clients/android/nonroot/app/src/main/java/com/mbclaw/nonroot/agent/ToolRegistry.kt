package com.mbclaw.nonroot.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * 工具注册表 — OpenAI function calling 格式
 *
 * MBclaw 能执行的 31 个真实手机操作
 * 每个工具都有 name/description/parameters schema
 */
object ToolRegistry {

    data class ToolDef(val name: String, val description: String, val parameters: JSONObject)

    val ALL: List<ToolDef> = listOf(
        // ═══ 设备控制 ═══
        ToolDef("toggle_wifi", "打开或关闭WiFi", JSONObject("""{"type":"object","properties":{"enable":{"type":"boolean","description":"true打开,false关闭"}},"required":["enable"]}""")),
        ToolDef("toggle_bluetooth", "打开或关闭蓝牙", JSONObject("""{"type":"object","properties":{"enable":{"type":"boolean"}},"required":["enable"]}""")),
        ToolDef("toggle_flashlight", "打开或关闭手电筒", JSONObject("""{"type":"object","properties":{"enable":{"type":"boolean"}},"required":["enable"]}""")),
        ToolDef("toggle_airplane_mode", "打开或关闭飞行模式", JSONObject("""{"type":"object","properties":{"enable":{"type":"boolean"}},"required":["enable"]}""")),
        ToolDef("set_brightness", "设置屏幕亮度(0-255)", JSONObject("""{"type":"object","properties":{"level":{"type":"integer","minimum":0,"maximum":255}},"required":["level"]}""")),
        ToolDef("set_volume", "设置音量(media/ring/alarm)", JSONObject("""{"type":"object","properties":{"type":{"type":"string","enum":["media","ring","alarm"]},"level":{"type":"integer","minimum":0,"maximum":15}},"required":["type","level"]}""")),
        ToolDef("get_battery", "获取电池电量和状态", JSONObject("""{"type":"object","properties":{}}""")),

        // ═══ 通信 ═══
        ToolDef("send_sms", "发送短信", JSONObject("""{"type":"object","properties":{"phone":{"type":"string","description":"电话号码"},"message":{"type":"string","description":"短信内容"}},"required":["phone","message"]}""")),
        ToolDef("read_sms", "读取最近短信", JSONObject("""{"type":"object","properties":{"limit":{"type":"integer","default":10}},"required":[]}""")),
        ToolDef("make_call", "拨打电话", JSONObject("""{"type":"object","properties":{"phone":{"type":"string"}},"required":["phone"]}""")),

        // ═══ 屏幕操作 ═══
        ToolDef("take_screenshot", "截取当前屏幕并返回base64", JSONObject("""{"type":"object","properties":{}}""")),
        ToolDef("screen_record", "开始录屏(秒)", JSONObject("""{"type":"object","properties":{"duration":{"type":"integer","default":10}},"required":[]}""")),
        ToolDef("click_at", "在屏幕坐标(x,y)点击 (归一化0-1000)", JSONObject("""{"type":"object","properties":{"x":{"type":"integer"},"y":{"type":"integer"},"description":{"type":"string","description":"操作描述用于记忆"}},"required":["x","y"]}""")),
        ToolDef("long_press_at", "在屏幕坐标长按", JSONObject("""{"type":"object","properties":{"x":{"type":"integer"},"y":{"type":"integer"},"duration_ms":{"type":"integer","default":800}},"required":["x","y"]}""")),
        ToolDef("swipe", "滑动屏幕", JSONObject("""{"type":"object","properties":{"x1":{"type":"integer"},"y1":{"type":"integer"},"x2":{"type":"integer"},"y2":{"type":"integer"},"duration_ms":{"type":"integer","default":300}},"required":["x1","y1","x2","y2"]}""")),
        ToolDef("input_text", "在当前焦点输入框输入文本", JSONObject("""{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}""")),
        ToolDef("press_key", "按系统按键(BACK/HOME/RECENTS/ENTER/DELETE)", JSONObject("""{"type":"object","properties":{"key":{"type":"string","enum":["BACK","HOME","RECENTS","ENTER","DELETE","VOLUME_UP","VOLUME_DOWN","POWER"]}},"required":["key"]}""")),

        // ═══ App管理 ═══
        ToolDef("open_app", "打开指定App(包名)", JSONObject("""{"type":"object","properties":{"package_name":{"type":"string"}},"required":["package_name"]}""")),
        ToolDef("list_apps", "列出已安装的App", JSONObject("""{"type":"object","properties":{"filter":{"type":"string","description":"搜索关键词"}},"required":[]}""")),
        ToolDef("uninstall_app", "卸载App(需要Shizuku)", JSONObject("""{"type":"object","properties":{"package_name":{"type":"string"}},"required":["package_name"]}""")),
        ToolDef("force_stop_app", "强制停止App", JSONObject("""{"type":"object","properties":{"package_name":{"type":"string"}},"required":["package_name"]}""")),

        // ═══ 系统信息 ═══
        ToolDef("get_system_info", "获取设备信息(型号/系统版本/屏幕/内存)", JSONObject("""{"type":"object","properties":{}}""")),
        ToolDef("get_clipboard", "读取剪贴板内容", JSONObject("""{"type":"object","properties":{}}""")),
        ToolDef("set_clipboard", "设置剪贴板内容", JSONObject("""{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}""")),
        ToolDef("get_notifications", "获取当前通知栏所有通知", JSONObject("""{"type":"object","properties":{"limit":{"type":"integer","default":10}},"required":[]}""")),

        // ═══ MBclaw内部 ═══
        ToolDef("search_memory", "搜索MBclaw本地记忆库", JSONObject("""{"type":"object","properties":{"query":{"type":"string"},"limit":{"type":"integer","default":5}},"required":["query"]}""")),
        ToolDef("dream_memory", "触发MBclaw梦想整合(总结近期对话)", JSONObject("""{"type":"object","properties":{"session_id":{"type":"string"}},"required":[]}""")),
        ToolDef("classify_conversation", "对当前对话进行语义分类", JSONObject("""{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}""")),
        ToolDef("dual_key_review", "用双Key评审一段内容", JSONObject("""{"type":"object","properties":{"content":{"type":"string"}},"required":["content"]}""")),
        ToolDef("collision_think", "思维碰撞产生创新点子", JSONObject("""{"type":"object","properties":{"keywords":{"type":"array","items":{"type":"string"}}},"required":["keywords"]}""")),
        ToolDef("trigger_voice_assistant", "唤起手机自带语音助手(小爱/Google/Bixby)执行命令", JSONObject("""{"type":"object","properties":{"command":{"type":"string","description":"要执行的语音命令"}},"required":[]}""")),
        ToolDef("get_capability", "获取当前MBclaw能力级别(40%/100%)", JSONObject("""{"type":"object","properties":{}}""")),
        ToolDef("read_file", "读取本地文件内容", JSONObject("""{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}""")),
    )

    /** 生成OpenAI function calling格式的tools数组 */
    fun toOpenAITools(): JSONArray {
        val arr = JSONArray()
        for (tool in ALL) {
            arr.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", tool.parameters)
                })
            })
        }
        return arr
    }

    fun find(name: String): ToolDef? = ALL.find { it.name == name }
}
