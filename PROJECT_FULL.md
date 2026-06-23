# MBclaw 项目完全档案

> 写给纯思考型 AI 助手：本文档包含项目所有细节，无需访问外部资源。  
> 最后更新: 2026-06-23 凌晨

---

# 第一部分：项目概述

## 1.1 这是什么

MBclaw 是一个 Android AI Agent 应用。它让大语言模型能操控手机：点击屏幕、输入文字、开关WiFi、卸载应用、读写文件等。

由一个18岁开发者**孟白**独立开发，非任何开源项目的Fork。

## 1.2 技术栈

- **Android 客户端**: Kotlin + Jetpack Compose + Coroutines + OkHttp + Gson
- **后端**: Python 3.10+ + FastAPI + SQLite + SQLAlchemy
- **服务器**: 两台阿里云 VPS
- **AI模型**: OpenAI兼容API（豆包/智谱/DeepSeek/任何兼容接口）

## 1.3 双版本策略

| | Root版 | Lite版(非Root) |
|---|---|---|
| 包名 | `com.mbclaw.root` | `com.mbclaw.nonroot` |
| APK大小 | 75MB | 20MB |
| Root需求 | 必须 | 不需要 |
| 图标 | 橙色M | 蓝色聊天气泡 |
| 触摸执行 | Root input tap→sendevent→无障碍 | 无障碍手势 |
| 截图 | Root screencap | 不支持 |
| 权限授权 | 自动pm grant | 手动 |

两个版本可同时安装在同一台手机上。

## 1.4 服务器

| 用途 | 地址 | 系统 | 运行服务 |
|------|------|------|---------|
| 后端API | `http://47.83.2.188:80` | Ubuntu | uvicorn (FastAPI, port 8000, nginx proxy) |
| 下载站 | `http://121.199.57.195:80` | Ubuntu | nginx (静态文件) |

后端服务路径: `/opt/mbclaw/`  
数据目录: `/var/lib/mbclaw/`  
下载站根目录: `/var/www/mbclaw/`

---

# 第二部分：完整文件清单与说明

## 2.1 Android 客户端文件 (75个Kotlin文件)

### agent/ 目录 — AI Agent 核心 (20个文件)

**`AgentLoop.kt`** (262行)
- 核心执行循环: LLM决策 → 工具调用 → 观察结果 → 继续
- 接收用户消息, 构建上下文(身份+人格+权限+工具+记忆)
- 最多20轮循环, 每轮调用LLM获取tool_call或文本回复
- 用OkHttp直接调 OpenAI兼容API (不经过后端)
- 携带 X-Utopia, X-User-Id, X-Client-Version header
- 已知Bug: 消息保存了两次(ChatViewModel存一次, AgentLoop又存一次)

**`ToolExecutor.kt`** (900+行)
- 执行84个工具的核心实现
- 包含: WiFi/蓝牙/飞行模式/亮度/音量/短信/通话/截图/录屏/文件操作/日历/联系人/应用管理/浏览器/Web搜索/天气/本地沙箱/视觉识别
- 每个工具先用TouchInjector尝试, 失败再走CapabilityRouter
- 视觉工具: see_screen, click_by_index, input_by_index, find_by_text, wait_screen, vision_locate

**`ToolRegistry.kt`** (180行)
- 84个工具的OpenAI function calling格式定义
- 每个工具: name, description, parameters (JSON Schema)
- toOpenAITools() 生成完整tools数组供LLM使用

**`CapabilityRouter.kt`** (96行)
- 三层执行调度: ROOT → ADB(Shizuku) → 无障碍(UI)
- 严格优先级: 有root就用root, 不降级
- ExecResult(success, layer, output) 让LLM看到走的是哪层

**`TouchInjector.kt`** (240行)
- 三通道触摸注入: root input tap → sendevent直写/dev/input → 无障碍手势
- tap(), longPress(), swipe(), inputText(), keyEvent()
- selfTest() 连通性自检
- sendevent注入: 直接写入Linux input子系统事件 (绕过SELinux)

**`VisionLocator.kt`** (350行)
- 截图→VLM看图→坐标输出 全链路
- screencap截屏→base64→OpenAI格式image_url→发给视觉模型
- 解析VLM返回值: `<think>...</think><answer>do(action="Tap", element=[500,100])</answer>`
- 归一化坐标(0-999)转物理像素
- probe() 连通性检测
- 参考Open-AutoGLM架构

**`ScreenAnalyzer.kt`** (170行)
- uiautomator dump (root) + 无障碍 双源获取屏幕元素
- XML解析: 按<node分割逐段提取属性
- 每个元素: index, text, clazz, bounds, clickable
- formatForLLM() 生成LLM友好的文本列表

