# FunctionMeta 动态加载 SPI 重构 + fluxion-builtin 重构评估

> 目标：将 `FunctionMeta` 的加载方式从「硬编码 `BuiltinFunctionMetas`」改为「通过 SPI 从数据库 / 配置中心动态获取」（数据源由 SPI 实现决定），并参考 `fluxion-schema`（`SchemaManager` / `SchemaRegistry`）架构。
> 数据流向约定（用户确认）：**DB 始终写入，配置中心也发布**。

---

## 1. 现状与痛点

| 项 | 现状 |
|----|------|
| 内置函数元信息 | 硬编码在 `fluxion-function/builtin/.../meta/BuiltinFunctionMetas.kt`（`InlineMeta` 常量） |
| 执行路径 | `BuiltinFunctionRegistrar` 调 `function.meta()` 直接注册，**运行时零 DB 读** |
| 管理路径 | `wf_function`（V11 种子）仅用于 Admin 列表展示，与执行 meta 双源并存 |
| Schema 加载 | 已建成 `SchemaManager` + `SchemaRegistry` + 配置中心订阅（`SchemaConfigSubscriber` → `SchemaConfigApplier` → `InMemorySchemaRegistry`） |

痛点：函数元信息无法在运维侧动态修改/下发，新增函数类型必须改代码重编，与 Schema 的「数据驱动」能力不对等。

---

## 2. 已实施的 SPI 架构（镜像 fluxion-schema）

新增子模块组 **`fluxion-function:meta`**（位于 `fluxion-function` 下，与 `fluxion-schema` 同构）：

```
fluxion-function:meta                 # 函数元信息 SPI（单模块，零 Spring 依赖）
  ├─ api/FunctionMetaManager        # 门面（镜像 SchemaManager）
  ├─ api/FunctionMetaRegistry        # SPI 数据源抽象（镜像 SchemaRegistry，额外支持 register/unregister 热更新）
  ├─ DefaultFunctionMetaManager      # 纯委托（镜像 DefaultSchemaManager）
  └─ registry/InMemoryFunctionMetaRegistry  # 内存实现（镜像 InMemorySchemaRegistry）

fluxion-function:spring-boot          # 共享：第三方 FunctionComponent SPI 自动注册 + 元信息自动装配
  ├─ FunctionMetaAutoConfiguration   # 自动装配（合并自原 meta:spring-boot，镜像 FluxionSchemaAutoConfiguration）
  ├─ FunctionMetaConfigApplier       # Worker 侧配置中心订阅应用器（镜像 SchemaConfigApplier）
  └─ FunctionMetaConfigCenterAutoConfiguration
```

数据源由 SPI 实现决定（运行时注入）：

| 环境 | FunctionMetaRegistry 实现 | 数据来源 | 说明 |
|------|---------------------------|----------|------|
| Admin 管理面 | `JdbcFunctionMetaRegistry`（`fluxion-admin`） | `wf_function` 表 | **DB 始终写入**（写经 `WfFunctionService` 落库） |
| Worker 运行面 | `InMemoryFunctionMetaRegistry` | 配置中心订阅 | **配置中心也发布**（Admin 经 `FunctionConfigPublisher` 推送） |

全链路（与 Schema 完全同构）：

```
Admin WfFunctionService(写库) ──写──> wf_function
        │
        └─ FunctionConfigPublisher ──发布──> 配置中心(Nacos/Apollo/HTTP)
                                                │
Worker FunctionConfigSubscriber ──订阅──> FunctionMetaConfigApplier ──> InMemoryFunctionMetaRegistry
                                                │
                                    FunctionMetaManager.getMeta(name)  ◀── 业务/校验/管理查询
```

> `FunctionConfigSnapshot` 已实现 `FunctionMeta`，故配置中心下发的快照本身即可直接作为元信息注册，无需二次转换。

### 关键改动清单
- 新增 `fluxion-function:meta` 模块（SPI，零 Spring 依赖）；`fluxion-function:meta:spring-boot` 的自动装配已合并进 `fluxion-function:spring-boot`（含 `AutoConfiguration.imports`）。
- `fluxion-admin`：新增 `JdbcFunctionMetaRegistry` + `FunctionMetaAdminConfiguration`（提供 `FunctionMetaRegistry` Bean）；`WfFunctionService` 新增 `loadAllMetaSnapshots()`（含 BUILTIN）；`publishToWorkers` 不再跳过 BUILTIN（配置中心也发布元信息）。
- `InternalFunctionController.loadAll()` 改为返回含 BUILTIN 的元信息快照，供 Worker 启动 `loadAll` 拉取。
- `BuiltinFunctionRegistrar`：优先从 `FunctionMetaManager`（SPI）解析 meta，回退代码常量；并把代码常量作为兜底种子写入 SPI 注册表（配置中心权威值覆盖）。
- `settings.gradle.kts`、`fluxion-admin`、`fluxion-function:builtin` 增加模块依赖。

