# MBclaw 服务端管理面板 — 完整功能规格书

## 技术栈
- 后端: FastAPI (已有) + SQLite (已有)
- 前端: 纯 HTML/CSS/JS（单文件，无需构建工具，直接嵌入 FastAPI）
- 风格: 暗色主题，卡片布局，移动端适配
- 认证: 简单的 Token 认证（配置文件设置 admin_token）

---

## 1. 📊 仪表盘 Dashboard

### 1.1 顶部统计卡片（4个）
| 卡片 | 数据来源 | 说明 |
|------|---------|------|
| 总用户数 | `SELECT COUNT(DISTINCT user_id) FROM miclaw_instances` + 调试心跳用户 | 去重用户数 |
| 活跃会话 | `SELECT COUNT(*) FROM sessions WHERE status='active'` | 当前进行中的会话 |
| 记忆条目 | `SELECT COUNT(*) FROM memories` / SQLite 表大小 | 长期记忆数据量 |
| 工具调用 | 内存计数器 或 `/api/stats/tools` | 今日/累计工具调用次数 |

### 1.2 系统状态面板
- 服务运行时间（uptime）
- 数据库状态（OK/ERROR）
- Python 版本
- 磁盘使用率 `/var/lib/mbclaw`
- 内存使用
- 最后备份时间

### 1.3 实时图表（可选，简化版）
- 最近 24 小时 API 请求量（柱状图用 ASCII或简单 Canvas）
- 最近 7 天新增用户数

### 1.4 API 端点需求
```
GET /api/admin/stats        → { users: N, sessions: N, memories: N, tools_today: N }
GET /api/admin/health       → { uptime, db_ok, disk_free, memory_used, python_version }
GET /api/admin/requests/stats → { last_24h: [{hour, count}], last_7d: [{date, new_users}] }
```

---

## 2. 👥 用户管理

### 2.1 用户列表
表格列：QQ号 | 微信ID | 昵称 | 设备ID | 设备型号 | 版本 | 最后在线 | 权限数 | 操作
- 搜索：按 QQ号/设备ID 搜索
- 排序：按最后在线时间倒序
- 分页：每页 20 条

### 2.2 用户详情
点击用户行展开/跳转：
- 基本信息：QQ号、微信ID、昵称、头像 URL
- 设备信息：型号、品牌、Android SDK 版本、Root 状态、无障碍状态
- 权限详情：已授予权限列表（绿色勾/红色叉）
- 使用统计：总会话数、总消息数、Token 消耗估算
- 操作按钮：拉黑设备、清除用户数据、查看对话记录

### 2.3 黑白名单管理
- 黑名单：IP 列表、设备 ID 列表、封禁原因、封禁时间
- 白名单：设备 ID 列表
- 操作：添加/移除、批量导入

### 2.4 API 端点需求
```
GET    /api/admin/users                    → { users: [...], total, page }
GET    /api/admin/users/:uid               → { user详细 }
POST   /api/admin/users/:uid/blacklist     → 拉黑
DELETE /api/admin/users/:uid/blacklist     → 移除黑名单
GET    /api/admin/blacklist                → 黑名单列表
POST   /api/admin/blacklist                → 添加黑名单
DELETE /api/admin/blacklist/:id            → 移除
```

---

## 3. 💬 会话管理

### 3.1 会话列表
表格列：会话ID | 用户 | 标题 | 状态(active/closed) | 消息数 | 创建时间 | 更新时间 | 操作
- 搜索：按标题/用户搜索
- 过滤：按状态（全部/进行中/已关闭）

### 3.2 会话详情
点击展开：
- 完整对话记录（类似聊天界面）
- 每条消息：角色(user/assistant/system) | 内容 | 时间 | 工具调用详情
- 关联记忆引用
- 操作：删除会话、导出 JSON

### 3.3 API 端点需求
```
GET    /api/admin/sessions                  → { sessions: [...], total }
GET    /api/admin/sessions/:sid             → { session详情, messages: [...] }
DELETE /api/admin/sessions/:sid             → 删除会话
GET    /api/admin/sessions/:sid/export      → 导出JSON
```

---

## 4. 🧠 记忆管理