**`MBclawEnforcer.kt`** (162行)
- 代码级行为约束, 不靠prompt祈祷
- PRE: 强制注入记忆+工具+身份
- POST: 验证响应质量 (禁止通用AI废话, 身份正确性检查)
- correctResponse() 修正: "ChatGPT"→"MBclaw"

**`PermissionTier.kt`** (140行)
- 权限分层: 系统API > Root > ADB(Shizuku) > 无障碍
- probeRoot(): 多路径su探测 (6个已知路径)
- shellRoot(): 先sh -c, 失败再su -c多路径兜底
- shellAdb(): Shizuku执行

**`RootBootstrap.kt`** (250行)
- 应用首次启动时自动: 授予所有危险权限 + 电池白名单 + 无障碍绑定 + 厂商自启动
- 60+个权限分3批pm grant, 每批10个
- MIN_GRANTED=30, 不达标标记未完成, 下次启动重试
- 等root就绪最多5次(15秒)
- 已知Bug: 云手机自动授权导致检测不准

**`DebugRemote.kt`** (175行)
- 反向调试通道: 设备上报心跳 + 轮询指令 + 执行 + 回传结果
- 每5秒循环: POST心跳 → GET指令 → 执行 → POST结果
- 支持指令: shell, perm_status, screen_dump, logcat, click, swipe, text, key, screenshot
- postJson() 已知Bug: 可能静默失败

**`MBclawAgent.kt`** (80行)
- MBclaw主代理类: 持有settings, db, systemPrompt
- chat() 简单同步LLM调用
- localMatch() 关键字本地匹配 (离线快速响应)

**`MiclawBridge.kt`** (92行)
- MiClaw白嫖算力桥接
- apply(): 向服务器申请代理实例
- status(): 轮询登录状态
- applyToSettings(): 配置成功后写入UserSettings

**`AntiTamper.kt`**
- 反作弊: kill flag检测 + 服务器校验 + 自卸载
- deviceFingerprint(): 设备唯一标识

**`SafeOps.kt`**
- 安全操作: 备份应用/文件 (3份循环), 风险等级判断

**`PermissionPolicy.kt`**
- 用户权限偏好: ALLOW/DENY_FOREVER/ASK_EACH_TIME

**`PermissionLabels.kt`**
- 权限中文名和描述

**`ModelCapability.kt`**
- 模型能力检测

**`ServerToolBridge.kt`**
- 服务端工具桥接 (从后端拉工具列表)

**`CustomToolStore.kt`**
- 自定义工具存储

### hand/ 目录 — 智能体之手 (7个文件)

**`AgentHand.kt`** (200行)
- 智能手主编排器
- STEP0: DNA记忆快速命中 → STEP1: 关键词模糊匹配 → STEP2: VLM视觉定位
- 失败时微调重试(小范围偏移)
- 成功记录到OperationMemory, 自动学习关键词

**`BlockRecognizer.kt`** (120行)
- VLM视觉定位通道选择器
- fullScreenLocate(): VLM看图→坐标 (核心)
- 无VLM时降级到关键词+历史记忆

**`FuzzyClicker.kt`** (85行)
- 快速模糊关键词匹配
- 内置12类100+常用操作词
- positionKnown标志: 关键词命中≠有坐标

**`FusionDecider.kt`** (120行)
- 三通道决策融合: VLM > Memory > Fuzzy

**`ScreenCalibration.kt`** (200行)
- 归一化坐标(0-1000) ↔ 物理像素映射
- 自动微调偏移 (积累10条后自动修正)
- 横竖屏自适应

**`OperationMemory.kt`**
- 操作记忆: 记录每次点击, 查找相似操作

**`HandConfig.kt`**
- 智能手配置: 速度/均衡/精确 三档

### service/ 目录 — 系统服务 (6个文件)

**`AgentFloatingService.kt`** (360行)
- AI运行时悬浮窗 + 常驻通知
- 方形停止键 ⏹, 走马灯滚动显示
- 拖动移动窗口位置
- 非root时Toast提示手动开启悬浮窗权限

**`MBclawAccessibilityService.kt`**
- 无障碍服务: clickAt(), longClickAt(), swipe(), inputText()
- performGlobalAction() 系统按键
- rootInActiveWindow 获取UI树

**`NotificationMonitor.kt`**
- 通知监听服务

**`ShizukuManager.kt`**
- Shizuku ADB通道管理

**`XiaomiApi.kt`**
- 小米系统API封装

