# MBclaw v6 母体网关 — 工程级施工PRT

> 版本: v6.0 | 日期: 2026-06-29 | 状态: 待施工

## 核心校正（v5→v6）

| v5 问题 | v6 校正 |
|---------|---------|
| 单会话=单线程 | **单Agent + MessageQueue(FIFO)** |
| 数据平铺 | **raw(全量) + view(过滤) 双层** |
| Gateway=route() | **Adapter → Normalize → Router → ResponseDispatcher 四层** |
| ToolRegistry各自为政 | **CapabilityRegistry 统一** |
| 无并发控制 | **session锁 + queue顺序消费** |

---

## 一、架构总览

```
QQ Bot ──┐
微信 Bot ─┤
飞书 Bot ─┼→ [Adapter 层] → [Normalize 层] → [Router 层] → ┌──────────────┐
Web聊天 ──┤                                                  │  母体 Agent   │
终端CLI ──┘                                                  │ session=global│
                                 │                           │   message queue│
                    [Response Dispatcher] ←───────────────── │   CapabilityRegistry│
                         │                                   └──────────────┘
          QQ/微信/飞书/Web/CLI ← 回复分发
```

---

## 二、标准消息协议

```json
{
  "trace_id": "uuid-v4",
  "session_id": "global",
  "channel": "qq|wechat|feishu|web|cli",
  "user_id": "device_code",
  "message": "text",
  "timestamp": 1719000000,
  "attachments": [],
  "meta": {
    "reply_to": null,
    "priority": "normal",
    "source_message_id": null
  }
}
```

---

## 三、数据双层结构

```
/var/lib/mbclaw/
  users/
    {code}/                    ← 每用户独立
      raw/                     ← 完整原始数据(不可修改)
        2026-06-29.jsonl
        2026-06-30.jsonl
      view/                    ← 过滤后(UI/面板用)
        heartbeat.json
        summary.json
        keys_public.json       ← 脱敏后的Key信息
      index.json               ← 快速索引
      permissions.json
      keys.json                ← 完整Key(母体可见)
      uploads/
  gateway/
    registry.json              ← 网关注册信息
  capabilities/
    registry.json              ← CapabilityRegistry 持久化
    skills/
    mcp/
    apis/
```

---

## 四、Gateway 四层职责

### 4.1 Adapter 层（只收发）
- `gateway/adapters/qqbot.py` — go-cqhttp WebSocket
- `gateway/adapters/wechat.py` — 企业微信 API
- `gateway/adapters/feishu.py` — 飞书/Lark SDK
- `gateway/adapters/web.py` — FastAPI HTTP 路由
- `gateway/adapters/cli.py` — WebSocket 终端

**禁止**: 调LLM、处理记忆、直接写磁盘

### 4.2 Normalize 层
- `gateway/normalize.py`
- 输入: 各adapter的原始消息
- 输出: 标准Message结构
- 职责: 字段映射、时间戳标准化、trace_id生成

### 4.3 Router 层
- `gateway/router.py`
- `send_to_agent(message: StandardMessage) -> str`
- 职责: 入队 → 等Agent消费 → 返回回复
- 内部: `asyncio.Queue` FIFO + `asyncio.Lock` 单线程消费

### 4.4 Response Dispatcher
- `gateway/dispatcher.py`
- 输入: Agent回复
- 输出: 分发到对应channel的adapter
- 职责: 格式化(QQ用Markdown/飞书用卡片/CLI纯文本)

---

## 五、Agent 层（母体）

```python
# agent.py - 单Agent内核
class MotherAgent:
    session_id = "global"
    message_queue: asyncio.Queue[StandardMessage]
    capability_registry: CapabilityRegistry
    memory: MemoryRepo
    
    async def run(self):
        while True:
            msg = await self.message_queue.get()
            # 1. 加载记忆 (按channel/user过滤最近N条)
            context = self.memory.load(session_id="global", limit=20)
            # 2. 调用LLM (通过capability_registry获取tools)
            reply = await self.llm.chat(context + [msg], tools=self.capability_registry.list())
            # 3. 保存到raw + 更新index
            self.memory.save(msg.trace_id, msg, reply)
            # 4. 分发回复
            await dispatcher.dispatch(msg.channel, reply)
```

