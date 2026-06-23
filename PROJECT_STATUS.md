# MBclaw 项目全景 — 已完成 / 待完成 / 畅想

> 最后更新: 2026-06-23  
> 作者: 孟白  
> GitHub: https://github.com/mengbaiyoudianxian

---

## 一、项目仓库一览

| 仓库 | 内容 | 状态 |
|------|------|------|
| **MBclaw** | 设计文档、架构蓝图 | 需更新 |
| **MBclaw-Lite** | Python FastAPI 后端 | 运行中(47.83.2.188) |
| **MBclaw-workspace** | Android 客户端(双版本) + 服务端配置 | 开发中 |
| **miclaw-apk-analysis** | MiClaw 原版 APK 逆向分析 + UI 截图 | 参考用 |
| **openclaw** | 参考项目 | 参考用 |

## 二、服务器

| 服务器 | IP | 用途 |
|--------|-----|------|
| 后端 API | 47.83.2.188:80 | FastAPI + 管理面板 + 调试 + 桥接 |
| 下载站 | 121.199.57.195:80 | APK 分发 + Linux rootfs + 静态页面 |

---

## 三、已完成功能 (Root版 v4.7)

### 3.1 核心 AI Agent
- [x] AgentLoop: LLM决策→工具调用→观察结果→循环
- [x] Function Calling: 84个工具 (OpenAI格式)
- [x] 多模型支持: OpenAI兼容API, 火山引擎, 智谱
- [x] MBclawEnforcer: 代码级行为约束 (身份/记忆/工具强注入)
- [x] 21个AI助手 (含 Ponytail 懒人模式)
- [x] 右滑打开助手列表

### 3.2 屏幕交互
- [x] ScreenAnalyzer: uiautomator dump + 无障碍 双源
- [x] TouchInjector: 三通道触摸 (input tap → sendevent → 无障碍)
- [x] VisionLocator: 截图→VLM→坐标 (Open-AutoGLM架构参考)
- [x] CapabilityRouter: ROOT→ADB→无障碍 三层调度
- [x] AgentHand: 双通道视觉定位 (关键词模糊+VLM精确)
- [x] click_by_index / input_by_index / find_by_text / see_screen / wait_screen

### 3.3 Root 权限
- [x] PermissionTier: 多路径 root 探测 (su/Magisk/KernelSU/云手机mciu)
- [x] shellRoot: sh -c 优先, su -c 兜底, 多 su 路径
- [x] RootBootstrap: 分批 pm grant + appops + 无障碍绑定 + 电池白名单
- [x] 权限状态检测: 逐项检查, 真实验证

### 3.4 权限与安全
- [x] SafeOps: 删除前自动备份 (3份循环), 高风险路径需确认
- [x] PermissionPolicy: 用户偏好 (允许/永久禁止/每次询问)
- [x] 60+ 危险权限清单
- [x] 权限详情页: 逐项查看+操作
- [x] 权限一键授予页: 品牌检测→服务器模板→逐项真实验证
- [x] "早知如此何必当初呢" 弹窗

### 3.5 用户界面
- [x] MiClaw 风格 UI (70-80%参考)
- [x] 聊天页 + 侧边栏 + 设置页
- [x] 主题切换 (浅色/深色/跟随系统)
- [x] 动画淡入淡出
- [x] 首次安装弹窗 (无root提示)
- [x] 返回键优先级: 弹窗→抽屉→路由pop→再次退出→回桌面

### 3.6 服务端
- [x] FastAPI 后端 (已部署)
- [x] 版本管理端点 (/admin/client/version)
- [x] 调试端点 (heartbeat/cmd/result/send/devices/results)
- [x] 权限模板端点 (/admin/client/perm-template)
- [x] MiClaw 桥接 (apply/login/status/chat)
- [x] 管理面板 API (/api/admin/stats, /api/admin/users, /api/admin/metrics, /api/admin/downloads)

### 3.7 下载中心
- [x] 双卡片并排设计 (Root版+Lite版)
- [x] 点击展开详情
- [x] 版本历史列表
- [x] APK 直接下载链接

