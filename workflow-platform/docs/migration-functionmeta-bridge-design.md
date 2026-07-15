# 迁移设计文档：FunctionMeta 兼容层 + wf_function 元数据扩展

> 目标模块：`fluxion-core`（兼容层）、`fluxion-admin`（`WfFunction` 实体/表、`JsonMapperHelper`、`WfFunctionService`）、`fluxion-builtin` / `fluxion-redis` / `fluxion-script`（元数据种子化）
> 约束：**`fluxion-core` 不得反向依赖 `fluxion-admin`**（admin → core，单向）
> 本文为设计（RESEARCH + PROPOSAL），**不修改任何源文件**。

---

## 0. 关键事实与约束确认（基于实际代码）

| 项 | 现状（源码依据） |
|----|------------------|
| `FunctionMeta` 字段 | `fluxion-core/.../value/FunctionMeta.kt`：`name, description, inputSchema, outputSchema, inputSchemaRef, outputSchemaRef, domain, descriptions` |
| 内置函数注册路径 | `BuiltinFunctionRegistrar`（`fluxion-function/builtin/.../config/BuiltinConfig.kt`）遍历 `List<BuiltinFunction>`，调用 `function.meta()` 拿 `FunctionMeta` **直接注册**，**运行时无 DB 读** |
| 脚本/外部函数注册路径 | `FunctionConfigApplier`（`fluxion-script/.../FunctionConfigApplier.kt`）从 `FunctionConfigSnapshot` 现场 `FunctionMeta.builder(...)` 构造 |
| 快照来源 | `WfFunctionService.toSnapshot()` 经 `JsonMapperHelper.functionToConfig()` 从 `wf_function.config` JSON 提取 `description/paramSchema/outputSchema/scriptBody/className/publishTarget` |
| `wf_function` 字段 | `WfFunction` 实体：`functionName, scope, appGroup, sourceRef, functionType, domain, config(JSON), status` |
| Schema SSOT | `wf_schema` 表 + V9 迁移已用 `INSERT IGNORE` 种子化内置函数 Schema；`BuiltinSchemaRefs`（`fluxion-function/builtin/.../meta/BuiltinFunctionMetas.kt`）持有 `name → wf_schema.schema_name` 引用 |
| **两类"category"必须区分** | ① `FunctionCategory` 枚举（admin 生成模型，`BUILTIN/CUSTOM/SCRIPT/EXTERNAL`）是**函数类型**，与前端 `FUNCTION_GROUPS` 的 `flow-control/data-access/...` **完全不同**；② 迁移要求的 `category` 指**前端能力分组 key** |
| 前端分组 key | `workflow-admin-ui/src/constants/functionDisplay.ts` `FUNCTION_GROUPS`：`flow-control, data-access, data-processing, validation, response, script, custom, external, other` |

> ⚠️ **核心设计决策**：新增的 `category` 列是**前端能力分组**（flow-control 等），与现有 `functionType`/`FunctionCategory` 正交，二者并存、互不影响。避免把新概念塞进既有的 `FunctionCategory` 枚举（那会污染函数类型语义）。

---

## 1. `FunctionMetaBridge` 兼容层接口（置于 `fluxion-core`）

### 1.1 设计原则

- 接口定义在 `fluxion-core`（如 `com.fluxion.core.value.FunctionMetaBridge` 及其输入契约 `FunctionMeta`）。
- **只依赖 `fluxion-core` 可见的纯类型**（`Map<String, Any?>`、字符串、`FunctionMeta` 本身），**不 import 任何 `fluxion-admin` 类** → 满足单向依赖。
- `fluxion-admin` 侧提供一个适配器（`WfFunctionMetaBridgeAdapter`）实现该接口，把 `WfFunction` / `FunctionConfigSnapshot` 转成核心能消费的 `Map`/`FunctionMeta` 再委托给核心的默认实现。这样**字段映射规则（config key → FunctionMeta 字段）统一收敛在 core**，admin 只做"实体 → 通用 Map"的搬运。

### 1.2 核心接口契约（`fluxion-core`）

