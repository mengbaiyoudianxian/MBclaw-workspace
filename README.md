# MBclaw 项目总览

> 最后更新：2026-06-20 | 由孟白打造，耗时2个月

---

## 一、项目结构

```
MBclaw-workspace/
├── main/                         ← 主项目
│   ├── MBclaw/                   ← 设计文档（11份架构文档，中英双语）
│   └── MBclaw-Lite/              ← 后端代码（FastAPI + SQLite + ChromaDB）
├── server/                       ← 服务端母体
│   ├── admin_panel/              ← Web 管理面板（验证工具）
│   ├── gateway/                  ← Nginx API 网关
│   ├── deploy/                   ← Docker Compose 生产部署
│   └── monitoring/               ← Prometheus + Grafana 监控
├── clients/                      ← 三平台客户端
│   ├── android/
│   │   ├── root/                 ← Android Root版（MiClaw内核 + MBclaw AI）
│   │   └── nonroot/              ← Android 非Root版（GPT + MiClaw融合，MBclaw品牌）
│   ├── linux/                    ← Linux CLI 客户端
│   └── windows/                  ← Windows 原生客户端
└── reference/                    ← 参考素材
    ├── miclaw-apk-analysis/      ← MiClaw APK逆向 + 劫持方案
    ├── miclaw-apk-analysis-full/ ← 完整分析（9个APK含语音APK）
    └── openclaw/                 ← OpenClaw 架构参考
```

## 二、核心完成度 100%

| 模块 | 状态 | 详情 |
|------|------|------|
| 核心13项目 | ✅ 13/13 | 含MiMo集成 |
| Hermes 记忆系统 | ✅ 6/6 | MEMORY.md + Dreaming + Curator + Skill |
| Agent Runtime | ✅ 6/6 | 执行循环 + 子Agent + 自纠正 |
| 服务端母体 | ✅ | Docker + Admin Panel + Prometheus |
| Android Root 版 | ✅ | MiClaw内核 + 386工具 + 本地沙箱 |
| Android 非Root版 | ✅ | GPT风格 + MBclaw品牌 |
| Linux CLI | ✅ | 交互式聊天 + 记忆搜索 |
| Windows 客户端 | ✅ | tkinter GUI |

## 三、架构决策

- **独立发展**：不 Fork OpenClaw，代码100%自己编写
- **Python/FastAPI**：不换 TypeScript，保持独立技术栈
- **三端独立原生**：不跨平台框架，各端原生开发
- **Android 策略**：Root版保留70% MiClaw UI + 替换AI后端；非Root版模仿GPT+MiClaw融合
- **MiMo Key**：tp-s6rzaq... (820亿token) / mimo-v2.5-pro / token-plan-sgp.xiaomimimo.com
- **测试服务器**：47.83.2.188 (root/20070520@han)

## 四、参考项目

| 项目 | 用途 |
|------|------|
| [OpenClaw](https://github.com/openclaw/openclaw) | 架构借鉴 |
| [OpenHands](https://github.com/All-Hands-AI/OpenHands) | 参考 |
| [miclaw_api_bridge](https://github.com/NEORUAA/miclaw_api_bridge) | MiClaw API 桥接 |
| [MiMo Code](https://github.com/mimo-ai/mimocode) | AI编程工具 |
| [Claude Code](https://github.com/anthropics/claude-code) | 参考 |

## 五、快速启动

### 后端
```bash
cd main/MBclaw-Lite
pip install -r requirements.txt
MIMO_API_KEY=tp-s6rzaq... MIMO_BASE_URL=https://token-plan-sgp.xiaomimimo.com/v1 \
  uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### 服务端母体
```bash
cd server/deploy
docker-compose -f docker-compose.prod.yml up -d
# Web 控制台: http://localhost:8080
```

### GitHub 仓库
- [MBclaw（文档）](https://github.com/mengbaiyoudianxian/MBclaw)
- [MBclaw-Lite（代码）](https://github.com/mengbaiyoudianxian/MBclaw-Lite)
- [MBclaw-workspace（工作区）](https://github.com/mengbaiyoudianxian/MBclaw-workspace)
- [miclaw-apk-analysis](https://github.com/mengbaiyoudianxian/miclaw-apk-analysis)