**`MBclawServerClient.kt`**
- 服务端API客户端

### data/ 目录 — 数据层 (8个文件)

**`LocalStore.kt`** (200行)
- UserSettings: 所有配置项 (provider, apiKey, model, vision**, voice**, utopia等)
- LocalDB: SQLite数据库, 会话+消息 CRUD

**`AssistantCatalog.kt`** (100行)
- 21个AI助手: default/ponytail/xiaomi/nsfw/schedule/comm/office/media/study/home/nutrition/fitness/emotion/code/translate/game/travel/finance/pet/car
- 每个助手: id, name, emoji, systemPrompt, temperature

**`VisionPresets.kt`** (51行)
- 视觉模型锁定: 豆包(火山引擎) / 智谱 AutoGLM-Phone
- Root模式可二选一, 非Root锁死智谱

**`Endpoints.kt`** (102行)
- 服务器地址OBF混淆 (Base64+XOR)
- warmUp(): 从GitHub注册中心拉最新地址
- backend() / download() 返回实际URL

**`AccountManager.kt`** (111行)
- 账号管理: QQ/微信 ID, 昵称, 头像缓存
- syncToServer() / fetchFromServer()

**`QQAutoLogin.kt`** (199行)
- 5重QQ号提取策略: shared_prefs → databases → process maps → dumpsys
- 3次重试 (3s/30s/5min)
- isValidUin(): 6-11位过滤, 排除时间戳

**`SecureVault.kt`**
- 隐私保险箱

**`Endpoints.kt`**
- 服务器地址注册中心

### ui/ 目录 — Compose界面

**`MBclawMainScreen.kt`** (700+行)
- 主屏幕: 聊天页 + 抽屉 + 设置路由
- 首次安装检测: 读 SharedPreferences `first_root_check`
- 无root弹窗: 重新检测/下载Lite/倔驴进入
- 无root提示条: "没有女朋友的心" 3秒消失 (非首次)
- 更新弹窗: 启动检测→弹窗 (立即更新/稍后/忽略)
- 热更新进度显示
- 返回键优先级: 弹窗→抽屉→路由pop→再次退出→回桌面

**`screens/ChatScreen.kt`** (260行)
- 仿MiClaw聊天界面
- 用户气泡(浅蓝) + AI气泡(浅灰) + 复制/分享
- +号文件上传: OpenDocument启动器 (图片/APK/压缩包/文档/视频/音频)
- 输入框: imePadding, 发送/麦克风/停止按钮

**`screens/ChatViewModel.kt`** (260行)
- 进程级单例, 强制从DB重载(onResume)
- send(): 立即存盘+启动悬浮窗+AgentLoop运行
- 助手切换: SharedPreferences记录

**`screens/SettingsPage.kt`** (800+行)
- 完整设置页: 账号/权限状态/外观/模型配置/视觉语音/工具市场/Linux环境/乌托邦/隐私/调试/版本
- 调试入口: DebugRemoteSheet, 显示连接码, 点击复制
- Linux环境: LinuxDownloadSheet (下载+解压+装包+进度)
- 权限一键授权入口 (弹出"早知如此何必当初呢")

**`screens/SettingsSheets.kt`** (450行)
- 账号Sheet: QQ/微信/昵称 + 头像预览 + 云端恢复
- 权限详情对话框: 逐项列表 + 点开操作 (永久禁止/打开/每次询问)
- 权限一键授权按钮: "早知如此何必当初呢" → 检测root → 有root进授权页 / 无root弹"没有root权限还想拥有我"
- MiClaw白嫖Sheet: 申请+轮询+自动配置

**`screens/PermissionGrantScreen.kt`** (200行)
- 全屏权限授予页
- 显示品牌+Android版本
- 逐项pm grant + 真实验证 + 画✅
- 跳过提示: "95%功能失效"

**`screens/VisionVoiceSheet.kt`** (180行)
- 视觉模型配置: Root模式二选一, 非Root锁死
- 语音TTS/ASR模型配置
- 键盘遮挡修复: navigationBarsPadding + imePadding

**`screens/ToolsScreen.kt`**
- 工具列表展示

**`screens/HistoryScreen.kt`**
- 聊天历史

**`AgentHandScreen.kt`** (220行)
- 智能手面板: 状态/自检/模式选择/关键词库/标定
- 双通道自检按钮

**`ui/theme/Theme.kt` + `Color.kt`**
- 浅色/深色/跟随系统主题

### 其他文件

**`MainActivity.kt`** (63行)
- Compose入口, onResume调用forceReload()