```kotlin
// fluxion-core/src/main/kotlin/com/fluxion/core/value/FunctionMetaBridge.kt

package com.fluxion.core.value

/**
 * FunctionMeta 兼容层 —— 从「配置型数据源」（DB config JSON / 函数配置快照）
 * 构造 [FunctionMeta]，使迁移后运行时拿到的 FunctionMeta 与代码常量 builder 构造的完全一致。
 *
 * 定义在 fluxion-core，只依赖 Map<String,Any?> 等纯类型，不反向依赖 fluxion-admin。
 */
interface FunctionMetaBridge {

    /**
     * 从统一 config Map 构造 FunctionMeta。
     * @param name 函数引用名（必填，作为 FunctionMeta.name）
     * @param config wf_function.config 解析后的 Map（可能为 null → 返回最小 meta）
     */
    fun fromConfig(name: String, config: Map<String, Any?>?): FunctionMeta

    /**
     * 从函数配置快照构造 FunctionMeta（Worker 侧 / Admin 推送路径共用）。
     */
    fun fromSnapshot(snapshot: FunctionMeta): FunctionMeta
}

/**
 * 核心侧定义的最小快照契约（admin 的 [FunctionConfigSnapshot] 已实现等价字段，
 * 此处用独立接口避免 core 依赖 admin；admin 适配器做字段拷贝即可）。
 */
interface FunctionMeta {
    val functionName: String
    val functionType: String
    val description: String?
    val paramSchema: String?      // JSON 字符串或已序列化的 Schema
    val outputSchema: String?
    val inputSchemaRef: String?   // 新增：指向 wf_schema.schema_name
    val outputSchemaRef: String?  // 新增
    val category: String?         // 新增：前端能力分组 key
    val descriptions: Map<String, String>? // 新增：多语言
    val domain: String?
}
```

### 1.3 字段映射规则（核心实现 `DefaultFunctionMetaBridge`）

| FunctionMeta 字段 | 来自 `config` JSON key | 来自 `FunctionMeta` 字段 | 来自 `FunctionConfigSnapshot` 现状 |
|---|---|---|---|
| `name` | （不入 config，由参数传入） | `functionName` | `snapshot.functionName` |
| `description` | `description` | `description` | `snapshot.description`（已有，经 `JsonMapperHelper.functionConfigToDescription`） |
| `inputSchema` | `paramSchema`（内嵌 JSON） | `paramSchema`（fallback） | `snapshot.paramSchema`（已有） |
| `outputSchema` | `outputSchema`（内嵌 JSON） | `outputSchema`（fallback） | `snapshot.outputSchema`（已有） |
| `inputSchemaRef` | `paramSchemaRef` | `inputSchemaRef` | **新增**（目前快照无此字段） |
| `outputSchemaRef` | `outputSchemaRef` | `outputSchemaRef` | **新增** |
| `domain` | `domain` | `domain` | `entity.domain`（快照目前未带，见 §3） |
| `descriptions` | `descriptions`（Map<locale,String>） | `descriptions` | **新增** |

**解析优先级（每个 Schema 字段独立）**：
- `inputSchemaRef` 优先（非空则 `FunctionMeta.inputSchemaRef = ref`，`inputSchema` 置 null，运行时由 `SchemaManager` 按 ref 解析，与 `BuiltinSchemaRefs` 既有行为一致）。
- 仅当 `inputSchemaRef` 为空时，回退到内嵌 `paramSchema` → `inputSchema`（向后兼容现有 SCRIPT/EXTERNAL 内嵌 JSON）。
- `outputSchema`/`outputSchemaRef` 同理。

**示例核心实现骨架**：

```kotlin
class DefaultFunctionMetaBridge : FunctionMetaBridge {

    override fun fromConfig(name: String, config: Map<String, Any?>?): FunctionMeta {
        if (config == null) return FunctionMeta.of(name)
        val b = FunctionMeta.builder(name)
        (config["description"] as? String)?.let { b.description(it) }
        (config["domain"] as? String)?.let { b.domain(it) }
        (config["descriptions"] as? Map<*, *>)?.let {
            b.descriptions(it.mapNotNull { (k, v) -> (k as? String)?.to(v as? String) })
        }
        // Schema：ref 优先，内嵌 fallback
        (config["paramSchemaRef"] as? String)?.let { b.paramSchemaRef(it) }
            ?: (config["paramSchema"])?.let { b.paramSchema(it) }
        (config["outputSchemaRef"] as? String)?.let { b.outputSchemaRef(it) }
            ?: (config["outputSchema"])?.let { b.outputSchema(it) }
        return b.build()
    }

    override fun fromSnapshot(snapshot: FunctionMeta): FunctionMeta {
        val b = FunctionMeta.builder(snapshot.functionName)
        snapshot.description?.let { b.description(it) }
        snapshot.domain?.let { b.domain(it) }
        snapshot.descriptions?.let { b.descriptions(it) }
        snapshot.inputSchemaRef?.let { b.paramSchemaRef(it) }
            ?: snapshot.paramSchema?.let { b.paramSchema(it) }
        snapshot.outputSchemaRef?.let { b.outputSchemaRef(it) }
            ?: snapshot.outputSchema?.let { b.outputSchema(it) }
        return b.build()
    }
}
```