**规则**:
- 只有一个 Agent 实例
- message_queue FIFO 顺序消费
- tools 从 CapabilityRegistry 动态获取
- memory 全局共享，按 channel 标记来源

---

## 六、CapabilityRegistry 统一接口

```json
{
  "id": "github-search",
  "type": "mcp",
  "source": "cloud",
  "entry": "server.py",
  "manifest": {
    "name": "GitHub Search",
    "description": "Search GitHub repositories",
    "parameters": { "type": "object", "properties": { "query": { "type": "string" } } }
  },
  "enabled": true,
  "installed_at": 1719000000
}
```

**统一方法**:
- `register(capability) → None`
- `unregister(id) → None`
- `list(type=None) → List[Capability]`
- `get(id) → Capability`
- `search(query) → List[Capability]`
- `subscribe() → AsyncIterator[Event]`  → 驱动UI自动刷新

**类型枚举**: `skill | mcp | api | tool | workflow`
**来源枚举**: `builtin | cloud | runtime | import`

---

## 七、任务执行清单

| Phase | # | 任务 | 文件(服务端) | 文件(APK) |
|-------|---|------|-------------|-----------|
| **Phase1 数据** | G1.1 | 创建双层目录结构 | `main.py` | — |
| | G1.2 | 迁移心跳写入 raw/index | `debug_api_v2.py` `admin_api.py` | — |
| | G1.3 | 迁移对话写入 raw/index | `api.py` `pipeline.py` | — |
| **Phase2 母体** | G2.1 | Agent单实例+message_queue | `agent.py` | — |
| | G2.2 | create_session固定global | `api.py` | — |
| **Phase3 网关** | G3.1 | Normalize层(标准消息) | `gateway/normalize.py` | — |
| | G3.2 | Router层(send_to_agent) | `gateway/router.py` | — |
| | G3.3 | Dispatcher层 | `gateway/dispatcher.py` | — |
| | G3.4 | Web Adapter | `gateway/adapters/web.py` | — |
| | G3.5 | CLI Adapter | `gateway/adapters/cli.py` | — |
| | G3.6 | QQ Bot Adapter | `gateway/adapters/qqbot.py` | — |
| | G3.7 | 微信 Bot Adapter | `gateway/adapters/wechat.py` | — |
| | G3.8 | 飞书 Bot Adapter | `gateway/adapters/feishu.py` | — |
| | G3.9 | 注册所有adapter | `main.py` | — |
| **Phase4 注册中心** | G4.1 | CapabilityRegistry(服务端) | `capabilities/registry.py` | — |
| | G4.2 | APK端 ToolRegistry→CapabilityRegistry | — | `ToolRegistry.kt` |
| | G4.3 | UI绑定 Flow | — | `ToolsScreen.kt` |
| **Phase5 面板** | D4 | 设备列表加列 | `panel_one.html` `panel.js` | — |
| | D6 | 下载统计JS | `index.html`×2 | — |

---

## 八、风险矩阵

| 风险 | 等级 | 缓解 |
|------|------|------|
| 单Agent消息积压 | 🟡 | queue最大100条，超出拒绝+返回"请稍后" |
| raw数据无限增长 | 🟡 | 每日切分.jsonl，30天归档 |
| Gateway并发连接 | 🟢 | asyncio原生支持 |
| adapter崩溃影响母体 | 🟡 | 每个adapter独立进程/线程，崩溃不影响核心 |

---

## 九、下一步选择

A. **Gateway完整代码结构** — 给你 `gateway/` 目录下每个文件的类图+方法签名
B. **Registry实现模板** — CapabilityRegistry 的完整Python实现
C. **Flow式UI绑定** — APK端 ToolRegistry→CapabilityRegistry 改造代码
D. **直接开工 Phase1** — 从数据重组开始施工