**`MBclawRootApp.kt`** (100行)
- Application初始化
- 顺序: loadPatch(热更新) → Endpoints.warmUp → createNotificationChannels → RootBootstrap.setupAsync → 反作弊检查 → QQAutoLogin → TouchInjector预热 → DebugRemote自动开启 → HotfixLoader下载

**`BuildConfig`**
- 版本号: versionCode=17, versionName="4.7-root"

**`proguard-rules.pro`** (94行)
- R8混淆规则: Keep Kotlin/Compose/Gson/OkHttp/MBclaw所有类/Android组件/JNI方法

---

## 2.2 后端 Python 文件 (11个)

### app/ 目录

**`main.py`** (70行)
- FastAPI入口: lifespan→init_db, CORS, 用户追踪中间件
- 挂载路由: api_router, admin_router(sessions/messages/search/providers/tools), admin_extra, admin_upload, version, bridge, debug, admin_api
- 静态HTML: 管理面板登录页
- /health 端点

**`api.py`** (400行)
- REST API: POST /sessions, POST /sessions/{sid}/messages, POST /sessions/{sid}/close, GET /sessions/{sid}/messages, GET /search, POST /agent/run, GET /agent/status
- 还包含了: GET/POST /admin/client/version, /admin/client/version/set
- JSONL transcript 写入

**`db.py`**
- SQLAlchemy数据库初始化

**`models.py`**
- ORM模型: Session, Message

**`llm.py`**
- LLM客户端 (OpenAI兼容)

**`memory.py`**
- MemoryRepo: 记忆查询

**`pipeline.py`**
- close_session: 摘要+关键词+持久化

**`agent.py`**
- Agent运行逻辑

**`providers.py`**
- LLM提供商列表

**`tools.py`**
- 工具执行

### app/admin/ 目录

**`router.py`**
- 管理面板核心路由 + 用户调用追踪 + 请求统计

**`extra.py`**
- 额外管理功能

**`upload.py`**
- 文件上传

**`version_api.py`** (70行)
- /admin/client/version: 版本检测 (tuple比较版本号)
- /admin/client/version/set: 管理面板设置版本
- 数据来源: /var/lib/mbclaw/version.json

**`debug_api.py`** (93行)
- POST /admin/client/debug/heartbeat → 接收心跳
- GET /admin/client/debug/cmd → 客户端轮询指令
- POST /admin/client/debug/result → 回传结果
- POST /admin/client/debug/send → 开发者发送指令
- GET /admin/client/debug/devices → 在线设备列表
- GET /admin/client/debug/results → 查看执行结果

**`bridge_manager.py`** (260行)
- POST /bridge/miclaw/apply → 申请代理实例
- GET /bridge/miclaw/login/{id} → 登录页 (代理桥进程HTML)
- POST /bridge/miclaw/login/{id} → 登录POST代理 (v4.8修复)
- GET /bridge/miclaw/status → 检查登录状态
- POST 桥进程 login → 验证用户凭证
- 数据: /var/lib/mbclaw/miclaw_instances.json, miclaw_applications.json
- 桥进程管理: 自动分配端口(8800-8900), spawn Python stub

**`admin_api.py`** (新, 150行)
- GET /api/admin/stats → 仪表盘统计 (users/instances/downloads)
- GET /api/admin/users → 用户列表 (含IP/root状态/key信息/黑名单)
- GET /api/admin/metrics → 服务器指标 (磁盘/内存/网络/DB)
- GET /api/admin/downloads → 下载统计
- POST /api/admin/downloads/track → 记录下载

**`static_index.py`**
- 管理面板HTML (嵌入版本, 当前是简单版)

---

## 2.3 服务器数据文件

**`/var/lib/mbclaw/version.json`**
```json
{
  "latest": "4.7-root",
  "min_supported": "4.5",
  "download_url": "http://121.199.57.195/mbclaw-root-latest.apk",
  "changelog": "v4.7: root多路探测(云手机)...",
  "force_update": false
}
```

**`/var/lib/mbclaw/miclaw_instances.json`** — 桥接代理实例
```json
{
  "app_id_xxx": {
    "application_id": "xxx",
    "user_id": "1973054239",
    "device_id": "f05ed420c1f4...",
    "ip": "223.160.159.119",
    "port": 8801,
    "pid": 61820,
    "created_at": 1782209756,
    "logged_in": false,
    "user_token": "xxx",
    "utopia_enabled": true,
    "self_used_tokens": 0,
    "shared_out_tokens": 0
  }
}
```