### 1.4 admin 侧适配器（`fluxion-admin`，**不破坏边界**）

`fluxion-admin` 新增 `WfFunctionMetaBridgeAdapter implements FunctionMeta` 或 直接调用 `DefaultFunctionMetaBridge`：

- 对 `WfFunction`：用 `JsonMapperHelper.functionToConfig(entity)` 得 `Map`，读取新 key（`paramSchemaRef`/`outputSchemaRef`/`descriptions`/`category`），调用 `bridge.fromConfig(entity.functionName, config)`。
- 对 `FunctionConfigSnapshot`：因 `fluxion-adapter-spi` 的 `FunctionConfigSnapshot` 是 `fluxion-core` 可见的（在 `fluxion-adapter-spi`，被 core/runtime 依赖）——**实际上 `FunctionConfigSnapshot` 已在 core 可达依赖链上**，可直接扩展其字段（见 §3），免去 `FunctionMeta` 拷贝；`FunctionMeta` 仅作为面向"未来不依赖 adapter-spi 的纯 core 场景"的可选抽象。

```kotlin
// fluxion-admin/.../mapper/WfFunctionMetaBridge.kt
@Component
class WfFunctionMetaBridge(private val delegate: FunctionMetaBridge) {
    fun fromEntity(entity: WfFunction): FunctionMeta {
        val config = jsonMapperHelper.functionToConfig(entity) ?: emptyMap()
        return delegate.fromConfig(entity.functionName, config)
    }
}
```

> 注意：`FunctionMetaBridge` 接口与 `DefaultFunctionMetaBridge` 在 `fluxion-core`；admin 的 `@Component` 适配器只是把 `WfFunction` 适配成 core 能理解的形式。**core 永不 import admin 类** —— 边界安全。

---

## 2. `WfFunction` 实体 / `wf_function` 表扩展

### 2.1 字段归属决策（列 vs config JSON）

| 新需求 | 决策 | 理由 |
|--------|------|------|
| `category`（前端能力分组） | **独立列** `category VARCHAR(32)` | 需要被列表查询 / 前端按组过滤 / 索引；放 JSON 无法高效查询，且 `FUNCTION_GROUPS` 是封闭枚举值集 |
| `descriptions`（`{locale:text}`） | **放 `config` JSON**（key `descriptions`） | 多语言 Map 无固定结构、无需查询过滤；复用既有"新增配置项无需 DDL"的 config 聚合模式（`JsonMapperHelper` 注释已明确此约定） |
| `paramSchemaRef` | **放 `config` JSON**（key `paramSchemaRef`） | 与既有 `paramSchema` 同源聚合；大多数函数无此值（仅内置函数用 ref），放 JSON 避免大量 NULL 列 |
| `outputSchemaRef` | **放 `config` JSON**（key `outputSchemaRef`） | 同上 |

**Flyway 友好性**：仅 `category` 需要 `ALTER TABLE`，且是 `NULLABLE` 加列（MySQL 8 / 主流 PG 均为瞬时 Online DDL，`NULLABLE` 不加默认值不锁表）。`descriptions`/`*SchemaRef` 纯 JSON 内扩展，**零 DDL**。→ 迁移脚本极小、可重入（无破坏性变更）。

### 2.2 实体 DDL Diff

```sql
-- V10__extend_wf_function_category.sql
ALTER TABLE wf_function
    ADD COLUMN category VARCHAR(32) NULL
    COMMENT '前端能力分组 key，对应 FUNCTION_GROUPS：flow-control/data-access/data-processing/validation/response/script/custom/external/other';
-- 可选索引（仅当前端需要后端按 category 过滤分页时增加；建议异步/低峰执行）
-- CREATE INDEX ix_wf_function_category ON wf_function (category);
```

> 若决定 `category` 必填（所有函数应有分组），用 `ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'other'`（default 兜底到 `FUNCTION_GROUPS` 的 `other`），再在种子迁移里回填真实值。推荐**先 NULLABLE + 种子回填**，降低高风险 ALTER 风险。

### 2.3 实体类 Diff（`fluxion-admin/.../entity/WfFunction.kt`）

```kotlin
    /** 前端能力分组 key（列：`category`），对应 FUNCTION_GROUPS。与 functionType 正交。 */
    @Column(name = "category", length = 32)
    var category: String? = null
```

