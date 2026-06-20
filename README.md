# MBclaw 项目总览

> 最后更新：2026-06-20 | 基于完整工作区扫描

---

## 一、项目结构

```
MBclaw-workspace/
├── main/                         ← 主项目
│   ├── MBclaw/                   ← 设计文档仓库（11份架构文档，中英双语）
│   └── MBclaw-Lite/              ← 代码实现（FastAPI + SQLite + ChromaDB）
├── server/                       ← 服务端母体（新增）
│   ├── admin_panel/              ← Web 管理面板
│   ├── gateway/                  ← Nginx API 网关
│   ├── deploy/                   ← Docker Compose 生产部署
│   └── monitoring/               ← Prometheus + Grafana 监控
├── clients/                      ← 三平台客户端（新增）
│   ├── android/                  ← Android APK（Kotlin + Jetpack Compose）
│   ├── linux/                    ← Linux CLI 客户端（Python）
│   └── windows/                  ← Windows 桌面客户端（Python + tkinter）
└── reference/                    ← 参考素材
    ├── miclaw-apk-analysis/      ← MiClaw APK逆向分析
    ├── miclaw-apk-analysis-full/ ← 完整APK分析（含APK文件）
    └── openclaw/                 ← OpenClaw 架构参考
```

---

## 二、组件完成度

| 模块 | 状态 | 说明 |
|------|------|------|
| MBclaw-Lite 后端 | ✅ 97% | 33/34完成，仅MiMo集成未做 |
| MiMo Code 集成 | ✅ 已完成 | adapter + change_detector + providers API |
| 服务端母体 | ✅ 已完成 | Docker Compose + Admin Panel + 监控 |
| Linux CLI 客户端 | ✅ 已完成 | 交互式聊天 + 项目管理 + 记忆搜索 |
| Windows 客户端 | ✅ 已完成 | tkinter GUI + 设置面板 |
| Android 客户端 | ✅ 已完成 | Kotlin + Jetpack Compose |
| 文档 | ✅ 已完成 | 11份中英双语架构文档 |

---

## 三、快速启动

### 后端

```bash
cd main/MBclaw-Lite
pip install -r requirements.txt
LLM_ENABLED=true LLM_API_KEY=sk-... python -m uvicorn app.main:app --reload
```

### 服务端（完整部署）

```bash
cd server/deploy
docker-compose -f docker-compose.prod.yml up -d
```

### Linux 客户端

```bash
cd clients/linux
pip install -e .
mbclaw config --set server_url=http://localhost:8000
mbclaw chat
```

### Windows 客户端

```bash
cd clients/windows
pip install requests
python mbclaw_win/client.py
```

### Android 客户端

用 Android Studio 打开 `clients/android/`，同步 Gradle，运行。

---

## 四、API 端点（39个）

### 核心 CRUD
- `GET/POST /api/users` — 用户管理
- `GET/POST /api/projects` — 项目管理
- `GET/POST /api/projects/{id}/sessions` — 会话管理
- `GET/POST /api/sessions/{id}/messages` — 消息管理
- `GET/POST /api/projects/{id}/dna` — 项目DNA

### 记忆系统
- `GET/PUT /api/projects/{id}/memory/durable` — MEMORY.md
- `GET/POST /api/projects/{id}/memory/daily` — 每日笔记
- `GET/POST /api/projects/{id}/memory/dreams` — Dreaming 整合

### 搜索与分类
- `GET /api/search?q=` — 全文搜索
- `GET/POST /api/projects/{id}/topics` — 话题树
- `POST /api/projects/{id}/search/semantic` — 语义搜索
- `POST /api/projects/{id}/search/prefetch` — 记忆预调用

### Agent
- `POST /api/agent/run` — 执行 Agent 循环
- `GET /api/tasks` — 任务队列
- `POST /api/projects/{id}/auto/decide` — 自动决策

### 集成（新增）
- `GET /api/providers` — 模型Provider列表
- `GET /api/providers/mimo/status` — MiMo试用状态
- `POST /api/providers/mimo/test` — 测试MiMo连接
- `POST /api/providers/configure` — 运行时配置切换
- `POST /api/projects/{id}/check/regression` — 回滚检测

### 网关
- `POST /api/gateway/{platform}/{id}` — 11平台消息接入

---

## 五、GitHub 仓库

| 仓库 | URL |
|------|-----|
| MBclaw（文档） | https://github.com/mengbaiyoudianxian/MBclaw |
| MBclaw-Lite（代码） | https://github.com/mengbaiyoudianxian/MBclaw-Lite |
| miclaw-apk-analysis | https://github.com/mengbaiyoudianxian/miclaw-apk-analysis |

---

## 六、下一步计划

1. 🔧 GitHub Token 修复 — 需要用户重新配置 GITHUB_TOKEN
2. 🚀 代码推送 — 将所有新增代码推送到 GitHub
3. 🧪 MiMo 实战测试 — 配置真实 MiMo Key 测试
4. 📱 APK 编译 — 用 Android Studio 编译生成 APK
5. 🔗 MiClaw 融合测试 — MBclaw × MiClaw 劫持方案实战