**`/var/lib/mbclaw/miclaw_blacklist.json`** — 黑名单
```json
{"ips": ["1.2.3.4"], "devices": ["device_id_xxx"], "log": [{"ip":"...","reason":"...","at":123}]}
```

**`/var/lib/mbclaw/stats/downloads.json`** — 下载追踪
```json
{
  "mbclaw-root-v4.7.apk": {"total": 2, "today": 2, "last_download": "2026-06-23T..."},
  "mbclaw-lite-latest.apk": {"total": 1, "today": 1, "last_download": null}
}
```

---

## 2.4 下载服务器文件

**`/var/www/mbclaw/`**
```
index.html                      ← 下载中心页面
mbclaw-root-latest.apk → v4.7   ← Root版最新 (符号链接)
mbclaw-root-v4.7.apk            ← Root版 v4.7 (78MB)
mbclaw-root-v4.5.apk            ← Root版 v4.5 (旧版)
mbclaw-lite-latest.apk          ← Lite版最新 (20MB)
mbclaw-linux-rootfs.tar.gz      ← Alpine ARM64 minirootfs (3.8MB)
ADMIN_PANEL_SPEC.md             ← 管理面板规格书
PROJECT_STATUS.md               ← 项目状态文档
```

---

# 第三部分：完整API文档

## 3.1 客户端调用的API

### 版本检测
```
GET /admin/client/version?current=4.6-root
Response: {
  "latest": "4.7-root",
  "current": "4.6-root",
  "has_update": true,
  "download_url": "http://121.199.57.195/mbclaw-root-latest.apk",
  "changelog": "v4.7: root多路探测...",
  "force_update": false,
  "min_supported": "4.5"
}
```

### 权限模板
```
GET /admin/client/perm-template?brand=xiaomi&model=2509FPN0BC&sdk=36
Response: {
  "brand": "Xiaomi",
  "os": "HyperOS",
  "sdk": 35,
  "su_method": "su -c",
  "required_perms": ["CAMERA", "RECORD_AUDIO", ...],
  "special_handling": {
    "SYSTEM_ALERT_WINDOW": "appops set ...",
    "ACCESSIBILITY": "settings put ..."
  }
}
```

### 调试
```
POST /admin/client/debug/heartbeat
Body: {"code":"zuozhe2580","device_id":"xxx","user_id":"xxx","version":"4.7-root","model":"SM-F9000","brand":"samsung","sdk":36,"permissions":{"root":true,...},"ts":123}
Response: {"has_command": false}

GET /admin/client/debug/cmd?code=zuozhe2580
Response: {"cmd":"shell","args":"pm grant ...","id":"cmd_123"}
(命令被pop, 下次轮询返回{})

POST /admin/client/debug/result
Body: {"code":"zuozhe2580","cmd_id":"cmd_123","output":"GRANT_OK"}
Response: {"ok": true}

POST /admin/client/debug/send?code=zuozhe2580&cmd=shell&args=id
Response: {"ok": true, "cmd_id": "abc123", "code": "zuozhe2580"}

GET /admin/client/debug/devices
Response: [{"code":"zuozhe2580","device_id":"xxx","model":"...","permissions":{...},"last_seen":"..."}]

GET /admin/client/debug/results?limit=20
Response: [{"cmd_id":"abc123","code":"zuozhe2580","output":"uid=0(root)..."}]
```

### MiClaw桥接
```
POST /bridge/miclaw/apply
Body: {"user_id":"1973054239","device_id":"xxx"}
Response: {
  "approved": true,
  "application_id": "zFU_RVTQcGgMHRrTxHAbuw",
  "login_url": "http://47.83.2.188/bridge/miclaw/login/zFU_RV...",
  "status": "审核中..."
}

GET /bridge/miclaw/login/{application_id}
Response: HTML登录页面 (表单post到自身URL, v4.8修复)

POST /bridge/miclaw/login/{application_id}
Body: {"u":"username","p":"password"}
Response: {"ok": true}

GET /bridge/miclaw/status?application_id=xxx
Response: {"ready": true, "user_token": "...", "model": "miclaw-default", "is_stub": true}
```

### 热更新
```
GET /hotfix/latest.json
Response: {"version": 27, "patch_url": "http://47.83.2.188/hotfix/patch_v27.zip", "desc": "..."}
```

## 3.2 管理面板API

### 仪表盘
```
GET /api/admin/stats
Response: {
  "unique_users": 7,
  "active_instances": 5,
  "total_instances": 8,
  "blacklisted": 0,
  "total_downloads": 3,
  "today_downloads": 3,
  "downloads_breakdown": {
    "mbclaw-root-v4.7.apk": {"total": 2, "today": 2, "last_download": "2026-06-23T..."},
    "mbclaw-lite-latest.apk": {"total": 1, "today": 1, "last_download": null}
  }
}
```