`config` JSON 内新增约定 key（不改实体，仅约定）：
```jsonc
{
  "description": "...",
  "paramSchema": "{...}",          // 既有（内嵌 fallback）
  "outputSchema": "{...}",         // 既有
  "paramSchemaRef": "builtin:dbExecute:param",   // 新增
  "outputSchemaRef": "builtin:dbExecute:output", // 新增
  "descriptions": { "zh": "...", "en": "..." },  // 新增（多语言）
  "domain": "db"                   // 既有（部分函数）
}
```

### 2.4 `JsonMapperHelper` 需新增的读取函数（`fluxion-admin`）

```kotlin
fun functionConfigToParamSchemaRef(config: Map<String, Any>?): String? =
    config?.get("paramSchemaRef")?.toString()

fun functionConfigToOutputSchemaRef(config: Map<String, Any>?): String? =
    config?.get("outputSchemaRef")?.toString()

@Suppress("UNCHECKED_CAST")
fun functionConfigToDescriptions(config: Map<String, Any>?): Map<String, String>? =
    (config?.get("descriptions") as? Map<String, Any>)?.mapNotNull { (k, v) ->
        (k as? String)?.to(v?.toString())
    }?.toMap()

fun functionConfigToCategory(config: Map<String, Any>?): String? =
    config?.get("category")?.toString() ?: (config?.get("categoryKey")?.toString())
```

> `category` 主要走实体列；`functionConfigToCategory` 仅作 config JSON 内冗余/兼容读取（若未来也允许 JSON 覆盖）。

---

## 3. `FunctionRegistry` 加载 / 构造变化

### 3.1 两条加载路径的迁移后行为

**路径 A — 内置函数（无 DB 读，保持不变 / 平滑过渡）**
- 现状：`BuiltinFunctionRegistrar` 调 `function.meta()`（返回 `BuiltinFunctionMetas.*` 常量）。
- 迁移选项（见 §3.3）：**首选保留 `meta()` 现场构造**，但让 `meta()` 在桥接可用时优先从 DB/config 取（双层）。**运行时零 DB 依赖**由 `fluxion-core` 不依赖 admin 保证 —— 内置函数注册发生在 `fluxion-builtin`（依赖 core，不依赖 admin），因此**核心运行时根本不会去读 `wf_function`**。

**路径 B — SCRIPT / EXTERNAL（已有快照路径）**
- `FunctionConfigApplier.applySnapshot()` 当前现场 `FunctionMeta.builder(...)`。迁移后改为调用 `bridge.fromSnapshot(snapshot)`（或 `bridge.fromConfig`），自动带上 `paramSchemaRef`/`outputSchemaRef`/`descriptions`/`domain`。
- **需扩展 `FunctionConfigSnapshot`（`fluxion-adapter-spi`，core 可见）字段**：

```kotlin
// fluxion-adapter-spi/.../config/Config.kt —— FunctionConfigSnapshot 新增：
    val inputSchemaRef: String? = null,
    val outputSchemaRef: String? = null,
    val descriptions: Map<String, String>? = null,
    val category: String? = null,
```

- 并扩展 `WfFunctionService.toSnapshot()` 把这些字段从 config/实体列填入：

```kotlin
return FunctionConfigSnapshot(
    functionName = entity.functionName,
    functionType = entity.functionType,
    scriptBody = ...,
    className = ...,
    endpoint = null,
    paramSchema = jsonMapperHelper.functionConfigToParamSchema(config),
    outputSchema = jsonMapperHelper.functionConfigToOutputSchema(config),
    inputSchemaRef = jsonMapperHelper.functionConfigToParamSchemaRef(config),   // 新增
    outputSchemaRef = jsonMapperHelper.functionConfigToOutputSchemaRef(config), // 新增
    description = jsonMapperHelper.functionConfigToDescription(config),
    descriptions = jsonMapperHelper.functionConfigToDescriptions(config),       // 新增
    category = entity.category,                                                  // 新增（实体列）
    config = config,
    enabled = entity.status == FunctionStatus.ACTIVE,
    targetGroups = targetGroups
)
```

- `FunctionConfigApplier` 相应改为：

```kotlin
val meta = functionMetaBridge.fromSnapshot(snapshot) // 替换原内联 builder
```

（`functionMetaBridge` 注入 `DefaultFunctionMetaBridge`，该 bean 可在 `fluxion-core:spring-boot` 自动配置，对 runtime/script-engine 可见。）

