# MBOS v3 施工任务清单

> 基于 v1/v2/v3 全部素材融合收敛后的可执行计划

---

## 融合分析

### 吸取的核心思想

| 来源 | 核心思想 | 取舍 |
|------|---------|------|
| v1 EventKernel | Event Log 唯一真相源 | ✅ 已实现，保留 |
| v1 Governor | Policy gate，只产event不执行 | ✅ 已实现，保留 |
| v1 Projectors | Event→View投影 | ✅ 已实现，保留 |
| v2 Planner+Scheduler | DAG编译+执行分配 | ⚠️ 概念正确，但独立模块过度设计→合并为ExecutionEngine |
| v2 Worker Pool | 多模型多Worker | ⚠️ 方向对，但母体还没Key，先做单Worker骨架 |
| v3 Syscall | 统一API入口 | ✅ 降级为 function-level API |
| v3 收敛 | 删虚层，留4核心 | ✅ 采纳 |
| v3 最终 | Event→ExecutionEngine→Worker→Policy 单链路 | ✅ 采纳 |

### 删除的

| 删除项 | 原因 |
|--------|------|
| Planner 独立层 | 并入 ExecutionEngine |
| Scheduler 独立层 | 并入 ExecutionEngine |
| Workspace 独立系统 | 降级为 event query |
| Projection 独立系统 | 已有 `projectors.py`，够用 |
| Syscall 独立层 | 内化为 API contract |
| Worker 类型体系 | 先做基类，不建分类树 |
| Knowledge Graph | Phase C |
| Self Evolution | Phase C |
| Event Bus | Phase B |

### 保留的4核心

```
Event Kernel (truth)
    ↓
Execution Engine (DAG + run)
    ↓
Worker Runtime (pure exec)
    ↓
Policy Engine (gate)
```

---

## 当前现状 vs 目标

| 模块 | 已有 | 缺 |
|------|------|-----|
| Event Kernel | `event_log.py` append/read/replay | Event Model dataclass |
| Execution Engine | — 全缺 | DAG compiler + task mapper + dispatcher |
| Worker Runtime | `LLMClient` (仅摘要) | Worker基类 + LLM Worker + Tool Worker |
| Policy Engine | `governor.py` check_action | risk model + policy compiler |
| State | `projectors.py` project_state/decisions | query views |
| Capability Registry | `capabilities/registry.py` | — |

---

## 任务清单

### Phase A：补齐核心4模块（5个任务）

| # | 任务 | 文件 | 做法 |
|---|------|------|------|
| A1 | Event Model | `mother/event_model.py` | Event dataclass + create_event() factory |
| A2 | Worker基类 | `mother/worker.py` | Worker ABC + LLMWorker(调DirectApiClient) + ToolWorker(调capabilities) |
| A3 | Execution Engine | `mother/engine.py` | ExecutionEngine.execute(goal)→DAG编译→Policy check→Worker执行→log result |
| A4 | DAG Compiler | `mother/dag.py` | Goal→Task DAG (简单topo sort实现，先不做NetworkX依赖) |
| A5 | Policy风险模型 | `mother/policy.py` | 从 `governor.py` 抽离 risk scoring: structural/execution/data/cost |

### Phase B：串联执行链路（3个任务）

| # | 任务 | 文件 | 做法 |
|---|------|------|------|
| B1 | 改造 agent.py | `agent.py` | MotherAgent.process() 改为走 ExecutionEngine 链路 |
| B2 | 网关接入 Engine | `gateway/router.py` | MessageRouter.send_to_agent() → ExecutionEngine.execute() |
| B3 | Event 记录结果 | `engine.py` | 每次执行完成 append result event → projector rebuild |

### Phase C：APK端收尾（2个任务）

| # | 任务 | 文件 | 做法 |
|---|------|------|------|
| C1 | CapabilityRegistry 命名统一 | `ToolRegistry.kt` | 类名改 CapabilityRegistry，向后兼容 |
| C2 | APK端 Worker 概念 | `agent/MBclawAgent.kt` | 预留 Worker 接口，暂不改执行逻辑 |

### Phase D：编译部署

| # | 任务 |
|---|------|
| D1 | 云电脑编译 v5.5.2 |
| D2 | 推两站 + MD5验证 |
| D3 | 服务端重启 + QQ Bot上线 |

---

## 改动范围汇总

| 层 | 新建 | 修改 |
|----|------|------|
| 服务端 Python | `event_model.py` `worker.py` `engine.py` `dag.py` `policy.py` (5文件) | `agent.py` `gateway/router.py` (2文件) |
| APK Kotlin | — | `ToolRegistry.kt` `MBclawAgent.kt` (2文件) |
| 数据库 | — | 不改 |
| API | — | 不改 |
| Nginx | — | 不改 |

**总计**: 新建5个Python文件, 修改4个文件。零数据库变更。

---

## 执行顺序

```
A1(event_model) → A2(worker) → A4(dag) → A5(policy) → A3(engine)
    ↓
B1(agent改造) → B2(网关接入) → B3(event记录)
    ↓
C1(CapabilityRegistry改名) → C2(Worker预留)
    ↓
D1-D3(编译部署)
```