---

## 3. 评估：是否应将 fluxion-builtin 参照 fluxion-schema 重新设计？

### 3.1 fluxion-schema 架构的本质
`fluxion-schema` 把「**能力轴=格式**」（json/avro/protobuf）拆成独立子模块，每个子模块只提供 `SchemaFormatBundle`，由统一的 `SchemaManager` / `SchemaRegistry` 编排。其可扩展点是 **数据格式**。

### 3.2 fluxion-builtin 的实际情况
`fluxion-builtin` 当前是「**函数实现 + 元信息常量**」的混合体：`BuiltinFunction` 子类是真正的 Kotlin 执行代码，无法、也不应从 DB/配置中心加载（运行时必须存在 Class）。可数据驱动的部分只有 `FunctionMeta`。

### 3.3 结论与建议

**建议：采用 fluxion-schema 的「门面 + 注册表 SPI 拆分」思想用于元信息，但不要将 fluxion-builtin 物理拆成多子模块。** 理由：

1. **函数实现无法数据驱动**：`BuiltinFunction` 是代码，「格式/来源」拆分（builtin/script/external 子模块）对**实现**无意义——它们本就是不同模块（builtin / script-engine / external/*），已经按来源拆分。再在 builtin 内部按来源拆子模块是过度设计。
2. **真正需要统一的只有元信息视图**：这正是本次新增 `fluxion-function:meta` 解决的——无论 BUILTIN / SCRIPT / EXTERNAL，**所有 FunctionMeta 都经同一个 `FunctionMetaManager` 读取**，数据源（DB / 配置中心 / 内存兜底）由 SPI 决定，实现「动态加载 + 统一管理」。
3. **保留 `BuiltinFunctionMetas` 作为离线兜底**：代码常量仍是 dev / 无配置中心场景的真相源兜底，并作为 `InMemoryFunctionMetaRegistry` 的种子；配置中心权威值覆盖之。这与 `InMemorySchemaRegistry` 由 `SchemaConfigApplier` 覆盖同构。

### 3.4 推荐的最终形态（落地路线）
| 关注面 | 归属 | 加载方式 |
|--------|------|----------|
| 函数执行实现 | `fluxion-builtin` 等代码模块（不变） | 代码常量 `meta()`，Worker 不读库 |
| 函数元信息（FunctionMeta） | `fluxion-function:meta` 统一管理 | SPI：`JdbcFunctionMetaRegistry`(Admin) / `InMemoryFunctionMetaRegistry`(Worker, 配置中心订阅) |
| 实际入参/出参 Schema 内容 | `fluxion-schema`（`wf_schema`） | 既有的 SchemaManager 解析 |

> 即：**执行实现留在各能力域模块，元信息视图收敛到 `fluxion-function:meta`**。这样既获得 fluxion-schema 的「动态加载 + 统一管理」收益，又避免对 builtin 模块做无收益的物理拆分。

### 3.5 后续可选项（非必须）
- 当 `wf_function` 内置种子的元信息被确认全覆盖后，可将 `BuiltinFunctionMetas` 收敛为仅「dev/离线」种子生成器（生产环境完全以 SPI 为准），进一步弱化硬编码。
- 若未来需要插件化内置函数（第三方 jar 贡献 builtin），可引入 `FunctionMetaProvider` SPI（类比 `SchemaFormatBundle`），由插件提供种子元信息——当前 `InMemoryFunctionMetaRegistry` 的 `register` 已为此预留写入能力。

---

## 4. 风险与缓解
- **Worker 启动依赖配置中心可达**：`FunctionMetaConfigApplier.init()` 与 `SchemaConfigApplier` 同构，复用既有可达性假设；离线场景由 `BuiltinFunctionRegistrar` 的代码种子兜底。
- **内置函数被可执行应用器误注册**：`script-engine` 的 `FunctionConfigApplier` 对 `BUILTIN` 类型走 `else → warn/return`，安全跳过；本应用器只维护元信息视图，与执行注册表隔离。
- **双源漂移**：执行面（代码常量）与管理面（DB/配置中心）用途不同；`FunctionMetaManager` 在两侧分别取权威源（Worker 取配置中心、Admin 取 DB），互不冲突。
