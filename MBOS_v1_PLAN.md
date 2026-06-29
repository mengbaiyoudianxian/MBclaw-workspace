# MBOS v1 — MBclaw Operating System 施工方案

> 日期: 2026-06-29 | 母体=操作系统，LLM=CPU之一

---

## 零、核心定义

```
母体 ≠ 一个AI对话机器人
母体 = MBclaw Operating System（MBOS）

LLM 只是 Runtime 里的 Worker 之一。
不是母体。
```

---

## 一、架构图

```
                        YOU
                         │
                 Mother Workspace
                         │
        ┌────────────────────────────────┐
        │ Governor（治理层）             │  ← 铁律宪法，最高优先级
        │ 永远遵守工程规则               │
        └────────────────────────────────┘
                         │
        ┌────────────────────────────────┐
        │ Executive（执行官）            │
        │ 当前目标 / Phase / Roadmap     │
        └────────────────────────────────┘
                         │
        ┌────────────────────────────────┐
        │ Planner（规划器）              │
        │ Goal Tree → Task Graph → Todo  │
        └────────────────────────────────┘
                         │
        ┌────────────────────────────────┐
        │ Scheduler（调度器）            │
        │ Worker / Model / Token         │
        └────────────────────────────────┘
                         │
        ┌────────────────────────────────┐
        │ Runtime（运行时）              │
        │ Worker Executor                │
        └────────────────────────────────┘
                         │
         ┌───────┬────────┬────────┬────────┐
         │Claude │ Gemini │ GPT    │ Local  │
         └───────┴────────┴────────┴────────┘
```

---

## 二、十层优化（分三期施工）

### 第一层：Governor（治理层）

**位置**: 整个系统最顶层，位于 Executive 之上。

**职责**:
- 永远遵守你的工程铁律（先搜再改、只改造不重写、不擅自编译等）
- 控制风险等级，高风险修改必须等待批准
- 控制 Token 与算力预算
- 审核 Evolution Engine 提出的所有 PRT，避免重复、冲突或偏离路线图
- 保证任何自动化修改都可回滚、可追踪、可审计

**实现**: `mother/governor.py`
```python
class Governor:
    iron_rules = [
        "先搜再改: grep → 调用链 → git blame → GitHub → 确认无现成实现后才改",
        "只改造不重写: 复用现有代码, 保持代码风格/命名/目录/架构",
        "禁止新建: 未经批准不新建Screen/DB/Service/Repository",
        "不改无关代码: 不借机重构",
        "不擅自部署: 编译/重启/杀进程需用户批准",
        "单Agent: 全局只有一个Agent实例, FIFO消费消息",
    ]
    risk_levels = ["low", "medium", "high", "critical"]
    
    def check_action(self, action: dict) -> bool: ...
    def inject_rules(self, message: str) -> str: ...
```

### 第二层：Executive（执行官）

**位置**: Governor 之下，接收指令。

**职责**: 维护当前工程状态。不是聊天记录。

**实现**: `mother/project_state.json`
```json
{
  "project": "MBclaw",
  "current_phase": "Gateway",
  "current_task": "QQ Bot Adapter",
  "current_branch": "mother-v6",
  "blocked": [],
  "next": ["Router", "Dispatcher", "WeChat Adapter", "Feishu Adapter"]
}
```

以后说"继续" → Executive 直接恢复整个工程状态，不需要翻聊天记录。

### 第三层：Planner + Goal Tree

**职责**: 把目标拆成可执行任务树。

```
Goal（目标）
  ↓
Milestone（里程碑）
  ↓
Feature（功能）
  ↓
Task（任务）
  ↓
SubTask（子任务）
  ↓
Operation（操作）
```

**实现**: `mother/goal_tree.json`
```json
{
  "goal": "MBclaw v6 Gateway",
  "milestones": [
    {
      "name": "Phase2: Single Agent",
      "status": "done",
      "features": [
        {
          "name": "MotherAgent单实例",
          "status": "done",
          "tasks": [
            {"name": "asyncio.Queue FIFO", "status": "done"},
            {"name": "create_session固定global", "status": "done"}
          ]
        }
      ]
    }
  ]
}
```

### 第四层：Scheduler + Worker Pool

**职责**: 不只有LLM。所有Worker统一调度。

```
Code Worker     → 写代码/改文件
Review Worker   → 代码审查
Search Worker   → 搜索/GitHub/grep
Memory Worker   → 压缩/索引/检索
Compress Worker → 上下文压缩
Planner Worker  → 任务规划
Browser Worker  → 网页操作
Android Worker  → APK端执行
Server Worker   → 服务器操作
```

每个Worker可以绑定不同的LLM模型：
```
Review Worker → Claude（最强审查）
Search Worker → Gemini（搜索便宜）
Compress Worker → Qwen（本地压缩）
```

### 第五层：Decision Memory（决策记忆）

**职责**: 记录每次重大决策和否决的原因。防止AI重复犯错。

**实现**: `mother/memory/decision/`

文件命名: `YYYY-MM-DD-{topic}.md`

示例: `2026-06-29-unified-capability-registry.md`
```markdown
# 决策: 统一CapabilityRegistry

**日期**: 2026-06-29
**决策者**: 孟白
**状态**: 已采纳

## 为什么不用 ToolRegistry 多重继承
- 会造成多份数据源
- UI层需要手动合并
- 后续Plugin/Skill/API无法统一注册

## 为什么统一 Capability
- 单一数据源
- StateFlow 驱动 UI
- register/unregister 统一接口

## 否决的方案
- ❌ ToolRegistry + PluginRegistry 双轨制 — 维护成本高
- ❌ Room 数据库 — 引入新依赖，过度设计
- ❌ 每个Provider独立StateFlow — UI层合并逻辑复杂
```