### 3.8 热更新
- [x] HotfixLoader: 启动检查→下载patch.zip→DexClassLoader加载
- [x] loadPatch() 在 onCreate 最前, 补丁类优先
- [x] 每版本只下载一次

### 3.9 远程调试
- [x] DebugRemote: 心跳+指令轮询+执行回传
- [x] 设备自动生成唯一码 (设备指纹前8位)
- [x] 默认开启调试模式
- [x] 连接码点击复制
- [x] 服务端可查看在线设备/发指令/看结果

### 3.10 账号系统
- [x] QQ号自动提取 (5策略+3次重试)
- [x] QQ头像下载 (q.qlogo.cn)
- [x] 账号云端同步
- [x] 提取失败不阻塞启动

### 3.11 其他
- [x] 悬浮窗 (AI运行中,带暂停键+拖动)
- [x] 通知栏 (MBclaw正在干什么)
- [x] 文件上传 (+号, 图片/APK/压缩包/文档)
- [x] 更新检测弹窗 (立即更新/稍后/忽略本次)
- [x] Linux环境预留接口 (~200MB Alpine rootfs, 一键下载+自动装包)

---

## 四、非Root版 (MBclaw Lite) 状态

### 4.1 已完成
- [x] 基于 Root 版完整代码, 仅关闭 root 探测
- [x] 包名 com.mbclaw.nonroot (与Root版共存)
- [x] 蓝色聊天气泡图标
- [x] 触摸走无障碍手势
- [x] 独立签名 (lite.keystore)
- [x] 20MB APK

### 4.2 待完成
- [ ] VisionLocator 不支持 (非Root无法截图)
- [ ] RootBootstrap 无操作 (无root)
- [ ] 无障碍服务自动开启提示

---

## 五、待完成 — 按优先级排列

### P0: 致命Bug
- [ ] root检测误判: 云手机 `/acct/.mci/mciu` 静默授权导致永远检测到root
- [ ] shellRoot 死锁风险: 只读 stdout, 大输出时缓冲区满→死锁 (需参考Aether的并行读stdout+stderr)
- [ ] 消息重复存储: AgentLoop 和 ChatViewModel 同时保存
- [ ] 调试结果回传不稳定 (postJson 可能静默失败)

### P1: 体验缺陷
- [ ] 输入框被键盘遮挡 (VisionVoiceSheet 修复不够)
- [ ] 云手机 root 开关行为异常 (开=提示/关=静默)
- [ ] 权限授予成功率低 (RootBootstrap batch 可能失败)
- [ ] QQ号提取准确率 (10538 不是有效QQ号)
- [ ] 无root提示条 "没有女朋友的心" 在云手机上永远不会出现

### P2: 功能缺失
- [ ] MCP 插件市场 (预留接口已做, 后端未实现)
- [ ] Linux 环境完整集成 (rootfs已上传, 自动装包代码有但未测试)
- [ ] 对话中上传的文件没有真正发送到服务器
- [ ] 服务器管理面板无前端 (API已有, HTML需补全JS)
- [ ] 下载量统计客户端上报
- [ ] 更新弹窗中 "稍后" 按钮跳转到设置页的具体位置

### P3: 优化
- [ ] 热更新补丁过大 (60MB包含所有dex, 应只包含变更的类)
- [ ] 权限授予日志诊断 (Aether风格)
- [ ] 进程执行改用 ProcessBuilder (防死锁)
- [ ] 对话session恢复 (杀后台回来丢最后几条消息)

---

## 六、项目畅想 (待实现)

### 6.1 MCP 插件生态
```
用户可以在"插件市场"浏览/安装社区贡献的 MCP 服务器
→ Google Search, GitHub, 本地文件, 天气, 股票...
→ 每个MCP插件按需从服务器下载
→ sandbox隔离执行
```

### 6.2 完整 Linux 环境
```
一键下载 Alpine rootfs → 自动解压 → chroot/proot
预装: Python3, bash, curl, git, vim, pip, node
用途: 编译代码, 运行脚本, 安装任意Linux包
Agent 可以直接调 linux_exec() 工具
```

### 6.3 AI 智能体之手 2.0
```
当前: VLM看图→输出坐标→执行点击
目标: VLM看图→理解UI语义→多步操作规划→执行+验证
参考: OpenAI Operator, Anthropic Computer Use
```