### 3.2 `FunctionRegistry` 自身是否需要改？

**不需要改 `FunctionRegistry` 的 API**。它仍然是 `register(name, meta, function)`。变化只发生在"meta 从哪来"：
- 内置：仍由 `meta()` 提供（§3.3 决定其来源）。
- 热发布：由 `FunctionConfigApplier` 经 bridge 构造后 `register`。

> `FunctionRegistry` 保持纯 Kotlin、零框架依赖 —— 满足"core 不反向依赖 admin"。

### 3.3 内置函数 `BuiltinFunctionMetas` 常量：删除还是保留？

**建议：分阶段迁移，保留 `meta()` 现场构造作为"代码兜底"，新增"DB/桥接优先"层。**

**理由 / 风险**：
- `BuiltinFunctionMetas.*` 当前被 10+ 个函数类通过 `override fun meta() = BuiltinFunctionMetas.CACHE_GET` 直接引用（`CacheFunctions.kt`、`DbExecuteFunction.kt` 等）。**直接删除会破坏编译 + 单测**（`FunctionRegistryTest`、`DbExecuteWorkflowCompilerTest` 等均依赖 `meta()`）。
- `RedisFunctionMetas.REDIS_COMMAND` 的 `paramSchema` 是**运行时动态生成的大 JSON**（基于 Redis 命令签名），**没有也不宜落 `wf_schema`**（命令集可变、schema 含 `x-commands` 动态元数据）。→ Redis 必须**保留代码常量**，不受本次迁移影响。
- `ScriptEngineFunctionMetas.GROOVY_SCRIPT` 的 `paramSchema` 是小段内嵌 JSON；可种子化也可保留。

**落地策略**：

| 模块 | 常量处理 | 种子化（wf_function via V10） | 运行时 meta 来源 |
|------|----------|-------------------------------|------------------|
| `fluxion-builtin` (`BuiltinFunctionMetas`) | **保留**（作为 fallback / dev 无 DB 场景） | V10 种子化 `wf_function`（scope=PLATFORM, functionType=BUILTIN），带 `paramSchemaRef`/`descriptions`/`category`/`domain` | Admin 侧 `WfFunctionService.loadAllEnabledSnapshots()` 已**显式排除 BUILTIN**（`!functionType.equals("BUILTIN")`），所以 Admin→Worker 推送路径**本就不推送内置函数**；内置函数始终由代码 `meta()` 注册。→ **内置 FunctionMeta 仍走代码常量，DB 种子仅用于"函数管理列表展示 / 前端分组 / 文档"，不参与运行时执行 meta** |
| `fluxion-redis` (`RedisFunctionMetas`) | **保留**（动态 schema 不适合落库） | 可选种子化（仅 `category=other`/`data-access`、无 schema ref） | 代码常量 |
| `fluxion-script` (`ScriptEngineFunctionMetas`) | **保留** | 可选种子化 `builtin:groovyScript` | 代码常量 |

> **关键架构澄清**：内置函数的 `FunctionMeta` 在 Worker 运行时**始终由代码 `meta()` 提供**，不经过 `wf_function` 读取。因此 "core 不反向依赖 admin / 运行时无 DB 读" 的约束天然满足，无需让 `fluxion-core` 去读库。`wf_function` 种子化解决的是**Admin 管理面（函数列表、前端分组、文档、i18n 描述持久化）单一数据源**问题，与执行面解耦。

**若未来想让内置 meta 也完全数据驱动**，可在 `fluxion-admin` 提供一个 `AdminBuiltinMetaProvider`（admin 侧），从 `wf_function` 读后通过 bridge 转 `FunctionMeta` 供管理 API 使用；**core 执行面不感知**。

### 3.4 `WfFunctionService` 过滤逻辑注意点

现有 `loadAllEnabledSnapshots()` / `loadEnabledSnapshots()` 均 `filterNot { it.functionType.equals("BUILTIN") }`。迁移后：
- 内置函数**继续排除**在推送快照外（执行面由代码常量）。
- 但 Admin 管理列表需要看到内置函数 → 新增独立查询 `loadBuiltinMetaList()`（含 category/descriptions），**不参与 Worker 推送**。建议 Admin 控制器对 PLATFORM+BUILTIN 直接读 `wf_function` 种子行 + bridge 转换，对 SCRIPT/EXTERNAL 走既有快照。

---

## 4. 种子策略（V10 迁移）

### 4.1 种子内容（与 V9 `INSERT IGNORE` 同模式）