以后AI永远不会再问"要不要重新做ToolRegistry"，因为Decision Memory告诉它：已被否决。

### 第六层：Knowledge Graph（知识图谱）

**职责**: 功能/模块/文件/API 之间的关联关系。

```
Bug: vision_locate返回(0,0)
  ↓ 影响
Module: VisionLocator.kt
  ↓ 涉及
Class: LocateResult
  ↓ 涉及
Method: parseVisionResponse()
  ↓ 调用
API: 视觉模型 Base URL
```

以后AI搜索不是 grep，而是走 Graph 关系链。

### 第七层：Runtime Registry（统一注册中心）

**职责**: 不是只有 Tool。所有东西都注册。

```
Capability Registry:
  - Skill
  - MCP
  - API
  - Tool
  - Workflow

Worker Registry:
  - Code Worker
  - Review Worker
  - Search Worker

Model Registry:
  - Claude
  - Gemini
  - GPT
  - Qwen

Gateway Registry:
  - QQ Bot
  - WeChat Bot
  - Feishu Bot
  - Web
  - CLI

Memory Provider Registry:
  - Raw Store
  - Index Store
  - View Store
```

全部统一接口:
```python
Registry.register(cap)
Registry.list(type=None)
Registry.search(query)
Registry.enable(id)
Registry.disable(id)
```

### 第八层：Workspace（工作空间）

**职责**: 替代无限的聊天上下文。保持当前工作状态。

**实现**: `mother/workspace/current/`
```
context.md          ← 当前上下文摘要（不是聊天记录）
todo.md             ← 当前待办
goal.md             ← 当前目标
opened_files.json   ← 当前打开的文件列表
active_workers.json ← 当前激活的Worker
active_models.json  ← 当前使用的模型
recent_decisions.md ← 最近的决策
recent_errors.md    ← 最近的错误
```

Claude Code 缺的就是这个 — 上下文越来越长。Workspace 永远保持当前状态。

### 第九层：Event Bus（事件总线）

**职责**: 所有变化广播。UI自动订阅刷新。

```
Capability Installed → Event
Plugin Updated → Event
Worker Finished → Event
Gateway Online → Event
Phase Completed → Event
```

```python
class EventBus:
    def publish(event_type: str, data: dict) -> None: ...
    def subscribe(event_type: str) -> AsyncIterator[Event]: ...
```

所有 UI 自动订阅:
```kotlin
// ToolsScreen.kt
val tools by EventBus.subscribe("capability.*").collectAsState()
```

### 第十层：Self Evolution Engine（自我进化）

**职责**: 持续观察系统，发现问题，生成改进方案（PRT），等待批准。

**流程**:
```
Observation（观察）
      ↓
Problem Detection（问题检测）
      ↓
Improvement Proposal（改进方案）
      ↓
Risk Analysis（风险分析）
      ↓
PRT Generation（生成施工方案）
      ↓
Waiting Approval（等待批准）
```

**示例**:
```
发现: QQ读取最近失败率 42%
      Root成功率下降
      Android16出现变化
      ↓
生成: PRT-2026-114《QQ读取兼容Android16》
      风险: 低
      影响文件: QQAutoLogin.kt
      预计改动: 28行
```

**前置条件**: 前面9层全部跑起来。这是最后一层。

---

## 三、分期施工

### Phase A（现在 — 1小时内完成）

| # | 优化 | 文件 | 做法 |
|---|------|------|------|
| A1 | Governor | `mother/governor.py` | 固化17条铁律，Agent启动时自动注入 |
| A2 | Project State | `mother/project_state.json` | 存current_phase/task/blocked |
| A3 | Decision Memory | `mother/memory/decision/` | 记录3个关键决策(Registry/单Agent/单会话) |
| A4 | 命名收尾 | `ToolRegistry.kt` → `CapabilityRegistry` | APK端改完 |

### Phase B（下一轮）

| # | 优化 | 做法 |
|---|------|------|
| B1 | Goal Tree | `mother/goal_tree.json` |
| B2 | Workspace | `mother/workspace/current/` |
| B3 | EventBus | `mother/event_bus.py` |
| B4 | Snapshot | `pipeline.py` → phase完成自动snapshot |

### Phase C（远期）

| # | 优化 | 做法 |
|---|------|------|
| C1 | Worker Pool | Scheduler + Worker + Model路由 |
| C2 | Knowledge Graph | SQLite图结构 |
| C3 | Self Evolution | EvolutionWorker常驻 |

---

## 四、当前进度

| 层级 | 状态 |
|------|------|
| Phase1 数据双层存储 | ✅ |
| Phase2 母体单Agent | ✅ |
| Phase3 网关5通道 | ✅ (QQ Bot已测试连通) |
| Phase4 CapabilityRegistry | ✅ |
| APK v5.5.1 | ✅ 云电脑编译+部署 |
| Governor | ❌ 待施工 |
| Project State | ❌ 待施工 |
| Decision Memory | ❌ 待施工 |
| Goal Tree | ❌ |
| Workspace | ❌ |
| Worker Pool | ❌ |
| Knowledge Graph | ❌ |
| Self Evolution | ❌ |