### 4.1 记忆列表
表格列：ID | 关联会话 | 关键词 | 内容摘要(150字) | 创建时间
- 搜索：全文搜索记忆内容
- 删除：单个/批量删除

### 4.2 API 端点需求
```
GET    /api/admin/memories                  → { memories: [...], total }
GET    /api/admin/memories/search?q=xxx     → 搜索结果
DELETE /api/admin/memories/:mid             → 删除
DELETE /api/admin/memories/batch            → 批量删除 { ids: [...] }
```

---

## 5. 📱 版本管理

### 5.1 版本配置
当前生效的版本信息：
- 最新版本号（如 `4.7-root`）
- 最低支持版本
- 下载 URL
- 更新日志（支持多行 Markdown）
- 强制更新开关
- 保存按钮

### 5.2 版本历史
表格列：版本号 | 更新日志 | 发布时间 | 操作(设为当前/删除)

### 5.3 APK 上传
- 拖拽/点击上传 APK 文件
- 自动识别版本号（解析 APK 的 AndroidManifest）
- 上传到下载服务器（scp 或 HTTP PUT 到 download_server）
- 上传进度条

### 5.4 API 端点需求
```
GET    /api/admin/version                   → 当前版本配置
POST   /api/admin/version                   → 更新版本配置
GET    /api/admin/version/history           → 版本历史
POST   /api/admin/version/upload            → 上传APK(multipart)
POST   /api/admin/version/:v/set-current    → 设为当前版本
```

---

## 6. 🐛 远程调试控制台

### 6.1 在线设备列表
表格列：连接码 | 设备ID | 型号 | 品牌 | 用户 | Root | 无障碍 | 权限数 | 最后心跳 | 操作
- 自动刷新（每 5 秒）
- 操作：发送指令、查看详情、断开

### 6.2 设备详情
- 完整权限状态
- Shell 命令输入框 + 执行按钮 + 输出区域（类似终端）
- 快捷指令按钮：权限检查、屏幕 dump、截图、logcat、点击测试
- 点击测试：输入坐标 X,Y → 发送 click 指令
- 指令历史（最近 10 条）

### 6.3 指令与结果
- 发送指令后等待结果（轮询）
- 结果显示区域（支持滚动）
- 结果可复制

### 6.4 API 端点需求（已有，需完善）
```
GET    /api/admin/debug/devices             → 在线设备列表 ✅
POST   /api/admin/debug/send                → 发送指令 ✅
GET    /api/admin/debug/results             → 查看结果 ✅
POST   /api/admin/debug/screenshot/:code    → 请求截图
GET    /api/admin/debug/screenshot/:code    → 获取截图(base64)
```

---

## 7. 🔌 MiClaw 桥接管理

### 7.1 代理实例列表
表格列：实例ID | 用户ID | 端口 | PID | 状态(运行/停止) | 已登录 | Utopia开关 | 创建时间 | 操作
- 操作：查看详情、重启、停止、删除

### 7.2 实例详情
- 实例配置信息
- Token 消耗统计（自用/共享出/共享入）
- 请求次数统计
- 桥进程日志（tail -100）

### 7.3 平台池管理
- 平台池总算力（所有 utopia=1 用户合计）
- 借用排行榜（贡献最多的用户）
- 借用记录

### 7.4 违规用户管理
- 失败次数统计
- 拉黑列表（IP + 设备ID）
- 封禁原因记录
- 操作：手动拉黑/解除、查看失败记录

### 7.5 API 端点需求
```
GET    /api/admin/bridge/instances           → 实例列表
GET    /api/admin/bridge/instances/:id       → 实例详情
POST   /api/admin/bridge/instances/:id/restart → 重启
DELETE /api/admin/bridge/instances/:id       → 停止+删除
GET    /api/admin/bridge/instances/:id/log   → 获取日志
GET    /api/admin/bridge/stats               → 平台池统计
GET    /api/admin/bridge/violations          → 违规记录
POST   /api/admin/bridge/blacklist           → 拉黑
```

---

## 8. 🔧 权限模板管理

### 8.1 模板列表
品牌 | Android版本 | 模板名称 | 权限数 | 特殊处理 | 操作

