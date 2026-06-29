# MBclaw v6 完整类结构图

> APK (Kotlin/Compose) + Server (Python/FastAPI)

## 一、Server 端

```
gateway/
├── __init__.py          # GatewayRegistry
├── normalize.py         # MessageNormalizer
├── router.py            # MessageRouter
├── dispatcher.py        # ResponseDispatcher
└── adapters/
    ├── __init__.py      # AdapterBase
    ├── web.py           # WebAdapter
    ├── cli.py           # CliAdapter
    ├── qqbot.py         # QQBotAdapter
    ├── wechat.py        # WechatAdapter
    └── feishu.py        # FeishuAdapter

capabilities/
├── __init__.py
├── registry.py          # CapabilityRegistry
└── models.py            # Capability, CapabilityType, CapabilitySource

agent/
├── __init__.py
├── mother.py            # MotherAgent
├── context_governor.py  # ContextGovernor
├── memory/
│   ├── __init__.py
│   ├── compressor.py    # MemoryCompressor
│   ├── raw_store.py     # RawStore
│   ├── index_store.py   # IndexStore
│   └── view_store.py    # ViewStore
└── runtime.py           # DualRuntimeManager

data/
├── __init__.py
├── user_store.py        # UserStore (双层 raw/view)
├── message_protocol.py  # StandardMessage
└── event_bus.py         # EventBus (后续Phase)
```

---

### 1.1 StandardMessage

```python
# data/message_protocol.py
from dataclasses import dataclass, field
from typing import Optional
import uuid, time

@dataclass
class StandardMessage:
    trace_id: str = field(default_factory=lambda: str(uuid.uuid4()))
    session_id: str = "global"
    channel: str = ""           # qq|wechat|feishu|web|cli
    user_id: str = ""            # device_code
    message: str = ""
    timestamp: float = field(default_factory=time.time)
    attachments: list = field(default_factory=list)
    meta: dict = field(default_factory=dict)
    # reply_to / priority / source_message_id

    def to_json(self) -> str: ...
    def to_raw_line(self) -> str: ...  # .jsonl 一行
```

### 1.2 GatewayRegistry

```python
# gateway/__init__.py
from .adapters import AdapterBase

class GatewayRegistry:
    """管理所有channel adapter"""
    _adapters: dict[str, AdapterBase] = {}

    def register(self, name: str, adapter: AdapterBase) -> None: ...
    def unregister(self, name: str) -> None: ...
    def get(self, name: str) -> AdapterBase: ...
    def list_channels(self) -> list[str]: ...
    async def start_all(self) -> None: ...
    async def stop_all(self) -> None: ...
```

### 1.3 AdapterBase

```python
# gateway/adapters/__init__.py
from abc import ABC, abstractmethod

class AdapterBase(ABC):
    name: str = ""
    
    @abstractmethod
    async def start(self) -> None: ...
    @abstractmethod
    async def stop(self) -> None: ...
    @abstractmethod
    async def send(self, target: str, message: str, meta: dict = None) -> bool: ...
    # 收消息通过 callback → Router
    def set_on_message(self, callback) -> None: ...
```

### 1.4 WebAdapter

```python
# gateway/adapters/web.py
# FastAPI路由: GET /gateway/web → HTML聊天页
#               POST /gateway/web/api → 收消息→callback→router
#               GET /gateway/web/ws → WebSocket(后续)

class WebAdapter(AdapterBase):
    name = "web"
    async def start(self) -> None:
        """注册FastAPI路由"""
    async def stop(self) -> None: ...
    async def send(self, target: str, message: str, meta=None) -> bool:
        """通过SSE或poll返回回复"""
```

### 1.5 CliAdapter

```python
# gateway/adapters/cli.py
# WebSocket ws://host/gateway/cli/ws
# 每行=一条消息, 回复同样按行返回

class CliAdapter(AdapterBase):
    name = "cli"
    _connections: dict[str, WebSocket] = {}
    async def start(self) -> None: ...
    async def stop(self) -> None: ...
    async def send(self, target: str, message: str, meta=None) -> bool: ...
```