- `wf_function` 种子：**仅 PLATFORM 作用域的内置函数**（`builtin:*` 共 19 个，含 redisCommand、groovyScript），`functionType=BUILTIN`，`status=ACTIVE`。
- 每个种子的 `config` JSON 携带：
  - `paramSchemaRef` / `outputSchemaRef`（指向 V9 已种子的 `wf_schema.schema_name`，如 `builtin:dbExecute:param`）；无 output 的函数（多数）仅 `paramSchemaRef`。
  - `descriptions`：`{"zh": "...", "en": "..."}`（从 `BuiltinFunctionMetas` 常量现有 `descriptions` 抄录）。
  - `domain`：与 `BuiltinFunctionMetas` 一致（`db`/`http`/`redis`/`cache`/`common`/`script`/`mq`）。
- `category` 列：按前端 `BUILTIN_NAME_TO_GROUP` 映射回填（`flow-control`/`data-access`/`data-processing`/`validation`/`response`/`script`）。

### 4.2 V10 迁移脚本骨架

```sql
-- V10__seed_builtin_functions.sql
-- 内置函数元数据种子化（管理面单一数据源）。
-- 使用 INSERT IGNORE：重复执行安全，不覆盖用户可能修改的行。

INSERT IGNORE INTO wf_function
  (function_name, scope, app_group, source_ref, function_type, domain, config, status, category, created_at, updated_at)
VALUES
-- dbExecute（带 input+output ref）
('builtin:dbExecute','PLATFORM',NULL,NULL,'BUILTIN','db',
 '{"description":"执行数据库 SQL","paramSchemaRef":"builtin:dbExecute:param","outputSchemaRef":"builtin:dbExecute:output","descriptions":{"zh":"执行数据库 SQL（SELECT/INSERT/UPDATE/DELETE）","en":"Execute database SQL"}}',
 'ACTIVE','data-access', NOW(), NOW()),
-- redisCommand（保留代码动态 schema，仅 category + domain）
('builtin:redisCommand','PLATFORM',NULL,NULL,'BUILTIN','redis',
 '{"description":"通用 Redis 命令执行器","descriptions":{"zh":"通用 Redis 命令执行器","en":"Generic Redis command executor"}}',
 'ACTIVE','data-access', NOW(), NOW()),
-- groovyScript
('builtin:groovyScript','PLATFORM',NULL,NULL,'BUILTIN','script',
 '{"description":"执行 Groovy 脚本","descriptions":{"zh":"执行 Groovy 脚本","en":"Execute Groovy script"}}',
 'ACTIVE','script', NOW(), NOW()),
-- ... 其余 16 个 builtin:* 同理（paramSchemaRef 指向 V9 schema，category 按 BUILTIN_NAME_TO_GROUP）
;
```

> 注：`created_at`/`updated_at` 取决于 `BaseEntity` 是否有审计字段（需确认；若 JPA `@MappedSuperclass` 自动填充则可省略这两列）。

### 4.3 种子与代码常量的"真相来源"边界（再次强调）

| 关注面 | 真相来源 |
|--------|----------|
| Worker 执行时 FunctionMeta（schema 校验/路由） | **代码 `meta()` 常量**（core 不读库） |
| Admin 函数管理列表 / 前端分组 / i18n 描述 | **`wf_function` 种子 + bridge**（数据驱动，可运维修改） |
| 实际入参/出参 JSON Schema 内容 | **`wf_schema`**（V9，SSOT，不被代码 hardcode） |

三者通过 `schema_name` / `functionName` 关联，互不冲突。

---

## 5. 架构风险与缓解

### 5.1 循环依赖风险
- **风险**：若把 `FunctionMetaBridge` 实现或 `WfFunction` 读取放进 `fluxion-core`，会反向引入 admin 依赖。
- **现状安全**：core 当前只依赖 `fluxion-adapter-spi`（其 `FunctionConfigSnapshot` 已是 core 可见）。本设计把接口 + 默认实现放 core，admin 放适配器 → **单向依赖不变**。
- **缓解**：CI 增加模块依赖断言（如 Gradle `module-info` / 架构测试，`fluxion-core` 的 `build.gradle.kts` 不允许出现 `fluxion-admin` 依赖）。

### 5.2 测试破坏风险
- **风险点**：
  1. 删除 `BuiltinFunctionMetas.*` 常量 → 10+ 函数类 + `FunctionRegistryTest`、`DbExecuteWorkflowCompilerTest`、`WfFunctionControllerTest` 编译失败。
  2. `FunctionConfigSnapshot` 新增字段为**非破坏性**（带默认值 `= null`）→ 既有构造调用不受影响。
  3. `WfFunction` 新增 `category` 字段带默认值 null → 既有 JPA 测试/反序列化不受影响。
