# MBclaw 母体网关 + 数据重组 施工方案

> 日期: 2026-06-29 | 版本: v1.0

## 架构设计

```
 QQ Bot ──┐
 微信 Bot ─┤
 飞书 Bot ─┼→ Gateway Registry → 单会话(母体) → Agent(LLM+Tools)
 Web聊天 ──┤
 终端CLI ──┘

 所有渠道 → 同一个对话流 → 同一个记忆库 → 母体全可见
 母体=唯一控制人，任何设备/渠道的数据无隐私屏蔽
```

## 数据目录

```
/var/lib/mbclaw/
  users/
    {debug_code}/          ← 每用户独立文件夹
      heartbeat.json       ← 心跳数据
      conversations.jsonl  ← 对话记录
      permissions.json     ← 权限状态
      keys.json            ← API配置(母体可见)
      uploads/             ← 用户上传文件
  gateway/
    registry.json          ← 网关注册信息
  version.json             ← 版本管理
```

## 任务清单(执行顺序)

### D4: 管理面板设备列表加列
- **文件**: `panel_one.html`
- **改动**: 设备表格加 QQ号/root状态/key配置/版本/最后心跳 列
- **理由**: API已有数据，HTML未渲染

### D6: 下载统计追踪
- **文件**: `index.html` × 2站 (121.199.57.195 + 8.130.42.188)
- **改动**: 下载链接加 onclick JS 调 `/admin/downloads/track`
- **理由**: API已存在，缺前端调用

### G1: 数据分文件夹
- **文件**: `api.py` `debug_api_v2.py` `admin_api.py`
- **改动**: 心跳/对话/Key 存储路径从 `heartbeat_logs/*.json` 改为 `users/{code}/`
- **理由**: 统一数据组织，母体可见全部用户数据

### G2: 母体单会话
- **文件**: `api.py`
- **改动**: `create_session` 按 device_id 复用已有 session，关闭多会话逻辑
- **理由**: 母体只有你一个用户，所有渠道共用一个对话流

### G3: 网关注册中心
- **文件**: `gateway/__init__.py` (新建)
- **改动**: `GatewayRegistry`: register(name, adapter) / route(msg) / channels()
- **理由**: 统一管理所有渠道接入，不新增数据库(用JSON文件)

### G4: Web聊天网关
- **文件**: `gateway/web.py` (新建)
- **改动**: FastAPI路由 `/gateway/web` → 简易聊天页，对接GatewayRegistry
- **理由**: 浏览器直接访问母体对话

### G5: 终端CLI网关
- **文件**: `gateway/terminal.py` (新建)
- **改动**: WebSocket `/gateway/cli/ws` → 纯文本收发
- **理由**: 命令行直连母体

### G6: QQ Bot
- **文件**: `gateway/qqbot.py` (新建)
- **改动**: 对接 go-cqhttp WebSocket → 收到消息→GatewayRegistry→返回回复
- **理由**: QQ渠道接入母体

### G7: 微信Bot
- **文件**: `gateway/wechat.py` (新建)
- **改动**: 对接企业微信 API → 同上模式
- **理由**: 微信渠道接入母体

### G8: 飞书Bot
- **文件**: `gateway/feishu.py` (新建)
- **改动**: 对接飞书开放平台 → 同上模式
- **理由**: 飞书渠道接入母体

## 改动范围

- **服务端**: 修改3个文件 + 新建7个文件
- **下载站**: 修改2个HTML
- **客户端(APK)**: 不改(心跳已上报debug code)
- **数据库**: 不改(复用现有SQLite)
- **Nginx**: 不改

## 参考项目

- [OpenClaw](https://github.com/openclaw/openclaw) — 多通道AI网关(TypeScript)
- [OpenClaw-Docker-CN-IM](https://github.com/justlovemaki/OpenClaw-Docker-CN-IM) — 中文IM预配置Docker版
- [OpenClaw Python Port](https://github.com/openxjarvis/openclaw-python) — Python端实现