### 1.6 QQBotAdapter

```python
# gateway/adapters/qqbot.py
# 对接 go-cqhttp WebSocket
# 正向ws: ws://127.0.0.1:6700

class QQBotAdapter(AdapterBase):
    name = "qq"
    _ws_url: str = "ws://127.0.0.1:6700"
    async def start(self) -> None:
        """连接go-cqhttp, 监听消息事件"""
    async def stop(self) -> None: ...
    async def send(self, target: str, message: str, meta=None) -> bool:
        """send_private_msg / send_group_msg"""
    def _on_private_msg(self, raw: dict) -> None:
        """回调 → normalize → router"""
    def _on_group_msg(self, raw: dict) -> None:
        """仅@机器人时触发"""
```

### 1.7 WechatAdapter / FeishuAdapter

```python
# 同模式: 收→normalize→callback, 回复→send
# wechat: 企业微信Webhook + 消息推送
# feishu: 飞书开放平台 Event + Message API
```

### 1.8 MessageNormalizer

```python
# gateway/normalize.py

class MessageNormalizer:
    """各adapter原始消息 → StandardMessage"""
    
    CHANNEL_MAP = {
        "qq": "_normalize_qq",
        "wechat": "_normalize_wechat",
        "feishu": "_normalize_feishu",
        "web": "_normalize_web",
        "cli": "_normalize_cli",
    }

    def normalize(self, channel: str, raw: dict) -> StandardMessage: ...
    def _normalize_qq(self, raw: dict) -> StandardMessage: ...
    def _normalize_wechat(self, raw: dict) -> StandardMessage: ...
    def _normalize_feishu(self, raw: dict) -> StandardMessage: ...
    def _normalize_web(self, raw: dict) -> StandardMessage: ...
    def _normalize_cli(self, raw: dict) -> StandardMessage: ...
```

### 1.9 MessageRouter

```python
# gateway/router.py
import asyncio

class MessageRouter:
    """统一入口 → Agent queue"""
    _queue: asyncio.Queue[StandardMessage] = asyncio.Queue(maxsize=100)
    _agent: "MotherAgent" = None

    def bind_agent(self, agent: "MotherAgent") -> None: ...

    async def send_to_agent(self, msg: StandardMessage) -> str:
        """入队 → 等Agent消费 → 返回回复"""
        if self._queue.full():
            return "系统繁忙，请稍后重试"
        await self._queue.put(msg)
        # Agent消费后通过Future返回
        future = asyncio.get_event_loop().create_future()
        self._pending[msg.trace_id] = future
        return await future

    async def consume_loop(self) -> None:
        """Agent消费循环"""
        while True:
            msg = await self._queue.get()
            reply = await self._agent.process(msg)
            # 通知等待者
            self._pending[msg.trace_id].set_result(reply)
```

### 1.10 ResponseDispatcher

```python
# gateway/dispatcher.py
from . import GatewayRegistry

class ResponseDispatcher:
    """Agent回复 → 分发到对应channel"""
    _registry: GatewayRegistry = None

    def bind_registry(self, reg: GatewayRegistry) -> None: ...

    def format_by_channel(self, channel: str, text: str) -> str:
        """QQ用纯文本, 飞书用卡片JSON, CLI原样"""
        ...

    async def dispatch(self, msg: StandardMessage, reply: str) -> bool:
        """找对应adapter → send"""
        formatted = self.format_by_channel(msg.channel, reply)
        adapter = self._registry.get(msg.channel)
        if adapter:
            return await adapter.send(msg.user_id, formatted, msg.meta)
        return False
```

### 1.11 MotherAgent

