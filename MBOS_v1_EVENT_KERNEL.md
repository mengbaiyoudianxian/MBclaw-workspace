# MBOS v1.0 — Event Kernel 设计

> 从"结构设计" → "一致性模型设计"的关键跃迁

---

## 核心修正

```
v0(错误): 4个独立真相源 → state divergence
v1(修正): 1个Event Log → 所有view都是projection
```

---

## 一、Event Schema（唯一真相源）

```json
{
  "event_id": "uuid",
  "event_type": "goal.created | task.started | task.done | task.failed | 
                decision.made | decision.revoked | 
                capability.registered | capability.removed |
                phase.completed | snapshot.created",
  "timestamp": 1719000000,
  "actor": "governor | planner | scheduler | worker:code | worker:review | human",
  "payload": {},
  "parent_event_id": null,
  "consistency": "strong"  // 单用户系统始终strong
}
```

### event_type 完整枚举

```
// 目标层
goal.created / goal.updated / goal.completed / goal.abandoned

// 任务层
task.created / task.started / task.done / task.failed / task.blocked

// 决策层
decision.made / decision.revoked / decision.superseded

// 能力层
capability.registered / capability.unregistered / capability.enabled / capability.disabled

// 阶段层
phase.started / phase.completed / phase.rolled_back

// 快照层
snapshot.created / snapshot.restored

// 系统层
governor.rule_violation / worker.error / model.limit_reached
```

---

## 二、State Projection 规则

### 2.1 Event Log → Project State

```python
def project_state(events: list[Event]) -> ProjectState:
    state = ProjectState()
    for e in events:
        if e.type == "goal.created": state.current_goal = e.payload["goal_id"]
        if e.type == "phase.started": state.current_phase = e.payload["phase_name"]
        if e.type == "task.done": state.completed_tasks.append(e.payload["task_id"])
        if e.type == "task.blocked": state.blocked.append(e.payload["task_id"])
    return state
```

**规则**: project_state.json **绝不写入**。每次从 Event Log 重建。

### 2.2 Event Log → Goal Tree

```python
def project_goal_tree(events: list[Event]) -> GoalTree:
    tree = GoalTree()
    for e in events:
        if e.type == "goal.created":
            tree.add_node(e.payload["goal_id"], e.payload["parent_id"])
        if e.type == "goal.completed":
            tree.mark_done(e.payload["goal_id"])
    return tree
```

### 2.3 Event Log → Decision Memory

```python
def project_decisions(events: list[Event]) -> list[Decision]:
    decisions = []
    for e in events:
        if e.type == "decision.made":
            decisions.append(Decision(
                id=e.payload["decision_id"],
                topic=e.payload["topic"],
                reasoning=e.payload["reasoning"],
                rejected=e.payload["rejected_alternatives"],
                status="active"
            ))
        if e.type == "decision.revoked":
            mark_revoked(decisions, e.payload["decision_id"])
    return decisions
```

### 2.4 Event Log → Workspace

```python
def project_workspace(events: list[Event]) -> Workspace:
    ws = Workspace()
    active_events = [e for e in events if e.type in [
        "task.started", "goal.created", "capability.registered"]]
    ws.opened_files = extract_files(active_events)
    ws.active_workers = extract_workers(active_events)
    ws.recent_errors = [e for e in events[-50:] if e.type in [
        "task.failed", "worker.error", "governor.rule_violation"]]
    return ws
```

---

## 三、执行边界（谁能改什么）

```
Event Log:
  WRITE: Planner, Scheduler, Governor, Human
  READ:  Everyone

Project State:
  WRITE: ❌ 禁止直接写入 (从Event Log重建)
  READ:  Everyone

Goal Tree:
  WRITE: ❌ 禁止直接写入
  READ:  Everyone

Decision Memory:
  WRITE: ❌ 禁止直接写入
  READ:  Everyone

Workspace:
  WRITE: ❌ 禁止直接写入
  READ:  Everyone
```

**核心规则**: 只有 `append_event()` 能改变系统状态。所有 view 都是不可变的 projection。

---

## 四、Governor边界（不能再变大）

```
Governor:
  CAN: 产生 event "governor.rule_violation"
  CAN: 产生 event "governor.approval_required"
  CAN: 拒绝任务开始 (pre-condition check)
  CANNOT: 修改 Event Log 中已有的事件
  CANNOT: 直接修改 Project State
  CANNOT: 执行 Worker
  CANNOT: 调用 LLM
```

**Governor 只是 Policy Enforcement Point。不是 super-agent。**

---

## 五、Scheduler Contract（确定性语义）

```json
{
  "task_id": "uuid",
  "task_type": "code.write | code.review | search.web | memory.compress",
  "input_schema": { "type": "object", "properties": {...} },
  "output_schema": { "type": "object", "properties": {...} },
  
  "worker": "code_worker | review_worker | search_worker",
  "model": "claude-sonnet | gemini-flash | qwen-3b",
  "priority": 1,
  
  "retry": { "max": 3, "backoff": "exponential" },
  "determinism": "pure | idempotent | mutable",
  "timeout_ms": 120000,
  
  "sandbox": {
    "can_read": ["workspace/*", "events/*"],
    "can_write": [],
    "can_network": true
  }
}
```

---

## 六、Worker Isolation（防止隐式共享内存）

```
每个 Worker 运行在独立 sandbox:
  - 输入: JSON schema
  - 输出: JSON schema
  - 副作用: 只能通过 append_event()
  - 文件访问: sandbox.can_read / sandbox.can_write 白名单
  - 网络: sandbox.can_network
```

---

## 七、Self Evolution Engine — 降级为推荐系统

```
Evolution Engine:
  CAN: 读取 Event Log
  CAN: 产生 "evolution.proposal" event
  CAN: 附带 PRT 文档
  CANNOT: 修改 Event Log
  CANNOT: 触发任何 Worker
  CANNOT: 修改系统结构
```

本质: **RECOMMENDATION SYSTEM, NOT EXECUTION SYSTEM**

---

## 八、MBOS v1.0 最小可执行版

砍掉所有未完成模块，只保留:

```
1. Event Log        → mother/events/{date}.jsonl
2. Governor         → mother/governor.py（纯Policy，只产生event）
3. Projectors       → mother/projectors.py（只读Event Log）
4. Planner          → 复用现有 Goal Tree
5. Scheduler        → 复用现有 MotherAgent + MessageQueue
6. Worker Runtime   → 复用现有 LLMClient + tools
```

### 文件清单

```
mother/
  events/
    2026-06-29.jsonl          ← 追加写入
  governor.py                 ← Policy Engine
  projectors.py               ← Event Log → Views
  project_state.json          ← ⚠️ 只读, 从Event Log重建
  goal_tree.json              ← ⚠️ 只读
  decision/
    2026-06-29-registry.md
  workspace/
    context.md                ← ⚠️ 只读
    todo.md                   ← ⚠️ 只读
```

### 改动范围

| 文件 | 改动 |
|------|------|
| `mother/governor.py` | 新建，Policy Engine |
| `mother/projectors.py` | 新建，Event → View |
| `mother/events/` | 新建，追加写入jsonl |
| `mother/agent.py` | 修改，appended_event()替代直接写状态 |
| `mother/project_state.json` | 新建，projection(只读) |

5个文件。不改数据库。不改API。不改APK。

---

## 九、下一步

你说得对——现在不是加功能的时候。现在是**定义一致性模型**的时候。

要我直接写 Governor + Projector + Event Log 三个核心文件的代码吗？