### 用户列表
```
GET /api/admin/users?page=1&limit=20&search=
Response: {
  "users": [{
    "user_id": "1973054239",
    "device_id": "f05ed420c1f4ebed8e462350eb2e7bd3",
    "ip": "223.160.159.119",
    "model": "2509FPN0BC",
    "brand": "Xiaomi",
    "version": "4.7-root",
    "root": true,
    "accessibility": true,
    "permissions_granted": 16,
    "permissions_total": 62,
    "can_overlay": true,
    "logged_in": false,
    "utopia": true,
    "last_seen": "2026-06-23T04:09:40...",
    "created_at": "2026-06-23T...",
    "is_blacklisted": false,
    "fail_count": 0,
    "key_info": {
      "has_token": true,
      "token_preview": "Jgx5wr3r...",
      "model": "miclaw-default"
    },
    "instance_id": "zFU_RVTQ...",
    "port": 8801,
    "pid": 61820
  }],
  "total": 8,
  "page": 1
}
```

### 服务器指标
```
GET /api/admin/metrics
Response: {
  "disk_total": 42949672960,
  "disk_free": 35648299008,
  "disk_used": 7301373952,
  "disk_pct": 17.0,
  "mem_total": 8589934592,
  "mem_used": 1224736768,
  "mem_pct": 14.3,
  "net_rx_bytes": 1234567890,
  "net_tx_bytes": 987654321,
  "uptime_seconds": 385200,
  "db_size": 0,
  "db_size_mb": 0.0
}
```

### 下载统计
```
GET /api/admin/downloads
Response: {
  "mbclaw-root-v4.7.apk": {"total": 2, "today": 2, "last_download": "2026-06-23T..."},
  "mbclaw-lite-latest.apk": {"total": 1, "today": 1, "last_download": null},
  "mbclaw-linux-rootfs.tar.gz": {"total": 0, "today": 0, "last_download": null}
}

POST /api/admin/downloads/track?file=mbclaw-root-v4.7.apk
Response: {"ok": true, "file": "mbclaw-root-v4.7.apk", "total": 3}
```

---

# 第四部分：已知Bug详细清单

## Bug #1: root检测误判 (云手机)
- **文件**: PermissionTier.kt: probeRoot()
- **现象**: 云手机 `/acct/.mci/mciu` 静默授权, probeRoot用 `head -1 /data/system/packages.list` 作为测试, 该文件云手机上世界可读, 导致永远返回true
- **影响**: 云手机用户永远不会看到"无root"弹窗
- **修复方向**: 用真正需要root的命令测试 (如 `pm grant` 并立即验证)

## Bug #2: shellRoot死锁
- **文件**: PermissionTier.kt: shellRoot()
- **现象**: 只读inputStream, 命令输出超过缓冲区时子进程阻塞, 主线程又等子进程结束 → 死锁
- **Aether做法**: 两个独立线程并行读stdout和stderr
- **修复方向**: 用ProcessBuilder + 线程并行读

## Bug #3: 消息重复存储
- **文件**: AgentLoop.kt:143-144 + ChatViewModel.kt
- **现象**: 用户消息在ChatViewModel.send()存一次, AgentLoop.run()结尾再存一次
- **修复方向**: 只在一处保存

## Bug #4: 调试结果回传不稳定
- **文件**: DebugRemote.kt: postJson()
- **现象**: 部分POST请求静默失败, 结果不回传到服务器
- **已做**: 添加了log, 返回Boolean
- **待做**: 添加重试机制

## Bug #5: 热更新补丁过大
- **文件**: HotfixLoader.kt
- **现象**: 补丁zip包含所有13个classes.dex (60MB), 下载慢
- **修复方向**: 只打包变更的类, 或增量dex

## Bug #6: QQ号提取准确率低
- **文件**: QQAutoLogin.kt
- **现象**: 提取到10538 (5位数字, 非QQ号)
- **已做**: isValidUin改为6-11位
- **待做**: 增加上下文验证 (数字必须在QQ相关文件中出现)

## Bug #7: 权限授予成功率低
- **文件**: RootBootstrap.kt
- **现象**: 首次安装只授权2/62, 虽然RootBootstrap已分批
- **可能原因**: 云手机root工具限制, pm grant命令静默失败
- **修复方向**: 每步验证, 失败重试, 记录详细日志