```python
# agent/mother.py
import asyncio

class MotherAgent:
    session_id: str = "global"
    _router: "MessageRouter" = None
    _registry: "CapabilityRegistry" = None
    _governor: "ContextGovernor" = None
    _compressor: "MemoryCompressor" = None
    _raw_store: "RawStore" = None

    async def process(self, msg: StandardMessage) -> str:
        """单条消息处理 (FIFO顺序调用)"""
        # 1. 保存raw
        await self._raw_store.append(msg.user_id, msg)
        
        # 2. 构建上下文 (governor控制token数)
        context = await self._governor.build(msg.user_id, msg.message)
        
        # 3. 获取可用tools
        tools = self._registry.list()
        
        # 4. 调用LLM
        reply = await self._llm.chat(context, tools)
        
        # 5. 压缩记忆 (如果超出阈值)
        await self._governor.maybe_compress(msg.user_id)
        
        # 6. 更新index
        await self._index_store.update(msg.user_id, msg, reply)
        
        return reply

    async def run_forever(self) -> None:
        """主循环: 从router消费消息"""
        await self._router.consume_loop()
```

### 1.12 ContextGovernor

```python
# agent/context_governor.py

class ContextGovernor:
    """token估算 + 压缩触发 + context构建"""
    
    # 阈值常量
    NORMAL = 0.6     # < 60%: 正常
    SOFT = 0.8       # 60-80%: 软压缩
    HARD = 1.0       # 80-100%: 强制压缩
    REJECT = 1.0     # >100%: 拒绝

    def estimate_tokens(self, text: str) -> int:
        """简单估算: 英文~4char=1token, 中文~1.5char=1token"""
        ...

    def should_compress(self, user_id: str) -> tuple[bool, str]:
        """返回 (是否需要压缩, 压缩级别)"""
        ...

    async def build(self, user_id: str, current_msg: str) -> list[dict]:
        """构建当前context window"""
        tokens = self.estimate_tokens(current_msg)
        raw = await self._raw_store.load_recent(user_id, limit=50)
        all_tokens = self.estimate_tokens(raw)
        
        ratio = (tokens + all_tokens) / self.model_context_limit
        
        if ratio < self.NORMAL:
            return self._format_context(raw[-20:] + [current_msg])
        elif ratio < self.SOFT:
            summary = await self._compressor.soft_compress(raw)
            return self._merge(summary, raw[-10:], current_msg)
        else:
            structured = await self._compressor.hard_compress(raw)
            return self._merge(structured, raw[-5:], current_msg)

    async def maybe_compress(self, user_id: str) -> None:
        """处理完毕后检查是否需要压缩"""
        should, level = self.should_compress(user_id)
        if level == "soft":
            await self._compressor.soft_compress(user_id)
        elif level == "hard":
            await self._compressor.hard_compress(user_id)
```

### 1.13 MemoryCompressor

```python
# agent/memory/compressor.py

class MemoryCompressor:
    """三层压缩: 短期摘要 / 结构化状态 / 语义蒸馏"""
    
    async def soft_compress(self, user_id: str) -> str:
        """Layer A: 短期压缩 — 高保真摘要最近20-50条"""
        raw = await self._raw_store.load_recent(user_id, limit=50)
        prompt = self._build_soft_prompt(raw)
        return await self._llm.chat(prompt)

    async def hard_compress(self, user_id: str) -> dict:
        """Layer B: 结构压缩 — facts/decisions/tasks/tools/errors"""
        raw = await self._raw_store.load_recent(user_id, limit=100)
        prompt = self._build_structured_prompt(raw)
        result = await self._llm.chat(prompt)
        return json.loads(result)  # {"facts":[],"decisions":[],"open_tasks":[],...}

    async def semantic_distill(self, user_id: str) -> list:
        """Layer C: 长期语义压缩 — embedding聚类, 只保留标签"""
        # (后续Phase, 需要向量数据库)
        ...

    def _build_soft_prompt(self, raw: list) -> str:
        return f"Summarize these messages in 500 words, keep key facts and decisions:\n{raw}"

    def _build_structured_prompt(self, raw: list) -> str:
        return """Extract structured state from these messages.
Return JSON:
{
  "facts": ["key fact 1", ...],
  "decisions": ["decision 1", ...],
  "open_tasks": ["task 1", ...],
  "tools_used": ["tool 1", ...],
  "errors": ["error 1", ...]
}"""
```