- **缓解**：**不删除** `BuiltinFunctionMetas` 常量（保留为 fallback）；`FunctionConfigSnapshot`/`WfFunction` 仅**加字段不改签名**；为 `DefaultFunctionMetaBridge` 在 `fluxion-core/src/test` 补单测（config→meta 映射、ref 优先、空 config fallback）；为 `WfFunctionService.toSnapshot()` 补快照含新字段的断言。

### 5.3 运行时 DB 读取风险
- **风险**：若让 Worker 执行面在 `meta()` 里读 `wf_function`，会引入 core→admin 依赖 + 运行时 DB 耦合 + 冷启动延迟。
- **缓解**：明确 **Worker 执行面只用代码 `meta()` 常量**（§3.3）。`wf_function` 读取仅发生在 **Admin 管理面**（`fluxion-admin`，本就依赖 DB）。`FunctionConfigApplier` 走的是推送快照（已在内存），不读库。

### 5.4 数据一致性风险（代码常量 vs DB 种子漂移）
- **风险**：`BuiltinFunctionMetas` 常量与 `wf_function` 种子 `descriptions`/`category` 可能不一致（改代码忘改 SQL）。
- **缓解**：
  1. 接受"执行面以代码为准、管理面以 DB 为准"的双源定位（二者用途不同，允许适度独立）。
  2. 增加 Admin 启动自检：`WfFunctionService` 启动时比对 `BuiltinFunctionMetas` 常量集合与 `wf_function`(BUILTIN) 行集合，缺则 WARN 日志（不阻断）。
  3. 将 `descriptions`/`category` 视为**可运维覆盖项**（DB 优先于代码默认值用于管理展示）。

### 5.5 Schema ref 解析缺失风险
- **风险**：`paramSchemaRef` 指向的 `wf_schema.schema_name` 若不存在（V9 漏种子 / 环境差异），运行时 `SchemaManager` 解析失败。
- **缓解**：V10 种子严格复用 V9 已存在的 `schema_name`（同一套 `BuiltinSchemaRefs`）；`SchemaManager` 对缺失 ref 应有降级（回退到 `inputSchema` 或跳过校验并打 WARN），与现有 `inputSchemaRef` 行为一致。

### 5.6 Flyway 顺序 / 重入风险
- **风险**：V10 依赖 V9 的 `schema_name` 存在；低版本库直接跳到 V10 会 FK/引用悬空（虽无物理 FK，但逻辑引用）。
- **缓解**：V10 仅 `INSERT IGNORE` 引用 V9 的 `schema_name`（字符串匹配，无外键约束），即使 V9 未跑，V10 也只插入 function 行、ref 暂未被解析（WARN 而非 ERROR）；Flyway 顺序由版本号保证 V9 < V10。

### 5.7 Redis 动态 schema 特例
- **风险**：`RedisFunctionMetas.REDIS_COMMAND.paramSchema` 是运行时基于命令签名生成的大 JSON，无法 / 不应落 `wf_schema`。
- **缓解**：Redis 函数 **保持代码常量**，`wf_function` 种子仅填 `category=other`/`data-access` + `domain=redis`，**不带 `paramSchemaRef`** → bridge 走"ref 为空 → 回退内嵌"分支，但内嵌也空（因代码常量也没硬编码完整 schema，是动态的）。→ 确认 `RedisCommandFunction` 执行时不依赖 `FunctionMeta.inputSchema` 做校验（动态 schema 在前端侧消费，后端执行靠 `RedisClientAdapter` 解析）。**建议**：Redis 函数的 `paramSchema` 继续由代码常量在 `meta()` 返回（动态生成），DB 种子不重复存储。

---

## 6. 实施步骤（建议顺序）