## Bug #8: 输入框被键盘遮挡
- **文件**: VisionVoiceSheet.kt
- **现象**: 视觉/语音Key输入框被键盘挡住
- **已做**: 添加navigationBarsPadding + imePadding
- **待做**: 使用bringIntoViewRequester

---

# 第五部分：架构图

## 5.1 APP启动流程

```
Application.onCreate()
  ├─ loadPatch()           ← 热更新补丁加载 (DexClassLoader合并)
  ├─ Endpoints.warmUp()    ← 从GitHub注册中心拉服务器地址
  ├─ createNotificationChannels()
  ├─ RootBootstrap.setupAsync()  ← 等root就绪→分批pm grant→无障碍→电池
  ├─ AntiTamper检查         ← kill flag检测
  ├─ QQAutoLogin.scheduleAfterStart()  ← 5分钟后尝试提取QQ号
  ├─ TouchInjector.init()   ← 探测触摸设备
  ├─ DebugRemote.save()     ← 自动开启调试 (设备指纹码)
  └─ HotfixLoader.checkAndDownload()  ← 检查新补丁→下载

MainActivity.onCreate()
  └─ setContent: MBclawMainScreen
       ├─ 首次安装? → showRootDialog
       ├─ 有root+权限不足? → showPermGrant
       ├─ 无root非首次? → showNoRootHint (3秒)
       ├─ 有新版本? → showUpdateDialog
       └─ ChatPage (默认)
```

## 5.2 Agent执行流程

```
用户输入消息
  └─ ChatViewModel.send()
       ├─ 立即存盘 (DB)
       ├─ 启动悬浮窗+通知
       └─ AgentLoop.run(userMessage, sessionId, maxTurns=20)
            ├─ Enforcer.buildContext()  ← 记忆+工具+身份注入
            ├─ 构建messages: 身份+人格+权限+工具+记忆+历史+用户消息
            ├─ 循环 (最多20轮):
            │    ├─ callWithTools(messages) → LLM API
            │    ├─ LLM返回tool_call?
            │    │   ├─ 是 → ToolExecutor.execute() → 结果加入messages → 继续
            │    │   └─ 否 → 文本回复 → 结束
            │    └─ 用户手动终止? → 结束
            └─ Enforcer.validateResponse() → 修正→返回
```

## 5.3 触摸执行流程

```
Agent需要点击屏幕
  ├─ 通道1: see_screen → uiautomator dump → 元素列表 → LLM选索引 → click_by_index
  └─ 通道2: vision_locate → screencap → VLM看图 → 坐标 → TouchInjector.tap()

TouchInjector.tap()
  ├─ 方法1: root input tap (Runtime.exec("sh -c 'input tap x y'"))
  ├─ 方法2: root sendevent (直写 /dev/input/eventX)
  └─ 方法3: AccessibilityService.clickAt()
```

---

# 第六部分：数据流

## 6.1 用户数据
- SharedPreferences: 设置项 (provider, apiKey, model, vision**, voice**, utopia, 主题)
- SQLite: 会话(sessions), 消息(messages), 记忆(memories)
- 服务器: miclaw_instances.json (设备+权限信息), miclaw_applications.json

## 6.2 网络请求流
- 客户端→LLM: 直连 (不经过MBclaw服务器), OpenAI兼容格式
- 客户端→后端: 版本检测, 调试心跳, MiClaw桥接, 账号同步
- 客户端→下载站: APK下载, Linux rootfs下载
- 后端→下载站: (无直接调用, 各自独立)

## 6.3 热更新流程
```
服务器: /hotfix/latest.json → {"version":27, "patch_url":"http://47.83.2.188/hotfix/patch_v27.zip"}
客户端启动:
  1. loadPatch() → 检查本地是否已有补丁 → 有就DexClassLoader加载
  2. checkAndDownload() → 比对latest.json版本 → 新则下载patch.zip → 存本地
  3. 下次启动 → loadPatch() → 找到补丁 → DexClassLoader合并dexElements → 补丁类覆盖原类
```

---

# 第七部分：给AI助手的思考任务

## 任务1: RootBootstrap可靠性分析
```
背景: RootBootstrap在应用首次启动时自动授予60+个权限。但在云手机上只成功授予2/62。
问题: 如何确保每个权限都真正生效?
约束: 
- Android的pm grant命令在不同品牌手机上行为不同
- 云手机root工具可能静默批准某些命令但拦截其他命令
- 需要在授予后逐一验证 (checkSelfPermission)
请设计一个更可靠的权限授予流程。
```