### 1.14 RawStore / IndexStore / ViewStore

```python
# agent/memory/raw_store.py
class RawStore:
    """写raw/*.jsonl, 只追加不修改"""
    BASE = "/var/lib/mbclaw/users"
    async def append(self, code: str, msg: StandardMessage) -> None:
        path = f"{self.BASE}/{code}/raw/{today()}.jsonl"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "a") as f:
            f.write(msg.to_raw_line() + "\n")

    async def load_recent(self, code: str, limit: int = 50) -> list[StandardMessage]:
        """倒序读最近N条"""

# agent/memory/index_store.py
class IndexStore:
    """快速索引: 每条消息的 trace_id → timestamp → channel 映射"""
    async def update(self, code: str, msg: StandardMessage, reply: str) -> None:
        """写 index.json"""
    async def search(self, code: str, query: str, limit: int = 10) -> list:
        """关键词搜索"""

# agent/memory/view_store.py
class ViewStore:
    """UI/面板展示层: 过滤敏感字段, 允许重建"""
    async def rebuild(self, code: str) -> None:
        """从raw重建view"""
    async def get_heartbeat(self, code: str) -> dict: ...
    async def get_summary(self, code: str) -> dict: ...
```

### 1.15 CapabilityRegistry (Server版)

```python
# capabilities/registry.py
import asyncio
from .models import Capability, CapabilityType, CapabilitySource

class CapabilityRegistry:
    _items: dict[str, Capability] = {}
    _subscribers: list = []  # async iterator listeners
    _lock = asyncio.Lock()

    async def register(self, cap: Capability) -> None:
        async with self._lock:
            self._items[cap.id] = cap
        await self._emit("register", cap)

    async def unregister(self, id: str) -> None:
        async with self._lock:
            del self._items[id]
        await self._emit("unregister", id)

    def list(self, type: str = None) -> list[Capability]:
        items = self._items.values()
        return [c for c in items if type is None or c.type == type]

    def search(self, query: str, limit: int = 10) -> list[Capability]:
        """按name/description关键词搜索"""

    def get(self, id: str) -> Capability | None: ...

    async def subscribe(self):
        """AsyncIterator[Event] → 驱动UI/Agent更新"""
        q = asyncio.Queue()
        self._subscribers.append(q)
        try:
            while True:
                yield await q.get()
        finally:
            self._subscribers.remove(q)

    async def _emit(self, action: str, data) -> None:
        for q in self._subscribers:
            await q.put({"action": action, "data": data})
```

### 1.16 Capability Models

```python
# capabilities/models.py
from enum import Enum
from dataclasses import dataclass, field

class CapabilityType(str, Enum):
    SKILL = "skill"
    MCP = "mcp"
    API = "api"
    TOOL = "tool"
    WORKFLOW = "workflow"

class CapabilitySource(str, Enum):
    BUILTIN = "builtin"
    CLOUD = "cloud"
    RUNTIME = "runtime"
    IMPORT = "import"

@dataclass
class Capability:
    id: str
    type: CapabilityType
    source: CapabilitySource
    entry: str = ""            # 文件路径 或 URL
    manifest: dict = field(default_factory=dict)
    enabled: bool = True
    installed_at: float = 0.0
    downloads: int = 0

    @property
    def name(self) -> str: return self.manifest.get("name", self.id)
    @property
    def description(self) -> str: return self.manifest.get("description", "")
    @property
    def parameters(self) -> dict: return self.manifest.get("parameters", {})
```