### 8.2 模板编辑
- 表单编辑各字段
- 权限列表 checkbox（勾选包含在此模板中的权限）
- 特殊处理命令编辑器（textarea）
- 添加新模板/删除

### 8.3 API 端点需求
```
GET    /api/admin/perm-templates             → 模板列表
GET    /api/admin/perm-templates/:key        → 模板详情
POST   /api/admin/perm-templates             → 创建/更新模板
DELETE /api/admin/perm-templates/:key        → 删除模板
```

---

## 9. 🔥 热更新管理

### 9.1 当前补丁状态
- 当前补丁版本号
- 描述
- 下载 URL
- 文件大小

### 9.2 补丁上传
- 上传新的 classes.zip（多 dex 文件打包）
- 自动设置为 latest
- 补丁历史列表

### 9.3 API 端点需求
```
GET    /api/admin/hotfix/latest              → 当前补丁信息
POST   /api/admin/hotfix/upload              → 上传新补丁
GET    /api/admin/hotfix/history             → 补丁历史
```

---

## 10. ⚙️ 系统配置

### 10.1 LLM 提供商配置
- 当前使用的 Provider
- 模型名称
- Base URL
- API Key（脱敏显示）
- 修改表单

### 10.2 服务器地址配置
- 后端地址
- 下载服务器地址
- GitHub 注册中心地址
- 保存按钮

### 10.3 数据管理
- 数据库大小
- 备份：手动触发备份 / 自动备份开关
- 清理：清除 X 天前的会话 / 清除 X 天前的记忆
- 导出全部数据

### 10.4 API 端点需求
```
GET    /api/admin/config                     → 全部配置
POST   /api/admin/config                     → 更新配置
POST   /api/admin/config/backup              → 触发备份
POST   /api/admin/config/cleanup             → 清理数据 { type, days }
GET    /api/admin/config/export              → 导出全部数据
```

---

## 🎨 UI 设计规范

### 布局
- 左侧边栏（240px）：导航菜单 + Logo
- 右侧主内容区：根据导航切换 Tab
- 移动端：侧边栏折叠为汉堡菜单

### 配色（暗色主题）
```
--bg: #0d1117           // 主背景
--surface: #161b22      // 卡片背景
--border: #30363d       // 边框
--text: #c9d1d9         // 主文字
--text-muted: #8b949e   // 次要文字
--accent: #58a6ff       // 强调色（蓝）
--green: #3fb950        // 成功
--red: #f85149          // 错误/危险
--orange: #d2991d       // 警告
```

### 组件
- 统计卡片：图标 + 数值 + 标签
- 数据表格：斑马纹 + 悬停高亮 + 排序箭头
- 徽章：ok(绿) / warn(橙) / err(红)
- 进度条：水平条+百分比文字
- 终端：黑色背景+绿色等宽字体+自动滚动到底部

### 导航菜单
```
📊 仪表盘
👥 用户管理
💬 会话记录
🧠 记忆管理
📱 版本管理
🐛 远程调试
🔌 MiClaw桥接
🔧 权限模板
🔥 热更新
⚙️ 系统配置
```

---

## 🔐 安全
- 所有 `/api/admin/*` 端点需要 Bearer Token 认证
- Token 从配置文件 `admin_token` 读取或环境变量 `MBCLAW_ADMIN_TOKEN`
- 登录页：输入 Token → 验证 → 存储到 sessionStorage → 所有请求带 Authorization header

---

## 📦 部署方式
- 前端文件放在 `server/admin_panel/` 目录
- FastAPI 挂载静态文件：
  ```python
  app.mount("/admin", StaticFiles(directory="admin_panel", html=True))
  ```
- 或嵌入到 main.py 的响应中（当前方式）
- 推荐改为独立静态文件 + API 调用方式，便于修改

---

## 优先级排序
1. 🔴 P0: 仪表盘 + 版本管理 + APK上传
2. 🟠 P1: 远程调试控制台 + 用户管理
3. 🟡 P2: 会话管理 + MiClaw桥接 + 热更新
4. 🟢 P3: 记忆管理 + 权限模板 + 系统配置