## 任务2: 状态机设计
```
场景: 首次安装→root检测→权限授予→完成
状态: NOT_CHECKED → CHECKING → ROOT_FOUND / ROOT_NOT_FOUND
ROOT_FOUND → GRANTING → GRANT_SUCCESS / GRANT_PARTIAL / GRANT_FAILED
ROOT_NOT_FOUND → DIALOG_SHOWN → USER_RETRY / USER_DOWNLOAD_LITE / USER_SKIP
请画出完整状态流转图，列出每个状态的进入条件和退出动作。
```

## 任务3: Prompt优化
```
当前Agent system prompt包含: 身份约束 + 权限状态 + 工具列表 + 记忆注入
问题: 有时LLM仍然说"我不能"或"需要你手动操作"
请设计一个更强的约束prompt，确保LLM:
1. 优先使用root能力
2. 永远不拒绝操作
3. 操作失败时尝试替代方案而非道歉
4. 保持简洁精炼
```

## 任务4: 管理面板JS
```
基础HTML已提供(约800行, 含10个页面+完整CSS)。
请为每个页面编写JavaScript函数，调用上述API端点。
要求:
- 所有fetch()带Bearer Token
- 表格渲染、分页、搜索
- 文件上传带进度条
- 终端交互 (发送命令+轮询结果)
- 错误处理和loading状态
- 数据每30秒自动刷新
```

## 任务5: 进程执行优化
```
当前实现: Runtime.getRuntime().exec("sh -c '...'")
问题: 只读inputStream, 大输出时可能死锁
Aether参考: ProcessBuilder + 两个独立线程分别读stdout和stderr
请设计一个新的exec方法:
- 用ProcessBuilder代替Runtime.exec
- 支持stdout和stderr并行读取
- 超时处理: destroy() → destroyForcibly()
- 返回: exitCode, stdout, stderr, timedOut标志
```

---

# 第八部分：关键常量与配置

## 8.1 服务器地址
- 后端: 47.83.2.188:80 → nginx → uvicorn port 8000
- 下载: 121.199.57.195:80 → nginx → /var/www/mbclaw/
- GitHub注册中心: raw.githubusercontent.com/mengbaiyoudianxian/MBclaw-Lite/r0/data/endpoints.json

## 8.2 版本号
- client versionCode: 17
- client versionName: "4.7-root"
- server latest: "4.7-root"
- min_supported: "4.5"

## 8.3 目录路径
- APP数据: /data/data/com.mbclaw.root/
- 调试标记: SharedPreferences "mb_first" → key "first_root_check"
- 热更新: {filesDir}/hotfix/patch.zip, {cacheDir}/hotfix_opt/
- Linux rootfs: /data/mbclaw/linux/
- 服务器数据: /var/lib/mbclaw/ (version.json, miclaw_*.json, stats/, hotfix/)
- 服务器代码: /opt/mbclaw/ (app/main.py, app/admin/*.py)

## 8.4 su路径列表
```
"su"                    ← PATH中
"/system/xbin/su"       ← 云手机→/acct/.mci/mciu
"/sbin/su"              ← Magisk→./magisk
"/system/bin/su"        ← 标准
"/data/adb/magisk/su"   ← Magisk备用
"/data/adb/ksu/bin/su"  ← KernelSU
"/acct/.mci/mciu"       ← 云手机mciu root
```

## 8.5 MIUI/HyperOS特殊处理
```
pm grant --user 0 {pkg} {perm}        ← 授权
appops set --user 0 {pkg} SYSTEM_ALERT_WINDOW allow  ← 悬浮窗
cmd deviceidle whitelist +{pkg}       ← 电池白名单
settings put secure enabled_accessibility_services {pkg}/...MBclawAccessibilityService
settings put secure accessibility_enabled 1
am startservice ...MBclawAccessibilityService
```

---

# 第九部分：版本发布流程

每次发布的步骤:
1. 修改 `build.gradle.kts` 中 versionCode 和 versionName
2. `./gradlew assembleDebug` 编译APK
3. `scp` APK到下载服务器 `/var/www/mbclaw/mbclaw-root-v{version}.apk`
4. 更新符号链接 `mbclaw-root-latest.apk → 新版本`
5. 更新下载服务器 `index.html` 版本号和更新日志
6. 更新后端 `version.json`: latest版本号, changelog
7. 提取 classes*.dex, 打包成 patch_v{version}.zip
8. 上传补丁到后端 `/var/lib/mbclaw/hotfix/`
9. 更新后端 `hotfix/latest.json` 版本号和补丁URL
10. Git commit + push
11. 验证: curl 版本端点, curl 下载链接