### 1.17 DualRuntimeManager

```python
# agent/runtime.py
from enum import Enum

class RuntimeMode(str, Enum):
    LITE_APK = "lite"       # 60% 能力
    MOTHER_FULL = "full"    # 100% 能力

class DualRuntimeManager:
    mode: RuntimeMode = RuntimeMode.LITE_APK
    context_limit: int = 4096       # Lite默认
    compression_trigger: float = 0.7 # Lite更频繁
    enable_embedding: bool = False   # Lite关闭
    enable_multi_session: bool = False

    def switch_to(self, mode: RuntimeMode) -> None:
        if mode == RuntimeMode.MOTHER_FULL:
            self.context_limit = 128000
            self.compression_trigger = 0.8
            self.enable_embedding = True

    def get_tools_whitelist(self) -> list[str]:
        """Lite模式限制工具集"""
        if self.mode == RuntimeMode.LITE_APK:
            return ["toggle_wifi", "send_sms", ...]  # 常用工具
        return []  # 空=全部允许
```

---

## 二、APK 端 (Kotlin/Compose)

```
app/src/main/java/com/mbclaw/root/
├── capability/
│   ├── CapabilityRegistry.kt   # ← 从 ToolRegistry 演化
│   ├── CapabilityModels.kt     # Capability, CapabilityType, CapabilitySource
│   └── CapabilityProvider.kt   # BuiltinProvider / CloudProvider / ImportProvider
├── agent/
│   ├── ContextGovernor.kt      # token估算 + 压缩触发
│   ├── MemoryCompressor.kt     # 本地小模型摘要
│   └── DualRuntimeManager.kt   # Lite/Full 模式切换
├── ui/screens/
│   ├── ToolsScreen.kt          # collectAsState() ← CapabilityRegistry.flow
│   ├── SettingsPage.kt         # RuntimeMode 切换开关
│   └── McpSheet.kt / SkillSheet.kt  # 导入→register()
└── api/
    ├── DirectApiClient.kt      # LLM调用
    └── SyncClient.kt           # /client/sync/messages
```

### 2.1 CapabilityRegistry (APK版)

```kotlin
// capability/CapabilityRegistry.kt
object CapabilityRegistry {
    private val _items = MutableStateFlow<Map<String, Capability>>(emptyMap())
    val flow: StateFlow<List<Capability>> = _items.map { it.values.toList() }
        .stateIn(GlobalScope, SharingStarted.Eagerly, emptyList())

    // Builtin初始化
    fun initBuiltins() {
        BUILTIN_TOOLS.forEach { register(it.toCapability()) }
    }

    // 统一注册
    fun register(cap: Capability) {
        _items.update { it + (cap.id to cap) }
        android.util.Log.i("CapRegistry", "registered: ${cap.id} (${cap.type})")
    }

    fun unregister(id: String) {
        _items.update { it - id }
    }

    fun list(type: String? = null): List<Capability> =
        if (type == null) _items.value.values.toList()
        else _items.value.values.filter { it.type == type }

    fun search(query: String, limit: Int = 10): List<Capability> =
        list().filter { it.name.contains(query, true) || it.description.contains(query, true) }
            .take(limit)

    fun get(id: String): Capability? = _items.value[id]

    // 兼容旧代码
    val ALL: List<Capability> get() = list()

    // 生成OpenAI function calling格式
    fun toOpenAITools(): JSONArray {
        val arr = JSONArray()
        list().filter { it.enabled }.forEach { cap ->
            arr.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", cap.name)
                    put("description", cap.description)
                    put("parameters", cap.parameters)
                })
            })
        }
        return arr
    }
}
```

### 2.2 CapabilityModels (APK版)