### 6.4 管理面板完整版
```
当前: 4个基础API, HTML骨架
目标: 完整的10模块面板 (仪表盘/用户/会话/记忆/版本/调试/桥接/模板/热更/配置)
实时数据, 图表, 搜索, 操作日志
```

### 6.5 云端协同
```
用户数据云端备份 → 换手机自动恢复
多设备共享记忆 → 手机+平板+电脑
Agent 能力云端增强 → 本地轻量+云端重量
```

### 6.6 社区与生态
```
插件市场 (开发者提交MCP工具)
助手市场 (用户分享自定义人格)
权限模板共享 (各品牌手机用户贡献)
主题市场 (自定义UI)
```

---

## 七、给 AI 助手的任务清单

> 以下任务适合给"纯思考、超高并发、不能联网"的AI:

### 任务A: 代码审查
贴以下文件, 让AI找bug并给出修复:
1. `agent/RootBootstrap.kt` — 为什么权限授权不稳定
2. `agent/PermissionTier.kt` — root探测逻辑漏洞
3. `agent/AgentLoop.kt` — 消息重复存储
4. `agent/DebugRemote.kt` — 结果回传不稳定
5. `ui/screens/SettingsPage.kt` — UI逻辑

### 任务B: 状态机设计
描述场景, 让AI设计完整状态流转:
1. 首次安装→root检测→权限授予→完成
2. 更新检测→下载→安装→生效
3. MCP插件 安装→配置→启用→调用

### 任务C: Prompt工程
1. 视觉定位 system prompt (让VLM更准)
2. Agent identity constraint (避免AI说"我不能")
3. 各种助手的 system prompt 优化

### 任务D: 管理面板JS补全
贴HTML+API清单, 让AI补全所有fetch()调用

---

## 八、技术债务

| 债务 | 影响 | 修复难度 |
|------|------|---------|
| Runtime.exec 死锁风险 | 大输出命令卡死 | 中 |
| 调试结果回传静默失败 | 看不到设备状态 | 低 |
| 热更新补丁过大 | 下载慢 | 中 |
| 权限授予一次性命令太长 | 部分权限失败 | 低(已分批) |
| postJson 无错误回调 | 排查困难 | 低(已加log) |
| 无自动化测试 | 回归bug多 | 高 |

---

## 九、关键设计决策 (已锁定)

1. ✅ Android Root版 + Lite版 双版本, 不同包名共存
2. ✅ Python/FastAPI 后端, 不动不换
3. ✅ Kotlin/Jetpack Compose, 不用跨平台框架
4. ✅ 非Root版基于Root版完整代码, 只关root探测
5. ✅ 服务器地址通过GitHub注册中心动态更新, 不写死
6. ✅ 热更新用 DexClassLoader 合并 dexElements
7. ✅ root执行优先 sh -c, su -c 多路径兜底
8. ✅ 权限检测用 /data/system/packages.list (需root)

---

## 十、版本历史

| 版本 | 主要更新 |
|------|---------|
| v3.5 | 工具扩到84, Root shell落地 |
| v3.6 | bug修复大版, 后端管理面板, 自动root权限 |
| v3.7 | MiClaw UI复刻, 自动备份, 默认乌托邦 |
| v3.8 | 任务1-12全部落地 |
| v3.9 | 新图标, 真实赞赏码, MiClaw桥接 |
| v4.0 | 真分流乌托邦, 跳酷安 |
| v4.1 | 服务器IP混淆, Root优先级修复, 主题切换, QQ自动登录 |
| v4.2 | 触摸root, 悬浮窗, 视觉语音Key, 主题真切换 |
| v4.3 | 智能手装眼睛, bug修复 |
| v4.4 | 4项重大修复, 视觉模型锁死 |
| v4.5 | CapabilityRouter, 反向调试, 助手切换 |
| v4.6 | TouchInjector多通道, VisionLocator VLM, 双通道屏幕感知, 20助手 |
| v4.7 | 多路径root(云手机), 热更新v2, 更新弹窗, 文件上传, Linux环境接口 |