1. **core**：新增 `FunctionMetaBridge` 接口 + `DefaultFunctionMetaBridge` 实现 + `FunctionMeta` 接口（`fluxion-core/.../value/`）。
2. **adapter-spi**：`FunctionConfigSnapshot` 加 4 个 nullable 字段（`inputSchemaRef`/`outputSchemaRef`/`descriptions`/`category`）。
3. **admin 实体/表**：`WfFunction` 加 `category` 列；写 `V10__extend_wf_function_category.sql`（ALTER ADD COLUMN）。
4. **admin mapper**：`JsonMapperHelper` 加 `functionConfigToParamSchemaRef/OutputSchemaRef/Descriptions/Category`。
5. **admin service**：`WfFunctionService.toSnapshot()` 填充新字段；新增 `loadBuiltinMetaList()` 供管理面。
6. **admin 适配器**：`WfFunctionMetaBridge`（`@Component`）委托 `FunctionMetaBridge`。
7. **script-engine**：`FunctionConfigApplier` 改用 `bridge.fromSnapshot(snapshot)`。
8. **admin 种子**：`V10__seed_builtin_functions.sql`（`INSERT IGNORE` PLATFORM 内置函数，带 ref/descriptions/category/domain）。
9. **core-spring-boot**：注册 `DefaultFunctionMetaBridge` bean（供 runtime / script-engine 注入）。
10. **测试**：`DefaultFunctionMetaBridge` 单测；`toSnapshot` 快照含新字段断言；启动自检 WARN 日志。
11. **CI 护栏**：`fluxion-core` 依赖断言禁止 `fluxion-admin`。

---

## 7. 涉及文件清单（引用实际路径）

| 文件 | 变更类型 |
|------|----------|
| `fluxion-core/src/main/kotlin/com/fluxion/core/value/FunctionMeta.kt` | 不变（接口已支持 ref/descriptions） |
| `fluxion-core/src/main/kotlin/com/fluxion/core/value/FunctionMetaBridge.kt` | **新增**（接口 + `FunctionMeta`） |
| `fluxion-core/src/main/kotlin/com/fluxion/core/value/DefaultFunctionMetaBridge.kt` | **新增**（默认实现） |
| `fluxion-core/src/main/kotlin/com/fluxion/core/function/FunctionRegistry.kt` | 不变 |
| `fluxion-adapter-spi/src/main/kotlin/com/fluxion/adapter/spi/config/Config.kt` | `FunctionConfigSnapshot` 加 4 字段 |
| `fluxion-admin/src/main/kotlin/com/fluxion/admin/entity/WfFunction.kt` | 加 `category` 字段 |
| `fluxion-admin/src/main/resources/db/migration/V10__extend_wf_function_category.sql` | **新增**（ALTER） |
| `fluxion-admin/src/main/resources/db/migration/V10__seed_builtin_functions.sql` | **新增**（种子） |
| `fluxion-admin/src/main/kotlin/com/fluxion/admin/mapper/JsonMapperHelper.kt` | 加 4 个读取函数 |
| `fluxion-admin/src/main/kotlin/com/fluxion/admin/service/WfFunctionService.kt` | `toSnapshot` 填充新字段 + `loadBuiltinMetaList()` |
| `fluxion-admin/src/main/kotlin/com/fluxion/admin/mapper/WfFunctionMetaBridge.kt` | **新增**适配器 |
| `fluxion-script/core/src/main/kotlin/com/fluxion/script/config/FunctionConfigApplier.kt` | 改用 bridge |
| `fluxion-function/builtin/src/main/kotlin/com/fluxion/builtin/meta/BuiltinFunctionMetas.kt` | **保留**（fallback） |
| `fluxion-redis/core/src/main/kotlin/com/fluxion/redis/meta/RedisFunctionMetas.kt` | **保留**（动态 schema） |
| `fluxion-script/core/src/main/kotlin/com/fluxion/script/meta/ScriptEngineFunctionMetas.kt` | **保留** |
| `workflow-admin-ui/src/constants/functionDisplay.ts` | 不变（前端 key 已被 `category` 列对齐） |

---

## 8. 设计取舍总结（Alternatives Considered）

- **A. 把 `category`/`descriptions`/`*SchemaRef` 全放 config JSON（零 DDL）**：✗ 拒绝——`category` 需被列表查询/索引，JSON 内无法高效过滤；且前端 `FUNCTION_GROUPS` 是封闭枚举，值得独立列。
- **B. 把 `category` 塞进 `FunctionCategory` 枚举**：✗ 拒绝——该枚举是函数类型（BUILTIN/SCRIPT/...），语义正交，混用会污染 API 契约。
- **C. 删除 `BuiltinFunctionMetas` 常量，运行时从 `wf_function` 读 meta**：✗ 拒绝——违反"core 不依赖 admin" + 引入运行时 DB 耦合 + 破坏既有测试；且 Redis 动态 schema 无法落库。
- **D. 本方案（双源：执行面代码常量 + 管理面 DB 种子 + 兼容层 bridge）**：✓ 采纳——最小侵入、边界安全、Flyway 友好、保留运行时零 DB 依赖、测试兼容。