```kotlin
// capability/CapabilityModels.kt
data class Capability(
    val id: String,
    val type: String,       // skill|mcp|api|tool
    val source: String,     // builtin|cloud|runtime|import
    val entry: String = "", // 文件路径或URL
    val name: String = "",
    val description: String = "",
    val parameters: JSONObject = JSONObject(),
    val enabled: Boolean = true,
    val downloads: Int = 0,
)

enum class CapabilityType { SKILL, MCP, API, TOOL }
enum class CapabilitySource { BUILTIN, CLOUD, RUNTIME, IMPORT }

// ToolRegistry.ToolDef → Capability 转换 (兼容)
fun ToolDef.toCapability(): Capability = Capability(
    id = name, type = "tool", source = source,
    name = name, description = description, parameters = parameters
)
```

### 2.3 CapabilityProvider

```kotlin
// capability/CapabilityProvider.kt
interface CapabilityProvider {
    suspend fun load(): List<Capability>
}

object BuiltinProvider : CapabilityProvider {
    override suspend fun load(): List<Capability> =
        BuiltinTools.map { it.toCapability() }
}

object CloudProvider : CapabilityProvider {
    override suspend fun load(): List<Capability> {
        // 调 /admin/client/mcp/list + /admin/client/skills/list
        val mcp = fetch("${backend}/admin/client/mcp/list")
        val skills = fetch("${backend}/admin/client/skills/list")
        return (mcp + skills).map { parseCapability(it) }
    }
}

class ImportProvider(private val ctx: Context) : CapabilityProvider {
    override suspend fun load(): List<Capability> {
        val plugins = File("/sdcard/MBclaw/plugins").listFiles() ?: emptyArray()
        val skills = File("/sdcard/MBclaw/skills").listFiles() ?: emptyArray()
        return (plugins + skills).mapNotNull { parseManifest(it) }
    }
}
```

### 2.4 ContextGovernor (APK版)

```kotlin
// agent/ContextGovernor.kt
class ContextGovernor(
    private val modelLimit: Int = 4096,  // 模型context上限
    private val mode: RuntimeMode = RuntimeMode.LITE_APK
) {
    companion object {
        const val NORMAL = 0.6f
        const val SOFT = 0.7f   // Lite模式更早触发
        const val HARD = 0.85f
    }

    fun estimateTokens(text: String): Int =
        (text.length / 3.5).toInt()  // 简化估算

    fun shouldCompress(conversation: List<ChatMessage>): CompressAction {
        val tokens = conversation.sumOf { estimateTokens(it.content) }
        val ratio = tokens.toFloat() / modelLimit
        return when {
            ratio < NORMAL -> CompressAction.NONE
            ratio < SOFT -> CompressAction.SOFT
            ratio < HARD -> CompressAction.HARD
            else -> CompressAction.REJECT
        }
    }

    fun buildContext(raw: List<ChatMessage>, current: String): List<ChatMessage> {
        val action = shouldCompress(raw)
        return when (action) {
            CompressAction.NONE -> raw.takeLast(20) + ChatMessage("user", current)
            CompressAction.SOFT -> {
                val summary = MemoryCompressor.softCompress(raw) // 本地小模型
                listOf(ChatMessage("system", summary)) + raw.takeLast(10) + ChatMessage("user", current)
            }
            CompressAction.HARD -> {
                val structured = MemoryCompressor.hardCompress(raw)
                listOf(ChatMessage("system", structured)) + raw.takeLast(5) + ChatMessage("user", current)
            }
            CompressAction.REJECT -> raw.takeLast(5) + ChatMessage("user", current)
        }
    }
}

enum class CompressAction { NONE, SOFT, HARD, REJECT }
```

### 2.5 DualRuntimeManager (APK版)

```kotlin
// agent/DualRuntimeManager.kt
enum class RuntimeMode {
    LITE_APK,    // 60% — 本地小模型压缩, context=4K, 关闭embedding
    MOTHER_FULL  // 100% — 云端LLM压缩, context=128K, 全部tool
}

object DualRuntimeManager {
    var mode: RuntimeMode by mutableStateOf(RuntimeMode.LITE_APK)

    val contextLimit: Int get() = if (mode == RuntimeMode.LITE_APK) 4096 else 128000
    val compressTrigger: Float get() = if (mode == RuntimeMode.LITE_APK) 0.7f else 0.8f
    val enableEmbedding: Boolean get() = mode == RuntimeMode.MOTHER_FULL

    val allowedTools: List<String> get() {
        if (mode == RuntimeMode.MOTHER_FULL) return emptyList() // 全部允许
        return listOf(
            "toggle_wifi", "send_sms", "click_at", "input_text",
            "open_app", "take_screenshot", "search_memory", ...
        )
    }

    fun switchToFullMode(settings: UserSettings) {
        if (settings.utopiaEnabled) {
            mode = RuntimeMode.MOTHER_FULL
        }
    }
}
```

### 2.6 ToolsScreen (Flow绑定版)

```kotlin
// ui/screens/ToolsScreen.kt
@Composable
fun ToolsScreen() {
    // ★ v6: 绑定CapabilityRegistry.flow, 自动刷新
    val tools by CapabilityRegistry.flow.collectAsState()

    // 分类 (不变)
    val cats = remember(tools) { categorize(tools) }
    val current = cats.find { it.first == selectedCat }?.second ?: tools

    // ... UI渲染 (不变, 使用tools替代oldToolRegistry.ALL)
    LazyColumn {
        items(current) { cap ->
            ToolCard(
                name = cap.name,
                description = cap.description,
                type = cap.type,        // ★ 显示 Capability 类型
                source = cap.source,    // ★ 显示来源
            )
        }
    }
}
```

### 2.7 SettingsPage (RuntimeMode切换)

```kotlin
// ui/screens/SettingsPage.kt — 新增开关
SectionLabel("运行模式")
Card {
    SwitchRow(
        "🌐 乌托邦 (母体模式)",
        "启用后context=128K, 全部工具可用, AI能力提升至100%",
        checked = utopia,
        { utopia = it; s.utopiaEnabled = it
          if (it) DualRuntimeManager.switchToFullMode(s) }
    )
}
```

---

## 三、数据流 (完整链路)

```
[QQ消息] → QQBotAdapter._on_private_msg(raw)
    → MessageNormalizer.normalize("qq", raw) → StandardMessage
    → MessageRouter.send_to_agent(msg)
        → asyncio.Queue.put(msg)
        → RawStore.append(code, msg)           # 写raw (不可变)
        → MotherAgent.process(msg)             # FIFO顺序
            → ContextGovernor.build(code, msg.message)
                → MemoryCompressor (if needed)
            → CapabilityRegistry.list()        # 获取tools
            → LLM.chat(context + tools)
            → IndexStore.update(code, msg, reply)
            → ResponseDispatcher.dispatch(msg, reply)
                → format_by_channel("qq", reply)
                → QQBotAdapter.send(user_id, formatted)
        → future.set_result(reply)
    → 返回给 Router → 返回给 Adapter
```

---

## 四、改动范围总结

| 层 | 新建文件 | 修改文件 |
|----|---------|---------|
| Server | `gateway/normalize.py` `gateway/router.py` `gateway/dispatcher.py` `gateway/adapters/*.py` `capabilities/registry.py` `capabilities/models.py` `agent/mother.py` `agent/context_governor.py` `agent/memory/compressor.py` `agent/memory/raw_store.py` `agent/memory/index_store.py` `agent/runtime.py` `data/message_protocol.py` | `agent.py` `api.py` `main.py` `tools.py` |
| APK | `capability/CapabilityRegistry.kt` `capability/CapabilityModels.kt` `capability/CapabilityProvider.kt` `agent/ContextGovernor.kt` `agent/DualRuntimeManager.kt` | `ToolRegistry.kt` `ToolsScreen.kt` `SettingsPage.kt` |
