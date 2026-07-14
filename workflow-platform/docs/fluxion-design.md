# 函数式工作流配置后台 — 技术方案

---

## 1. 设计哲学：工作流 = 受控的调用栈

### 1.1 类比：CPU 调用栈模型

```
CPU 调用栈                       工作流引擎
─────────────────────────────    ─────────────────────────────────
Stack Frame（栈帧）          ↔   WorkflowNode（节点）
return value（返回值）        ↔   NodeOutput — 下一节点的直接入参
函数形参（局部，不可变）        ↔   NodeInput.directInput（只读）
调用方传递的参数               ↔   rawInput（工作流原始入参）
已结束帧的返回值（只读可访问） ↔   ImmutableExecutionState.nodeOutputs
TLS（线程本地存储）             ↔   executionId、traceId 等元信息
Program Counter               ↔   currentNode 指针
```

**核心约束**（类比 CPU 栈帧）：
1. 节点函数**只能看到**：自己的直接入参 + 显式声明依赖的前置节点输出
2. 节点函数**不能写入**共享状态 — 返回值是唯一的输出通道
3. 执行状态在每次节点完成后产生**新的不可变快照**，旧快照永久保存

### 1.2 四大核心原则

| 原则 | 类比 | 工程价值 |
|------|------|---------|
| **Function as First-Class Citizen** | FP 一等函数：可传递、可组合、可存储 | 最细粒度复用逻辑，提升语义表达力 |
| **Explicit Data Flow** | UNIX 管道：每个进程只看 stdin/stdout | 节点间依赖图可静态分析，无隐式耦合 |
| **Immutable State** | Haskell State Monad：状态显式线程化 | 天然支持重放、调试、热加载一致性 |
| **Isolated Execution** | Actor 模型：只通过消息通信 | DAG 并行无锁，测试可完全隔离 |

#### 函数是一等公民（Function as First-Class Citizen）

核心思想：**凡是能以最细粒度独立编程、独立测试的业务逻辑，都应表达为 `WorkflowFunction<O>`**，通过组合和编排实现复用与语义清晰。引擎和框架层（调度、观测、容错、适配）不强制函数化。

**适用范围**（业务处理管道中的原子步骤）：

```
传统硬编码方式                      函数化表达
─────────────────────────        ─────────────────────────────
if (!valid) throw ...        →   builtin:schemaValidate
jdbcTemplate.query(...)      →   builtin:dbQuery
BeanUtils.copyProperties()   →   builtin:jsonTransform
return ResponseEntity.ok()   →   builtin:responseAssemble
redisTemplate.get(...)       →   builtin:redisCommand
restTemplate.exchange(...)   →   builtin:httpCall
groovyShell.evaluate(...)    →   script:groovy / script:js
```

**不属于函数职责**（由引擎/框架层负责）：
- 引擎调度：DAG 拓扑排序、状态快照流转
- 适配器翻译：HTTP/RPC → UnifiedRequest
- 横切关注点：装饰器（metrics / trace / rateLimit）
- 基础设施：配置加载、路由注册、实例心跳

**设计约束**：
1. 业务处理逻辑通过 `WorkflowFunction` 接口暴露，引擎内部不硬编码业务逻辑
2. 内置函数（`builtin:*`）与自定义函数共享同一 `FunctionRegistry`，行为完全一致
3. 函数可通过 `andThen` 组合为新函数，组合后仍然是 `WorkflowFunction`
4. 新增能力 = 新增函数实现 + 注册到 Registry，引擎代码零修改

### 1.3 数据流模型

```
节点1(input) ──return──► output_n1
                             │
                    ImmutableExecutionState.withNodeOutput("n1", output_n1)
                             │
节点2(output_n1) ──return──► output_n2  ← 显式数据流
                             │
DAG 聚合器 ──merge──► {n1:..., n2:...}  ← 并行节点输出合并
```

### 1.4 调度能力边界：核心引擎不内置定时/延时任务

Fluxion 核心引擎只负责**函数式工作流的一次性执行与编排**，不内置任何定时或延时（一次性）任务调度能力。`TriggerType` 仅保留 `MANUAL`、`WEBHOOK`、`EVENT`、`API` 四类触发方式，原 `CRON` 周期性调度已移除。

**能力边界说明**：

| 能力 | 是否在核心引擎内 | 推荐实现方式 |
|------|----------------|------------|
| 周期性定时触发 | 否 | 用户自建 Quartz / XXL-JOB / ElasticJob 等调度平台，到点调 HTTP/RPC/MQ 入口 |
| 一次性延时触发 | 否 | 用户自建延迟队列（RocketMQ / RabbitMQ 延迟消息、Redis ZSET 等），到期回调触发 |
| 工作流执行编排 | 是 | Fluxion 核心负责 DAG 拓扑、状态快照、节点调度 |

**为什么不在核心引擎集成调度**：

1. **职责聚焦**：Fluxion 的核心价值是函数式工作流编排，调度属于另一个独立问题域（时间/延迟队列管理），应由专门系统解决。
2. **避免核心服务内存堆积**：内置调度器会在核心进程中长期驻留大量待触发任务，影响执行面稳定性与扩缩容弹性。
3. **扩展性更好**：外部调度平台可按业务规模独立选型、独立扩容，不绑定工作流引擎发布节奏。
4. **已有入口足够**：工作流已暴露 HTTP（`MvcWorkflowHandler` / `WebFluxWorkflowHandler`）、RPC（`DubboWorkflowApi`）、MQ 消费、Signal 恢复等入口，外部调度平台只需在触发时间点发起调用即可。

**未来演进**：若后续需要为 Fluxion 提供“开箱即用”的调度体验，应建设**独立模块/服务**（如 `fluxion-scheduler` 或外部 SaaS），由该模块管理调度计划并向上述入口发起触发，而不是把调度逻辑下沉到 `fluxion-core` 或 `fluxion-admin`。

---

## 2. 核心抽象重设计

### 2.1 不可变执行状态（ImmutableExecutionState）

```kotlin
/**
 * 工作流执行状态 — 不可变快照，类比"调用栈的只读视图"
 *
 * 设计类比：
 *   inputs       = 函数调用时传入的形参（创建后不可变）
 *   nodeOutputs  = 调用栈上方所有帧的返回值（append-only，只读）
 *   meta         = Thread-Local Storage（执行元信息）
 *
 * 线程安全：所有字段为只读 val，天然支持 DAG 并发读取，无需任何同步
 */
class ImmutableExecutionState private constructor(
    /** 工作流原始入参（类比函数调用时的实参，永远不变） */
    val inputs: Map<String, Any>,
    /** 已执行节点的输出快照（Append-only） */
    val nodeOutputs: Map<String, Any?>,
    /** 执行元信息 */
    val meta: ExecutionMeta
) {
    companion object {
        /** 工作流启动时创建初始状态 */
        @JvmStatic
        fun start(def: WorkflowDefinition, rawInput: Map<String, Any>?): ImmutableExecutionState {
            val meta = ExecutionMeta(
                workflowId = def.id,
                workflowName = def.name,
                version = def.version,
                executionId = UUID.randomUUID().toString(),
                startTime = System.currentTimeMillis()
            )
            return ImmutableExecutionState(
                inputs = rawInput?.toMap() ?: emptyMap(),
                nodeOutputs = emptyMap(),
                meta = meta
            )
        }
    }

    /** 节点完成后产生新快照（不修改当前实例） */
    fun withNodeOutput(nodeId: String, output: Any?): ImmutableExecutionState {
        val newOutputs = nodeOutputs.toMutableMap()
        newOutputs[nodeId] = output
        return ImmutableExecutionState(inputs, newOutputs.toMap(), meta)
    }

    /** DAG 并行节点完成后批量合并（原子操作） */
    fun mergeNodeOutputs(parallelOutputs: Map<String, Any?>): ImmutableExecutionState {
        val newOutputs = nodeOutputs.toMutableMap()
        newOutputs.putAll(parallelOutputs)
        return ImmutableExecutionState(inputs, newOutputs.toMap(), meta)
    }

    // ─── 只读访问接口 ───────────────────────────────────────────
    @Suppress("UNCHECKED_CAST")
    fun <T> getInput(key: String): T? = inputs[key] as? T

    @Suppress("UNCHECKED_CAST")
    fun <T> getNodeOutput(nodeId: String): T? = nodeOutputs[nodeId] as? T

    fun hasNodeOutput(nodeId: String): Boolean = nodeOutputs.containsKey(nodeId)
}
```

### 2.2 节点输入模型（NodeInput）

```kotlin
/**
 * 节点的完整输入上下文 — 类比"函数的调用参数 + 可见的外部环境"
 *
 * 类比：
 *   directInput   = 函数形参（来自上一节点的返回值）
 *   declaredDeps  = 闭包捕获的外部变量（静态声明的依赖节点输出）
 *   workflowInput = 全局常量（整个工作流的原始入参）
 *   nodeParams    = 编译时常量（节点配置参数，不来自运行时数据流）
 *
 * 节点函数 只能 通过 NodeInput 访问数据，不能访问 ImmutableExecutionState 的其他部分
 * 这保证了节点的纯洁性：给定相同的 NodeInput，永远返回相同的输出
 */
data class NodeInput(
    /** 直接入参：上一节点的返回值（线性流）或 DAG 聚合结果 */
    val directInput: Any?,
    /** 显式声明的依赖节点输出（对应节点配置 dependsOn 字段） */
    val declaredDeps: Map<String, Any> = emptyMap(),
    /** 工作流原始入参（不可变） */
    val workflowInput: Map<String, Any> = emptyMap(),
    /** 节点配置参数（来自 WorkflowNode.params，非运行时数据） */
    val nodeParams: Map<String, Any> = emptyMap(),
    /** 执行元信息（executionId/traceId 等，只读） */
    val meta: ExecutionMeta? = null
) {
    /** 直接入参安全取值（nullable） */
    inline fun <reified T> input(): T? = directInput as? T

    /** 直接入参必传，缺失抛异常 */
    inline fun <reified T> requireInput(): T =
        input<T>() ?: throw InvalidParamException("directInput is required")

    /** 直接入参带默认值 */
    inline fun <reified T> input(default: T): T = input<T>() ?: default

    /** 按 key 获取声明依赖的前置节点输出 */
    inline fun <reified T> dep(nodeId: String): T? = declaredDeps[nodeId] as? T

    /** 获取工作流原始入参 */
    inline fun <reified T> wfInput(key: String): T? = workflowInput[key] as? T

    /** 节点参数安全取值（nullable） */
    inline fun <reified T> param(key: String): T? = nodeParams[key] as? T

    /** 节点参数必传，缺失抛异常 */
    inline fun <reified T> requireParam(key: String): T =
        param<T>(key) ?: throw InvalidParamException("nodeParams.$key is required")

    /** 替换 directInput，其他字段保持不变（用于 WorkflowFunction.andThen 函数组合） */
    fun withDirectInput(newDirectInput: Any?): NodeInput = copy(directInput = newDirectInput)

    /** 替换 nodeParams，其他字段保持不变（用于 Saga 补偿时注入 sideEffects 信息） */
    fun withNodeParams(newNodeParams: Map<String, Any>?): NodeInput =
        copy(nodeParams = newNodeParams?.toMap() ?: emptyMap())
}
```

### 2.3 工作流函数接口（升级版）

```kotlin
/**
 * 工作流函数核心抽象 v2
 *
 * 设计要点：
 *   O apply(NodeInput input)  // 显式、纯净、可测试（无 ctx 副作用）
 *
 * @param O 输出类型
 */
fun interface WorkflowFunction<O> {

    /**
     * 执行函数逻辑
     *
     * 约束（类比纯函数 / 栈帧隔离）：
     *   ① 只能读取 input 中的数据，不能访问 ImmutableExecutionState
     *   ② 返回值是唯一的输出通道（没有副作用写入共享状态）
     *   ③ 对于相同的 input，应始终返回相同的 output（幂等性）
     *   ④ I/O 副作用（DB读写、RPC）通过 FunctionResult 的 sideEffects 字段显式声明
     */
    fun apply(input: NodeInput): FunctionResult<O>

    /**
     * 函数元信息（用于注册、文档生成、Schema 校验）
     *
     * 推荐实现类把 FunctionMeta 作为 companion object 常量静态初始化，
     * 并在 meta() 中直接返回该常量，保证调用时只是纯 get，无运行时构造开销。
     */
    fun meta(): FunctionMeta = FunctionMeta.of(this::class.java.simpleName ?: "anonymous")

    /**
     * 降级函数（可选覆盖）
     *
     * ⚠️ 默认实现抛出异常。若需要真正的降级恢复逻辑，必须覆盖此方法并返回合法的降级值。
     */
    fun fallback(input: NodeInput, ex: Throwable): FunctionResult<O> {
        throw WorkflowNodeException(meta().name, ex)
    }

    // ─── 函数组合能力 ────────────────────────────────────────────

    /**
     * 链式组合：this → after（类比函数调用链）
     * 组合后的函数：NodeInput → this.apply() → NodeInput(output) → after.apply()
     */
    fun <V> andThen(after: WorkflowFunction<V>): WorkflowFunction<V> {
        val self = this
        return object : WorkflowFunction<V> {
            override fun apply(input: NodeInput): FunctionResult<V> {
                val intermediate = self.apply(input)
                val nextInput = input.withDirectInput(intermediate.output)
                return after.apply(nextInput)
            }

            override fun meta(): FunctionMeta = FunctionMeta.composed(self.meta(), after.meta())
        }
    }
}
```

### 2.4 函数返回值（FunctionResult）

```kotlin
/**
 * 函数执行结果 — 显式声明副作用
 *
 * 将 I/O 副作用从隐式行为变为显式字段，
 * 使引擎可以统一管理事务、回滚、审计等横切关注点。
 *
 * @param O 业务输出类型
 */
data class FunctionResult<O>(
    /** 业务输出（下一节点的 directInput） */
    val output: O?,
    /** 函数执行状态 */
    val status: FunctionStatus,
    /** 声明发生的副作用（DB写、MQ发送），供 Saga 回滚使用 */
    val sideEffects: List<SideEffect> = emptyList()
) {
    companion object {
        @JvmStatic
        fun <O> success(output: O): FunctionResult<O> =
            FunctionResult(output, FunctionStatus.SUCCESS)

        @JvmStatic
        fun <O> successWithEffects(output: O, effects: List<SideEffect>): FunctionResult<O> =
            FunctionResult(output, FunctionStatus.SUCCESS, effects)

        @JvmStatic
        fun <O> skipped(): FunctionResult<O> =
            FunctionResult(null, FunctionStatus.SKIPPED)
    }
}

/** 副作用描述（用于 Saga 补偿） */
data class SideEffect(
    /** 副作用类型："DB_UPDATE", "MQ_SEND", "RPC_CALL" 等 */
    val type: String,
    /** 目标：表名 / topic / 服务名 */
    val target: String,
    /** 写入的数据 */
    val payload: Any?,
    /** 补偿函数引用 */
    val compensateFunctionRef: String? = null
)
```

### 2.5 工作流节点（升级版）

```kotlin
/**
 * 工作流节点 — 类比调用栈的栈帧
 */
data class WorkflowNode(
    var id: String = "",
    var name: String = "",
    /** 所属工作流 ID（引擎注入，用于 Metrics/Trace） */
    var workflowId: String = "",
    var functionRef: String = "",
    var type: NodeType = NodeType.CUSTOM,

    /** 节点配置参数（非运行时数据流，在 NodeInput.nodeParams 中提供） */
    var params: Map<String, Any>? = null,

    /** 错误处理策略：FAIL / SKIP / FALLBACK / RETRY */
    var errorStrategy: ErrorStrategy = ErrorStrategy.FAIL,
    var timeoutMs: Int = 0,
    var retryCount: Int = 0,
    /** 重试基础间隔(ms)，指数退避：delay = retryBaseMs * 2^attempt */
    var retryBaseMs: Int = 0,

    /**
     * 补偿函数引用（Saga 模式）
     * 当后续节点失败触发回滚时，引擎调用此函数撤销本节点的副作用
     */
    var compensateFunctionRef: String? = null,

    // ─── 流转控制 ──────────────────────────────────────────────

    /** 默认下一节点（null = 按列表顺序） */
    var next: String? = null,

    /**
     * 条件分支（优先级高于 next）
     * 引擎按顺序求值，第一个为 true 的条件生效
     */
    var conditionalNexts: List<ConditionalNext>? = null,

    /**
     * 显式依赖声明（DAG 并行场景）
     *
     * 关键设计：这里声明的 nodeId 对应的输出，会在执行时被打包进
     * NodeInput.declaredDeps，节点函数通过 input.dep("n1") 访问。
     * 无需在函数内部访问 ImmutableExecutionState。
     */
    var dependsOn: List<String>? = null,

    /** 装饰器引用列表 */
    var decorators: List<String>? = null,
    var decoratorParams: Map<String, Map<String, Any>>? = null
)
```

### 2.6 核心辅助类型（完整定义）

被引擎和函数层广泛引用的值对象、枚举和接口，集中定义在此处，供跨模块使用。

#### 2.6.1 ExecutionMeta（执行元信息）

```java
/**
 * 执行元信息 — 类比 CPU 的 Thread-Local Storage
 * 绑定到单次工作流执行，贯穿所有节点（只读）
 *
 * 通过 NodeInput.meta() 在节点函数中访问，不暴露 ImmutableExecutionState 全局状态
 */
@Value
@Builder
public class ExecutionMeta {

    /** 工作流定义 ID */
    String workflowId;
    /** 工作流名称（日志/Metrics 用） */
    String workflowName;
    /** 当前执行的工作流版本号 */
    int    version;
    /** 本次执行唯一 ID（UUID），写入 wf_execution_log */
    String executionId;
    /** 执行开始时间（毫秒时间戳） */
    long   startTime;
    /** 分布式追踪 TraceId（从上游 HTTP/RPC 头传播） */
    String traceId;
    /** 分布式追踪 SpanId（本次工作流入口 Span） */
    String spanId;
    /** W3C Baggage / B3 扩展字段（只读透传） */
    @Builder.Default
    Map<String, String> baggage = Map.of();

    /** 获取用于链路追踪的上下文标识（优先用 traceId，降级用 executionId） */
    public String getTraceContext() {
        return traceId != null ? traceId : executionId;
    }
}
```

#### 2.6.2 FunctionMeta（函数元信息）

```kotlin
/**
 * 函数元信息 — 用于注册、文档生成、Schema 校验
 *
 * 设计为不可变值对象，通过 [FunctionMeta.builder] 构造。
 * 单例语义由 [WorkflowFunction] 实现方负责（推荐存为 companion object 常量）。
 */
data class FunctionMeta(
    /** 函数引用名（如 "builtin:dbQuery"，全局唯一） */
    val name: String,
    /** 函数说明（文档/Admin 后台展示用） */
    val description: String? = null,
    /** 输入 JSON Schema（为 null 则跳过入参校验） */
    val inputSchema: Any? = null,
    /** 输出 JSON Schema（为 null 则跳过出参校验） */
    val outputSchema: Any? = null
) {
    class Builder(private val name: String) {
        private var description: String? = null
        private var inputSchema: Any? = null
        private var outputSchema: Any? = null

        fun description(description: String) = apply { this.description = description }
        fun paramSchema(paramSchema: Any?) = apply { this.inputSchema = paramSchema }
        fun outputSchema(outputSchema: Any?) = apply { this.outputSchema = outputSchema }
        fun build(): FunctionMeta = FunctionMeta(name, description, inputSchema, outputSchema)
    }

    companion object {
        @JvmStatic
        fun builder(name: String) = Builder(name)

        /** 快速构造（无 Schema，适用于内部辅助函数） */
        @JvmStatic
        fun of(name: String): FunctionMeta = FunctionMeta(name)

        /** 组合函数的元信息（andThen 使用）：输入取 first，输出取 second */
        @JvmStatic
        fun composed(first: FunctionMeta, second: FunctionMeta) = FunctionMeta(
            name = "${first.name} >> ${second.name}",
            description = "Composed: ${first.name} then ${second.name}",
            inputSchema = first.inputSchema,
            outputSchema = second.outputSchema
        )
    }
}
```

#### 2.6.3 ConditionalNext（条件路由）

```java
/**
 * 条件分支定义 — WorkflowNode.conditionalNexts 的元素
 *
 * 引擎按顺序对 condition 求值（ExpressionEvaluator.evalBoolean），
 * 第一个为 true 的条件生效，跳转到 target 节点。
 */
public record ConditionalNext(
    /**
     * SpEL 条件表达式
     * 可用变量：
     *   #output  — 当前节点的输出（Map 时字段可直接访问，如 #status == 'APPROVED'）
     *   #input   — 工作流原始入参
     * 示例：
     *   "#output['status'] == 'APPROVED'"
     *   "#input['amount'] > 1000"
     */
    String condition,
    /** 条件为 true 时跳转的目标节点 ID */
    String target
) {
    public String getCondition() { return condition; }
    public String getTarget()    { return target; }
}
```

#### 2.6.4 枚举类型

```java
/** 函数执行结果状态 */
public enum FunctionStatus {
    /** 正常返回输出 */
    SUCCESS,
    /** 函数执行失败（抛出异常） */
    FAILED,
    /** 函数主动跳过（如前置条件不满足） */
    SKIPPED
}

/** 节点执行状态（执行记录用） */
public enum NodeStatus {
    SUCCESS,  // 正常完成
    FAILED,   // 失败（错误策略=FAIL 或重试耗尽）
    SKIPPED,  // 跳过（错误策略=SKIP）
    FALLBACK, // 降级成功（错误策略=FALLBACK）
    RETRY     // 重试中（中间状态，最终为 SUCCESS 或 FAILED）
}

/**
 * 节点错误处理策略
 * 配置在 WorkflowNode.errorStrategy，决定节点异常时引擎的行为
 */
public enum ErrorStrategy {
    /** 抛出异常，中止整个工作流 */
    FAIL,
    /** 跳过本节点，继续执行下一节点（output 为 null） */
    SKIP,
    /** 调用 WorkflowFunction.fallback() 获取降级输出，继续执行 */
    FALLBACK,
    /** 按 retryCount / retryBaseMs 指数退避重试 */
    RETRY
}

/** 节点函数类型（决定 FunctionRegistry 的解析优先级） */
enum class NodeType {
    /** 内置函数（builtin:* 前缀，引擎内置） */
    BUILTIN,
    /** 自定义函数（代码中 registry.register() 注册） */
    CUSTOM,
    /** 脚本函数（Groovy/JS，存储在 wf_function 表） */
    SCRIPT,
    /** 外部服务函数（通过 Function Gateway gRPC/HTTP 调用） */
    EXTERNAL
}
```

#### 2.6.5 ValidationResult（校验结果）

```java
/**
 * JSON Schema 校验结果
 * 由 SchemaValidator.validate() 返回，供引擎决定是否允许执行
 */
public record ValidationResult(
    boolean      valid,
    List<String> errors
) {
    /** 校验通过 */
    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    /** 校验失败（携带错误详情） */
    public static ValidationResult fail(List<String> errors) {
        return new ValidationResult(false, List.copyOf(errors));
    }

    // Getter 兼容
    public boolean      isValid()    { return valid; }
    public List<String> getErrors()  { return errors; }
}
```

#### 2.6.6 EngineResult（引擎执行结果）

```java
/**
 * 工作流引擎执行结果
 * 包含业务输出、执行轨迹、最终不可变状态快照（供调试/重放使用）
 */
@Data
@Builder
public class EngineResult {
    /** 执行是否成功 */
    private boolean success;
    /** 最终业务输出（最后一个节点的输出，或 null） */
    private Object  data;
    /** 本次执行 ID（与 wf_execution_log 对应） */
    private String  executionId;
    /** 失败原因（success=false 时有值） */
    private String  errorMsg;
    /** 执行轨迹（各节点的 NodeExecutionRecord 列表，按执行顺序） */
    @Builder.Default
    private List<NodeExecutionRecord> trace = new ArrayList<>();
    /**
     * 最终不可变状态快照（保留完整 nodeOutputs）
     * 供调试断点恢复、精确重放节点使用。生产环境可选择不持久化以节省存储。
     */
    private ImmutableExecutionState finalState;

    /** 成功结果工厂方法 */
    public static EngineResult success(Object data, ImmutableExecutionState state) {
        return EngineResult.builder()
            .success(true)
            .data(data)
            .executionId(state.getMeta().getExecutionId())
            .finalState(state)
            .build();
    }

    /** 失败结果工厂方法 */
    public static EngineResult failure(String errorMsg, String executionId) {
        return EngineResult.builder()
            .success(false)
            .errorMsg(errorMsg)
            .executionId(executionId)
            .build();
    }

    public boolean isSuccess() { return success; }
}
```

#### 2.6.7 ApiResponse（统一 API 响应）

```java
/**
 * 统一 API 响应封装
 * builtin:responseWrapper / builtin:errorWrapper 函数的输出类型
 *
 * @param <T> 业务数据类型
 */
public record ApiResponse<T>(
    /** 业务状态码（200 正常，4xx 客户端错误，5xx 服务端错误） */
    int    code,
    /** 状态说明 */
    String message,
    /** 业务数据（失败时为 null） */
    T      data
) {
    public static <T> ApiResponse<T> of(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
```

#### 2.6.8 PageResult（分页结果）

```java
/**
 * 分页查询结果
 * builtin:paginate 函数的输出类型
 */
public record PageResult<T>(
    /** 当前页数据 */
    List<T> items,
    /** 当前页码（从 1 开始） */
    int     page,
    /** 每页大小 */
    int     size,
    /** 总记录数 */
    long    total,
    /** 总页数 */
    int     totalPages
) {
    public static <T> PageResult<T> of(List<T> items, int page, int size, long total) {
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResult<>(items, page, size, total, totalPages);
    }

    public boolean hasNext() { return page < totalPages; }
    public boolean hasPrev() { return page > 1; }
}
```

#### 2.6.9 WorkflowRouter（工作流路由器）

```java
/**
 * 工作流路由器 — 适配器层的核心协调者
 * 负责将 UnifiedRequest 路由到正确的 WorkflowDefinition 并调用引擎
 *
 * 实现位于 fluxion-admin 模块，注入到各适配器（HTTP/Dubbo/gRPC/MQ）
 */
public interface WorkflowRouter {

    /**
     * 从框架原生请求（HttpServletRequest）和工作流定义构建统一请求
     * 主要用于 SpringMvcWorkflowAdapter.WorkflowHandler
     */
    UnifiedRequest buildRequest(Object nativeRequest, WorkflowDefinition definition);

    /**
     * 执行工作流
     * 内部会从缓存查找最新 WorkflowDefinition（支持热加载透明）
     *
     * @param request 统一请求
     * @return 执行结果（永不为 null）
     */
    EngineResult execute(UnifiedRequest request);

    /**
     * 根据 workflowId 直接执行（跳过路由解析，用于内部调用）
     */
    default EngineResult executeById(String workflowId, Map<String, Object> params) {
        UnifiedRequest req = new UnifiedRequest(workflowId, params, Map.of(), "INTERNAL", null);
        return execute(req);
    }
}
```

#### 2.6.10 SqlCondition / SqlWithParams

```java
/**
 * SQL 条件参数 — DynamicSqlGenerator 的输入类型
 * 配置在 WorkflowNode.params.conditions 数组中（JSON 反序列化）
 */
public record SqlCondition(
    /** 字段名（经过 validateIdentifier 校验，防 SQL 注入） */
    String                     field,
    /** 操作符 */
    DynamicSqlGenerator.Operator op,
    /**
     * 比较值
     * EQ/NEQ/GT 等：任意值
     * IN/NOT_IN：List<?>
     * BETWEEN：Object[2]（范围的 lower/upper）
     * IS_NULL/IS_NOT_NULL：忽略此字段
     */
    Object                     value
) {
    public String                      getField() { return field; }
    public DynamicSqlGenerator.Operator getOp()   { return op; }
    public Object                      getValue() { return value; }
}

/**
 * SQL 语句 + 绑定参数（PreparedStatement 风格）
 * DynamicSqlGenerator 的输出，传给 JdbcTemplate
 */
public record SqlWithParams(
    /** 带 ? 占位符的 SQL 片段或完整 SQL */
    String   sql,
    /** 对应 ? 的绑定参数（与 JdbcTemplate.query(sql, params) 顺序一致） */
    Object[] params
) {
    /** 可变参数构造（避免每次 new Object[]{...}） */
    public static SqlWithParams of(String sql, Object... params) {
        return new SqlWithParams(sql, params);
    }

    public String   getSql()    { return sql; }
    public Object[] getParams() { return params; }
}
```

#### 2.6.11 TransactionConfig（事务配置）

```java
/**
 * 事务配置 — TransactionAdapter.begin() 的参数
 * 对应 WorkflowDefinition.transactionConfig 字段（JSON 反序列化）
 */
@Data
@Builder
public class TransactionConfig {
    private String workflowId;
    private String executionId;
    /** 事务名称（用于日志/Seata 全局事务名） */
    private String transactionName;
    /**
     * 隔离级别
     * 取值：java.sql.Connection.TRANSACTION_READ_COMMITTED 等
     * 默认：Connection.TRANSACTION_READ_COMMITTED（2）
     */
    @Builder.Default
    private int    isolationLevel    = java.sql.Connection.TRANSACTION_READ_COMMITTED;
    /**
     * 传播行为
     * 取值：TransactionDefinition.PROPAGATION_REQUIRED 等
     * 默认：PROPAGATION_REQUIRED（0）
     */
    @Builder.Default
    private int    propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED;
    /**
     * 超时时间（毫秒，0 = 无限制）
     * 超时后 TransactionAdapter 自动回滚
     */
    @Builder.Default
    private int    timeoutMs          = 30_000;
}
```

### 2.7 函数注册中心（FunctionRegistry）— 多版本共存

```kotlin
/**
 * 函数注册中心 — 纯 Kotlin，零框架依赖
 *
 * 支持函数多版本共存：
 * - 代码内置函数（BUILTIN / CUSTOM）使用版本 0，直接替换 ACTIVE 版本
 * - 热发布函数（SCRIPT / EXTERNAL）使用递增版本号，旧版本进入 RETIRING 状态
 *   后继续服务在途调用，无在途调用后可被清理
 *
 * 解析优先级：CUSTOM > BUILTIN > SCRIPT > EXTERNAL
 */
class FunctionRegistry {

    /** functionName / functionNameWithPrefix -> VersionedFunction */
    private val functions = ConcurrentHashMap<String, VersionedFunction>()

    /** 当前 ACTIVE 版本的元信息 */
    private val metaRegistry = ConcurrentHashMap<String, FunctionMeta>()

    /**
     * 代码注册函数（内置 / 自定义函数）。
     * 使用版本号 0，直接替换 ACTIVE 版本，不产生退役版本。
     */
    fun register(name: String, meta: FunctionMeta, function: WorkflowFunction<*>) { ... }

    fun register(name: String, function: WorkflowFunction<*>) {
        register(name, FunctionMeta.of(name), function)
    }

    /**
     * 注册热发布函数。
     * 使用 [version] 作为版本号，原 ACTIVE 版本进入 RETIRING。
     */
    fun register(name: String, version: Long, meta: FunctionMeta, function: WorkflowFunction<*>) { ... }

    /** 解析当前 ACTIVE 版本 */
    fun resolve(functionRef: String, nodeType: NodeType?): WorkflowFunction<Any> = ...

    /** 解析指定版本（可从 ACTIVE 或 RETIRING 中查找） */
    fun resolve(functionRef: String, nodeType: NodeType?, version: Long): WorkflowFunction<Any> = ...

    /** 获取指定函数当前 ACTIVE / RETIRING 版本号 */
    fun activeVersion(functionRef: String): Long? = ...
    fun retiringVersion(functionRef: String): Long? = ...

    /** 尝试清理指定函数的 RETIRING 版本（仅当无在途调用时） */
    fun purgeRetiring(functionRef: String): Boolean = ...
    fun purgeAllRetiring(): Int = ...
}
```

**版本生命周期（最简模型）**：

```
            注册 v1               注册 v2               注册 v3
              │                     │                     │
              ▼                     ▼                     ▼
ACTIVE:      v1                    v2                    v3
RETIRING:    -                     v1                    v2
             ▲                     ▲                     ▲
        无在途调用即可 purge   v1 无在途即可 purge   v2 无在途即可 purge
```

- 每个函数最多同时保留 **ACTIVE + RETIRING** 两个版本。
- 新版本发布时，原 ACTIVE 进入 RETIRING；RETIRING 槽若已有版本，则直接丢弃其引用（任何在途调用仍持有该 `FunctionVersion` 对象，可安全完成）。
- `InFlightTrackingFunction` 每次 `apply()` 前后维护 `FunctionVersion` 的 `inFlight` 计数，使退役版本能感知是否还有未完成的调用。

> 对应实现：[FunctionRegistry.kt](fluxion-core/src/main/kotlin/com/fluxion/core/function/FunctionRegistry.kt)、
> [VersionedFunction.kt](fluxion-core/src/main/kotlin/com/fluxion/core/function/VersionedFunction.kt)、
> [FunctionVersion.kt](fluxion-core/src/main/kotlin/com/fluxion/core/function/FunctionVersion.kt)

---

## 3. 工作流执行引擎

### 3.1 执行模型概览

```
外部请求
    │
    ▼
UnifiedRequest（适配器层转换）
    │
    ▼
SchemaValidator.validate(inputSchema, rawInput)
    │
    ▼
ImmutableExecutionState.start(def, rawInput)   ← 初始状态
    │
    ▼ 循环执行节点
┌─────────────────────────────────────────────────────┐
│  buildNodeInput(currentNode, state)                  │
│    ├── directInput = state.getNodeOutput(prevNode)   │
│    ├── declaredDeps = {n1:..., n2:...}              │
│    ├── workflowInput = state.getInputs()             │
│    └── nodeParams = currentNode.getParams()          │
│                                                      │
│  FunctionResult result = function.apply(nodeInput)   │
│                                                      │
│  state = state.withNodeOutput(nodeId, result.output) │
│          ← 产生新快照，旧快照不受影响                  │
└─────────────────────────────────────────────────────┘
    │
    ▼
OutputSchemaValidator（devMode 开启时）
    │
    ▼
EngineResult
```

### 3.2 引擎核心实现

```java
/**
 * 工作流引擎 — 基于不可变执行状态
 * 零框架依赖，位于 fluxion-core 模块
 */
public class WorkflowEngine {

    private final FunctionRegistry    functionRegistry;
    private final SchemaValidator     schemaValidator;
    private final DecoratorRegistry   decoratorRegistry;
    private final WorkflowMetrics     metrics;
    /** 时间轮重试调度器（替代 Thread.sleep，非阻塞延迟重试） */
    private final RetryScheduler      retryScheduler;

    /** 是否开启严格 Schema 校验（开发/测试环境建议开启） */
    private final boolean devMode;
    /** 是否校验每个节点的输出 Schema */
    private final boolean validateNodeOutput;

    public EngineResult execute(WorkflowDefinition def, Map<String, Object> rawInput) {

        // 1. 入参 Schema 校验
        ValidationResult validation = schemaValidator.validate(def.getInputSchema(), rawInput);
        if (!validation.isValid()) {
            throw new InvalidParamException(validation.getErrors());
        }

        // 2. 创建初始不可变状态（同时锁定版本，热加载期间不受影响）
        ImmutableExecutionState state = ImmutableExecutionState.start(def, rawInput);

        // 3. 预构建执行图（O(n)，后续节点查找 O(1)）
        List<WorkflowNode> lockedNodes = new ArrayList<>(def.getNodes());
        lockedNodes.forEach(n -> n.setWorkflowId(def.getId()));
        Map<String, WorkflowNode> nodeIndex     = buildNodeIndex(lockedNodes);
        Map<String, Integer>      nodeListIndex = buildNodeListIndex(lockedNodes);

        // 4. 执行记录（类比调用栈轨迹）
        List<NodeExecutionRecord> executionTrace = new ArrayList<>();

        // 5. 节点执行循环
        // ★ 首节点直接入参 = rawInput（而非 null）
        Object directInput     = rawInput;
        WorkflowNode current   = lockedNodes.get(0);

        while (current != null) {
            NodeExecutionRecord record = executeNode(current, directInput, state, executionTrace);
            executionTrace.add(record);

            if (record.getStatus() == NodeStatus.FAILED &&
                    current.getErrorStrategy() == ErrorStrategy.FAIL) {
                throw new WorkflowNodeException(current.getName(), record.getError());
            }

            // 6. 更新状态快照（产生新的不可变版本）
            if (record.getStatus() != NodeStatus.SKIPPED) {
                state = state.withNodeOutput(current.getId(), record.getOutput());
                directInput = record.getOutput();
            }

            // 7. 解析下一节点（O(1) 查找）
            current = resolveNextNode(current, directInput, state, nodeIndex, nodeListIndex, lockedNodes);
        }

        // 8. 输出 Schema 校验（devMode）
        if (devMode) {
            schemaValidator.validateStrict(def.getOutputSchema(), directInput);
        }

        return EngineResult.builder()
            .success(true)
            .data(directInput)
            .executionId(state.getMeta().getExecutionId())
            .trace(executionTrace)
            .finalState(state)   // 保留完整状态快照，供调试/重放使用
            .build();
    }

    // ─── 单节点执行 ────────────────────────────────────────────

    /**
     * 执行单个节点（package-private，供 DagExecutor 在同包内调用）
     *
     * ⚠️ 可见性说明：
     *   - 不对外公开（避免绕过引擎的流程控制直接调用节点）
     *   - DagExecutor 位于同一包（fluxion-core），可合法调用
     *   - 若需要在包外调用（如测试），建议通过 WorkflowEngine.executeSingleNode() 委托
     */
    NodeExecutionRecord executeNode(WorkflowNode node,
                                    Object directInput,
                                    ImmutableExecutionState state,
                                    List<NodeExecutionRecord> trace) {
        long start = System.currentTimeMillis();
        try {
            // 构建 NodeInput（节点函数的唯一数据来源）
            NodeInput nodeInput = buildNodeInput(node, directInput, state);

            // 解析函数（优先级：custom > builtin > script > external）
            WorkflowFunction<Object> function =
                functionRegistry.resolve(node.getFunctionRef(), node.getType());

            // 应用装饰器（metrics、async、cache、retry...）
            if (node.getDecorators() != null) {
                for (String decoratorRef : node.getDecorators()) {
                    NodeDecorator decorator = decoratorRegistry.resolve(decoratorRef);
                    function = decorator.decorate(function, node);
                }
            }

            // 函数级 Input Schema 校验
            FunctionMeta meta = function.meta();
            if (meta.getInputSchema() != null) {
                ValidationResult inputValidation =
                    schemaValidator.validate(meta.getInputSchema(), nodeInput.directInput());
                if (!inputValidation.isValid()) {
                    throw new SchemaValidationException(
                        "Node [" + node.getName() + "] input schema mismatch: "
                            + inputValidation.getErrors());
                }
            }

            // 执行（含超时控制）
            FunctionResult<Object> result =
                withTimeout(() -> function.apply(nodeInput), node.getTimeoutMs());

            // 函数级 Output Schema 校验（validateNodeOutput=true）
            if (meta.getOutputSchema() != null && validateNodeOutput) {
                ValidationResult outputValidation =
                    schemaValidator.validate(meta.getOutputSchema(), result.output());
                if (!outputValidation.isValid()) {
                    metrics.increment("workflow.node.schema.output.mismatch",
                        Map.of("node", node.getId(), "workflow", node.getWorkflowId()));
                    log.warn("Node [{}] output schema mismatch: {}", node.getName(),
                        outputValidation.getErrors());
                }
            }

            return NodeExecutionRecord.success(node, result.output(),
                System.currentTimeMillis() - start);

        } catch (Exception ex) {
            return handleNodeError(node, directInput, state, ex,
                System.currentTimeMillis() - start);
        }
    }

    // ─── 构建 NodeInput ────────────────────────────────────────

    /**
     * 将 ImmutableExecutionState 中的数据按需打包为 NodeInput
     *
     * 类比：
     *   - 调用函数时，编译器/VM 将参数压栈，函数只能看到自己的参数
     *   - dependsOn 声明 = 显式的"闭包变量捕获列表"
     *   - 节点函数对 ImmutableExecutionState 完全不可见
     */
    private NodeInput buildNodeInput(WorkflowNode node,
                                     Object directInput,
                                     ImmutableExecutionState state) {
        // 按 dependsOn 声明提取依赖节点的输出
        Map<String, Object> declaredDeps = new LinkedHashMap<>();
        if (node.getDependsOn() != null) {
            for (String depNodeId : node.getDependsOn()) {
                Object depOutput = state.getNodeOutput(depNodeId);
                if (depOutput == null) {
                    throw new DependencyNotReadyException(node.getId(), depNodeId);
                }
                declaredDeps.put(depNodeId, depOutput);
            }
        }

        return new NodeInput(
            directInput,
            Collections.unmodifiableMap(declaredDeps),
            state.getInputs(),           // 工作流原始入参（不可变）
            node.getParams() != null ? node.getParams() : Map.of(),
            state.getMeta()
        );
    }

    // ─── 错误处理 ──────────────────────────────────────────────

    private NodeExecutionRecord handleNodeError(WorkflowNode node, Object directInput,
                                                ImmutableExecutionState state,
                                                Exception ex, long durationMs) {
        metrics.increment("workflow.node.error",
            Map.of("node", node.getId(), "error", ex.getClass().getSimpleName()));

        switch (node.getErrorStrategy()) {
            case SKIP:
                log.warn("Node [{}] skipped: {}", node.getName(), ex.getMessage());
                return NodeExecutionRecord.skipped(node, durationMs);

            case FALLBACK:
                try {
                    WorkflowFunction<Object> fn =
                        functionRegistry.resolve(node.getFunctionRef(), node.getType());
                    NodeInput nodeInput = buildNodeInput(node, directInput, state);
                    FunctionResult<Object> fallbackResult = fn.fallback(nodeInput, ex);
                    return NodeExecutionRecord.fallback(node, fallbackResult.output(), durationMs);
                } catch (Exception fallbackEx) {
                    return NodeExecutionRecord.failed(node, fallbackEx, durationMs);
                }

            case RETRY:
                // ★ 时间轮异步重试：scheduleRetryAttempt() 注册到 HashedWheelTimer，
                //   不阻塞调用线程；.join() 在当前线程挂起等待最终结果
                //   （Java 21 虚拟线程：挂起期间 carrier 线程可被其他虚拟线程复用）
                return executeWithRetry(node, directInput, state, ex, durationMs);

            case FAIL:
            default:
                return NodeExecutionRecord.failed(node, ex, durationMs);
        }
    }

    private NodeExecutionRecord executeWithRetry(WorkflowNode node, Object directInput,
                                                  ImmutableExecutionState state,
                                                  Exception firstEx, long initialDurationMs) {
        int maxRetries = node.getRetryCount() > 0 ? node.getRetryCount() : 3;
        int baseMs     = node.getRetryBaseMs() > 0 ? node.getRetryBaseMs() : 100;

        // ★ 记录整个重试过程的起始时间（含首次失败耗时）
        long start = System.currentTimeMillis() - initialDurationMs;

        // ★ 时间轮异步重试，通过 CompletableFuture 获取最终结果
        //   - 不阻塞调用线程（时间轮调度线程只做槽推进，不执行业务）
        //   - Java 21 虚拟线程环境：.join() 不占用 carrier 线程，平台线程零浪费
        return scheduleRetryAttempt(node, directInput, state, firstEx, start, 1, maxRetries, baseMs)
            .join();  // 线性引擎需要同步结果；DAG 执行器（Kotlin）使用 delay() 不需要此处 join
    }

    /**
     * 递归时间轮重试（CompletableFuture 异步链）
     *
     * 调用链（以第 1 次重试为例，baseMs=100）：
     *   t=0   : scheduleRetryAttempt(attempt=1) → 注册到时间轮，立即返回 Future
     *   t=100 : 时间轮触发 → retryWorker 执行函数调用
     *           成功 → future.complete(SUCCESS record)
     *           失败 → exceptionallyCompose → scheduleRetryAttempt(attempt=2)
     *   t=300 : attempt=2 的时间轮触发（100 * 2^1 = 200ms 后）
     *   ...
     *   超出 maxRetries → completedFuture(FAILED record)
     *
     * ★ 整个过程：时间轮线程 + retryWorker 线程协作，调用方线程在 .join() 挂起
     *   挂起期间不消耗 CPU，Java 21 虚拟线程下 carrier 线程完全释放
     */
    private CompletableFuture<NodeExecutionRecord> scheduleRetryAttempt(
            WorkflowNode node, Object directInput, ImmutableExecutionState state,
            Exception prevEx, long start, int attempt, int maxRetries, int baseMs) {

        if (attempt > maxRetries) {
            // 重试耗尽：直接返回已完成的失败 Future，不再进入时间轮
            long totalMs = System.currentTimeMillis() - start;
            log.warn("Node [{}] exhausted {} retries, giving up", node.getName(), maxRetries);
            return CompletableFuture.completedFuture(NodeExecutionRecord.failed(node, prevEx, totalMs));
        }

        // 指数退避延迟（上限 30s）：baseMs * 2^(attempt-1)
        long delayMs = Math.min((long) (baseMs * Math.pow(2, attempt - 1)), 30_000L);

        // 1. 将函数调用任务注册到时间轮，delayMs 后触发执行（非阻塞，立即返回 Future）
        return retryScheduler.schedule(() -> {
            NodeInput nodeInput = buildNodeInput(node, directInput, state);
            WorkflowFunction<Object> fn =
                functionRegistry.resolve(node.getFunctionRef(), node.getType());
            // withTimeout 内部用 FutureTask 执行，超时取消并抛出 WorkflowNodeException
            return withTimeout(() -> fn.apply(nodeInput), node.getTimeoutMs());

        }, delayMs)

        // 2. 执行成功：包装为 SUCCESS 执行记录，完成整个重试 Future 链
        .thenApply(result -> {
            long durationMs = System.currentTimeMillis() - start;
            log.info("Node [{}] succeeded on retry {}/{}, totalDurationMs={}",
                node.getName(), attempt, maxRetries, durationMs);
            return NodeExecutionRecord.success(node, result.output(), durationMs);
        })

        // 3. 执行失败：记录异常，递归调度下一次重试（不阻塞，继续返回新 Future）
        .exceptionallyCompose(throwable -> {
            Exception retryEx = throwable instanceof Exception
                ? (Exception) throwable : new RuntimeException(throwable);
            log.warn("Node [{}] retry {}/{} failed (delayWas={}ms): {}",
                node.getName(), attempt, maxRetries, delayMs, retryEx.getMessage());
            return scheduleRetryAttempt(
                node, directInput, state, retryEx, start, attempt + 1, maxRetries, baseMs);
        });
    }

    // ─── 节点路由（O(1)）──────────────────────────────────────

    /**
     * 解析下一节点：conditionalNexts > next > 列表顺序
     * 使用预构建的 nodeListIndex（O(1)），避免 indexOf O(n)
     */
    private WorkflowNode resolveNextNode(WorkflowNode current, Object output,
                                          ImmutableExecutionState state,
                                          Map<String, WorkflowNode> nodeIndex,
                                          Map<String, Integer> nodeListIndex,
                                          List<WorkflowNode> nodeList) {
        // 1. 条件分支优先
        if (current.getConditionalNexts() != null) {
            for (ConditionalNext cn : current.getConditionalNexts()) {
                if (ExpressionEvaluator.evalBoolean(cn.getCondition(), output, state.getInputs())) {
                    return nodeIndex.get(cn.getTarget());
                }
            }
        }
        // 2. 显式 next
        if (current.getNext() != null) {
            return nodeIndex.get(current.getNext());
        }
        // 3. 列表顺序（O(1) 查找）
        int idx = nodeListIndex.getOrDefault(current.getId(), -1);
        return (idx >= 0 && idx < nodeList.size() - 1) ? nodeList.get(idx + 1) : null;
    }

    private Map<String, WorkflowNode> buildNodeIndex(List<WorkflowNode> nodes) {
        return nodes.stream().collect(Collectors.toMap(WorkflowNode::getId, n -> n,
            (a, b) -> { throw new DuplicateNodeIdException(a.getId()); },
            LinkedHashMap::new));
    }

    private Map<String, Integer> buildNodeListIndex(List<WorkflowNode> nodes) {
        Map<String, Integer> index = new HashMap<>(nodes.size() * 2);
        for (int i = 0; i < nodes.size(); i++) {
            index.put(nodes.get(i).getId(), i);
        }
        return index;
    }
}
```

### 3.3 引擎辅助方法

#### RetryScheduler（时间轮重试调度器）

```java
/**
 * 重试调度器 — 基于 Netty HashedWheelTimer（时间轮）
 *
 * ── 为什么不用 Thread.sleep() ──────────────────────────────────────────
 *   Thread.sleep() 在整个退避窗口（最长 30s）内独占线程：
 *   - 高并发下，多个节点同时等待重试 → 线程池快速耗尽
 *   - 业务线程空转，CPU/内存零贡献
 *
 * ── 为什么不用 ScheduledExecutorService.schedule() ─────────────────────
 *   基于最小堆（红黑树）实现：
 *   - 注册/取消 O(log n)，大量重试任务时性能下降
 *   - 时间精度受限于线程调度抖动
 *
 * ── 时间轮（HashedWheelTimer）优势 ────────────────────────────────────
 *   - 单调度线程推进轮盘，O(1) 注册/取消
 *   - 512 槽 × 100ms = 51.2s 内到期任务无哈希碰撞
 *   - 调度线程只触发事件，实际任务委托 retryWorker 异步执行
 *   - 调用方通过 CompletableFuture 获取结果，不阻塞业务线程
 *
 * ── 调用链 ────────────────────────────────────────────────────────────
 *   1. 注册到时间轮（O(1)，立即返回 Future）
 *   2. delayMs 后，轮盘线程触发槽 → submit 到 retryWorker
 *   3. retryWorker 执行 supplier → complete / completeExceptionally
 *   4. 调用方在 CompletableFuture 上 thenCompose 链式处理结果
 *                                   ↑ 不占用任何线程睡眠等待
 */
@Component
public class RetryScheduler {

    /**
     * 时间轮：100ms 精度，512 槽
     * 单实例全局共享（所有工作流重试共用一个轮盘，开销极低）
     */
    private final HashedWheelTimer wheelTimer = new HashedWheelTimer(
        r -> { Thread t = new Thread(r, "wf-retry-timer"); t.setDaemon(true); return t; },
        100, TimeUnit.MILLISECONDS,
        512
    );

    /**
     * 重试工作线程池（与时间轮线程分离）
     * 时间轮到期时只做 submit；实际函数调用在此线程池执行
     * 使用 CachedThreadPool + 虚拟线程友好（Java 21 可改为 VirtualThreadPerTaskExecutor）
     */
    private final Executor retryWorker = Executors.newCachedThreadPool(
        r -> { Thread t = new Thread(r, "wf-retry-worker"); t.setDaemon(true); return t; }
    );

    /**
     * 延迟 delayMs 后在 retryWorker 上异步执行 supplier，返回 CompletableFuture
     *
     * @param supplier 重试逻辑（抛出异常时 Future 以异常完成）
     * @param delayMs  延迟毫秒（≤ 0 则立即提交，不经过时间轮）
     */
    public <T> CompletableFuture<T> schedule(Supplier<T> supplier, long delayMs) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (delayMs <= 0) {
            // 无延迟：直接提交到 retryWorker，不经过时间轮
            retryWorker.execute(() -> executeSupplier(supplier, future));
        } else {
            // 有延迟：注册到时间轮；到期后 submit 到 retryWorker（时间轮线程不执行业务）
            wheelTimer.newTimeout(
                ignored -> retryWorker.execute(() -> executeSupplier(supplier, future)),
                delayMs, TimeUnit.MILLISECONDS
            );
        }
        return future;
    }

    private <T> void executeSupplier(Supplier<T> supplier, CompletableFuture<T> future) {
        try {
            future.complete(supplier.get());
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
    }

    @PreDestroy
    public void shutdown() {
        wheelTimer.stop();
    }
}
```

#### withTimeout（超时控制）

```java
/**
 * 带超时控制的函数执行辅助方法
 *
 * 设计说明：
 *   - 使用独立 ExecutorService（非 ForkJoinPool），避免阻塞 DAG 协程线程
 *   - timeoutMs <= 0 表示不限时，直接同步执行（避免无意义的 Future 开销）
 *   - 超时后 future.cancel(true) 会向执行线程发送中断信号，
 *     函数实现应检查 Thread.interrupted() 或使用响应中断的 I/O 操作
 */
private static final ExecutorService timeoutExecutor =
    Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "wf-timeout-worker");
        t.setDaemon(true);
        return t;
    });

private <T> T withTimeout(Supplier<T> supplier, int timeoutMs) {
    if (timeoutMs <= 0) {
        return supplier.get();  // 不限时，直接执行
    }
    Future<T> future = timeoutExecutor.submit(supplier::get);
    try {
        return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
        future.cancel(true);
        throw new WorkflowNodeException(
            "Execution timed out after " + timeoutMs + "ms", e);
    } catch (ExecutionException e) {
        Throwable cause = e.getCause();
        throw (cause instanceof RuntimeException)
            ? (RuntimeException) cause
            : new WorkflowNodeException("Execution failed", cause);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new WorkflowNodeException("Execution interrupted", e);
    }
}
```

#### executePartial（局部执行，重放专用）

```java
/**
 * 从指定节点列表开始执行（重放专用，不执行 Schema 校验）
 *
 * ⚠️ 仅供 DebugService.rerunFromNode() 调用，不对外暴露。
 * 传入的 replayState 已包含前置节点的输出，引擎从 nodesToRerun[0] 开始执行。
 *
 * @param nodesToRerun  从目标节点到末尾的节点列表
 * @param directInput   目标节点的直接入参（可由 overrideInput 覆盖）
 * @param replayState   已恢复的前置状态快照
 */
RerunResult executePartial(List<WorkflowNode> nodesToRerun,
                           Object directInput,
                           ImmutableExecutionState replayState) {
    Map<String, WorkflowNode> nodeIndex     = buildNodeIndex(nodesToRerun);
    Map<String, Integer>      nodeListIndex = buildNodeListIndex(nodesToRerun);
    List<NodeExecutionRecord> trace         = new ArrayList<>();

    ImmutableExecutionState state   = replayState;
    WorkflowNode            current = nodesToRerun.get(0);
    Object                  input   = directInput;

    while (current != null) {
        NodeExecutionRecord record = executeNode(current, input, state, trace);
        trace.add(record);

        if (record.getStatus() != NodeStatus.SKIPPED) {
            state = state.withNodeOutput(current.getId(), record.getOutput());
            input = record.getOutput();
        }
        current = resolveNextNode(current, input, state, nodeIndex, nodeListIndex, nodesToRerun);
    }

    return new RerunResult(input, state, trace);
}

/** 重放结果 */
public record RerunResult(
    Object                    finalOutput,
    ImmutableExecutionState   finalState,
    List<NodeExecutionRecord> trace
) {}
```

#### ExpressionEvaluator（条件表达式求值）

```java
/**
 * 条件表达式求值器
 * 用于 WorkflowNode.conditionalNexts 的路由条件判断
 *
 * 支持 SpEL（Spring Expression Language）
 *
 * 可用变量（在表达式中通过 # 前缀访问）：
 *   #output   — 当前节点的输出（Map 时字段可直接以 #fieldName 访问）
 *   #input    — 工作流原始入参（workflowInput）
 *
 * 示例：
 *   "#output['status'] == 'APPROVED'"
 *   "#input['amount'] > 1000 && #output['riskLevel'] == 'LOW'"
 *   "#output != null"
 */
public class ExpressionEvaluator {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /**
     * 求值布尔条件
     *
     * @param expression    SpEL 表达式
     * @param nodeOutput    当前节点输出（绑定为 #output；若为 Map，字段也会展开绑定）
     * @param workflowInput 工作流原始入参（绑定为 #input）
     * @return 条件为 true 时返回 true，表达式求值失败时返回 false（并记录 warn 日志）
     */
    public static boolean evalBoolean(String expression,
                                      Object nodeOutput,
                                      Map<String, Object> workflowInput) {
        if (expression == null || expression.isBlank()) return false;

        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("output", nodeOutput);
        ctx.setVariable("input",  workflowInput);

        // 若 nodeOutput 是 Map，将所有字段展开为变量（支持直接写 #status 而非 #output['status']）
        if (nodeOutput instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k instanceof String key) ctx.setVariable(key, v);
            });
        }

        try {
            Boolean result = PARSER.parseExpression(expression).getValue(ctx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (EvaluationException e) {
            log.warn("ExpressionEvaluator: evaluation failed [expr={}]: {}", expression, e.getMessage());
            return false;
        }
    }
}
```

### 3.4 DAG 并行执行器（Kotlin Coroutines）

```kotlin
/**
 * DAG 并行执行器 — 基于 Kotlin Coroutines
 * 无依赖关系的节点自动并行，有依赖的节点等待前置完成
 *
 * 类比：
 *   线性流 = 顺序的函数调用链
 *   DAG    = 并发调用多个独立函数，等待所有返回值后继续
 */
class DagExecutor(
    private val engine: WorkflowEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * 执行 DAG 工作流
     *
     * 算法：
     *   1. 拓扑排序，检测环
     *   2. 计算每个节点的 in-degree（未满足的依赖数）
     *   3. in-degree=0 的节点入就绪队列，并发执行
     *   4. 节点完成 → 更新状态快照 → 后继节点 in-degree--
     *   5. 重复直到所有节点完成
     *
     * ★ DAG 中的节点重试：
     *   engine.executeNode() 内部调用 executeWithRetry() → scheduleRetryAttempt()
     *   DagExecutor 的协程在 async { engine.executeNode(...) } 中挂起，
     *   等待期间当前协程让出协程线程，Kotlin delay() 语义与时间轮完全兼容，
     *   不阻塞任何平台线程。
     */
    suspend fun execute(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>
    ): EngineResult = coroutineScope {

        val lockedNodes = def.nodes.toMutableList()
        val nodeMap     = lockedNodes.associateBy { it.id }

        // 拓扑排序检测环
        val sortedIds = topologicalSort(lockedNodes)
            ?: throw CyclicDependencyException(def.id)

        // 初始状态
        var state = ImmutableExecutionState.start(def, rawInput)
        val inDegree = lockedNodes.associate { node ->
            node.id to (node.dependsOn?.size ?: 0)
        }.toMutableMap()

        // 就绪队列（in-degree = 0）
        val readyQueue = ArrayDeque(lockedNodes.filter { (inDegree[it.id] ?: 0) == 0 })
        val pendingResults = mutableMapOf<String, Deferred<NodeExecutionRecord>>()

        while (readyQueue.isNotEmpty() || pendingResults.isNotEmpty()) {
            // 并发启动所有就绪节点
            while (readyQueue.isNotEmpty()) {
                val node = readyQueue.removeFirst()
                val directInput = resolveDirectInput(node, state)
                pendingResults[node.id] = async(dispatcher) {
                    engine.executeNode(node, directInput, state)
                }
            }

            // 等待任一节点完成（select first completed）
            val (completedId, record) = awaitFirst(pendingResults)
            pendingResults.remove(completedId)

            // ★ 更新不可变状态快照（原子操作）
            state = state.withNodeOutput(completedId, record.output)

            // 更新后继节点的 in-degree，将就绪节点加入队列
            nodeMap[completedId]?.let { completedNode ->
                lockedNodes
                    .filter { it.dependsOn?.contains(completedId) == true }
                    .forEach { successor ->
                        val newDegree = (inDegree[successor.id] ?: 1) - 1
                        inDegree[successor.id] = newDegree
                        if (newDegree == 0) readyQueue.add(successor)
                    }
            }
        }

        EngineResult.success(state.getNodeOutput(sortedIds.last()), state)
    }

    // ─── DAG 辅助方法 ─────────────────────────────────────────

    /**
     * 拓扑排序（Kahn 算法），同时检测环
     *
     * @return 拓扑有序的节点 ID 列表；若存在环则返回 null
     *
     * 算法：
     *   1. 计算每个节点的入度（dependsOn 的数量）
     *   2. 将入度为 0 的节点加入队列
     *   3. 逐个出队，将后继节点入度 -1，入度降为 0 则入队
     *   4. 最终结果长度 < 节点总数 → 有环
     */
    private fun topologicalSort(nodes: List<WorkflowNode>): List<String>? {
        val inDegree  = mutableMapOf<String, Int>()
        val adjacency = mutableMapOf<String, MutableList<String>>()

        for (node in nodes) {
            inDegree.putIfAbsent(node.id, 0)
            adjacency.putIfAbsent(node.id, mutableListOf())
            node.dependsOn?.forEach { dep ->
                inDegree[node.id] = (inDegree[node.id] ?: 0) + 1
                adjacency.getOrPut(dep) { mutableListOf() }.add(node.id)
            }
        }

        val queue  = ArrayDeque(nodes.filter { (inDegree[it.id] ?: 0) == 0 }.map { it.id })
        val result = mutableListOf<String>()

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)
            adjacency[current]?.forEach { successor ->
                val newDegree = (inDegree[successor] ?: 1) - 1
                inDegree[successor] = newDegree
                if (newDegree == 0) queue.add(successor)
            }
        }

        // 结果长度与节点数一致 → 无环；否则有环
        return if (result.size == nodes.size) result else null
    }

    /**
     * 等待 pendingResults 中任意一个 Deferred 完成（select first completed）
     *
     * 使用 Kotlin coroutines select 原语，避免轮询：
     *   - 任一节点完成时立即返回，不阻塞其他节点的并发执行
     *   - 配合 DAG 调度循环实现"完成一个、推进后继"的模式
     *
     * @return Pair(completedNodeId, NodeExecutionRecord)
     */
    private suspend fun awaitFirst(
        pending: Map<String, Deferred<NodeExecutionRecord>>
    ): Pair<String, NodeExecutionRecord> = select {
        pending.forEach { (id, deferred) ->
            deferred.onAwait { record -> id to record }
        }
    }

    /**
     * 解析节点的直接入参
     * 线性流：取前驱节点的输出；DAG 起点：取 rawInput（保存在 state.inputs）
     */
    private fun resolveDirectInput(
        node: WorkflowNode,
        state: ImmutableExecutionState
    ): Any? {
        // 若节点有 dependsOn，则 directInput 为空（通过 NodeInput.declaredDeps 获取依赖）
        if (!node.dependsOn.isNullOrEmpty()) return null
        // 无 dependsOn 的节点：取当前 state 中最后一个有值的节点输出，或 rawInput
        return state.nodeOutputs.values.lastOrNull() ?: state.inputs
    }
}
```

---

## 4. Saga 补偿事务

### 4.1 设计思想

`FunctionResult` 的 `sideEffects` 字段显式记录每个节点产生的副作用，使引擎可以精确地逆序补偿：

```
正向执行：
  n1 → SideEffect(DB_UPDATE, inventory, {qty: -5}) ✅  入补偿栈
  n2 → SideEffect(RPC_CALL, order.create, {...})   ✅  入补偿栈
  n3 → SideEffect(MQ_SEND, payment.charge, {...})  ❌  触发回滚

逆序补偿：
  n2.compensate ← order.cancel(orderId)
  n1.compensate ← inventory.restore({qty: +5})
```

### 4.2 Saga 执行器

```java
/**
 * Saga 执行器 — 基于 FunctionResult.sideEffects 的精确补偿
 * 位于 fluxion-core，零框架依赖
 */
public class SagaExecutor {

    private final FunctionRegistry functionRegistry;

    public EngineResult execute(WorkflowDefinition def, Map<String, Object> rawInput) {
        ImmutableExecutionState state = ImmutableExecutionState.start(def, rawInput);
        List<WorkflowNode> lockedNodes  = new ArrayList<>(def.getNodes());

        // 补偿栈：记录已执行且有副作用的节点（用于逆序回滚）
        Deque<CompensationEntry> compensationStack = new ArrayDeque<>();

        Object directInput = rawInput;
        for (WorkflowNode node : lockedNodes) {
            try {
                NodeInput nodeInput = buildNodeInput(node, directInput, state);
                WorkflowFunction<Object> fn =
                    functionRegistry.resolve(node.getFunctionRef(), node.getType());

                FunctionResult<Object> result = fn.apply(nodeInput);
                state = state.withNodeOutput(node.getId(), result.output());
                directInput = result.output();

                // ★ 将副作用记录入补偿栈（仅有副作用的节点需要补偿）
                if (!result.sideEffects().isEmpty()) {
                    compensationStack.push(new CompensationEntry(
                        node, nodeInput, result.sideEffects()));
                }

            } catch (Exception ex) {
                log.error("Saga failed at node [{}], triggering compensation chain", node.getId());
                compensate(compensationStack);
                throw new SagaExecutionException("Saga failed at [" + node.getId() + "]", ex);
            }
        }
        return EngineResult.success(directInput, state);
    }

    private void compensate(Deque<CompensationEntry> stack) {
        while (!stack.isEmpty()) {
            CompensationEntry entry = stack.pop();
            WorkflowNode node = entry.node();
            try {
                if (node.getCompensateFunctionRef() == null) continue;

                WorkflowFunction<Object> compensateFn =
                    functionRegistry.resolve(node.getCompensateFunctionRef(), node.getType());

                // 补偿函数接收原始 NodeInput（包含当时的入参和副作用信息）
                NodeInput compensateInput = entry.originalInput()
                    .withNodeParams(Map.of("sideEffects", entry.sideEffects()));
                compensateFn.apply(compensateInput);

                log.info("Saga compensation OK: node=[{}]", node.getId());
            } catch (Exception e) {
                // 补偿失败：记录日志，继续补偿其他节点（不中断补偿链）
                log.error("Saga compensation FAILED for node=[{}], manual intervention required: {}",
                    node.getId(), e.getMessage());
            }
        }
    }
}

record CompensationEntry(WorkflowNode node, NodeInput originalInput, List<SideEffect> sideEffects) {}
```

---

## 5. 适配器层（Adapter Layer）

### 5.1 SPI 核心接口

适配器层职责：将各种协议的原生请求转换为 `UnifiedRequest`，执行完毕后将 `EngineResult` 转回原生响应。
引擎内部状态模型升级对适配器层**完全透明**——适配器无需感知 `ImmutableExecutionState`。

```java
/** 统一请求模型（协议无关） */
public record UnifiedRequest(
    String workflowId,          // 根据 protocol/path/method 路由解析
    Map<String, Object> params, // 请求参数（已解析）
    Map<String, String> headers,
    String protocol,            // HTTP / DUBBO / GRPC / KAFKA / MQ
    String rawBody              // 原始请求体（调试用）
) {}

/**
 * 工作流适配器 SPI
 * 每种协议对应一个实现，通过 Spring @Conditional 或 SPI 动态加载
 */
public interface WorkflowAdapter {
    /** 协议标识，对应 wf_definition.protocol */
    String protocol();

    /**
     * 动态注册路由（工作流发布时调用）
     * @param definition 工作流定义（含 path/method/protocol_config）
     */
    void registerRoute(WorkflowDefinition definition);

    /** 注销路由（工作流下线时调用） */
    void unregisterRoute(String workflowId);

    /** 将框架原生请求转换为 UnifiedRequest（适配器内部使用） */
    UnifiedRequest toUnifiedRequest(Object nativeRequest, WorkflowDefinition definition);
}
```

### 5.2 Spring MVC 适配器

#### 设计原则

- **零硬编码路径**：无任何固定前缀（如 `/wf/**`），所有路由由管理后台发布、配置中心下发
- **配置驱动**：admin 后台发布 HTTP 接口定义 → 写入 Nacos/Apollo → 本模块监听变更 → 动态注册/注销 Spring MVC 路由
- **SPI 可插拔**：`RouteConfigStore` SPI 解耦配置中心，开发者可接入 Nacos、Apollo 或自定义来源

#### 模块结构

```
fluxion-adapter-http/
└── fluxion-adapter-http-springmvc/
    ├── build.gradle.kts
    ├── src/
    │   └── .../springmvc/
    │       ├── spi/
    │       │   ├── HttpRouteDefinition.kt    ← 路由定义值对象
    │       │   └── RouteConfigStore.kt       ← SPI 接口 + RouteChangeListener
    │       ├── handler/
    │       │   └── WorkflowHttpHandler.kt    ← 统一处理器（非 @RestController）
    │       ├── registry/
    │       │   └── WorkflowRouteRegistry.kt  ← 动态 registerMapping/unregisterMapping
    │       └── config/
    │           └── SpringMvcAdapterAutoConfiguration.kt
    ├── fluxion-adapter-http-springmvc-nacos/     ← Nacos 实现（可选引入）
    ├── fluxion-adapter-http-springmvc-apollo/    ← Apollo 实现（可选引入）
    └── fluxion-adapter-http-springmvc-spring-boot/ ← 统一自动装配（可选引入）
```

#### SPI 定义

```kotlin
/**
 * HTTP 路由定义（admin 后台发布后通过配置中心下发）
 */
data class HttpRouteDefinition(
    val routeKey:   String,      // 唯一标识，格式：METHOD:path
    val path:       String,      // Spring 路径模式：/api/user/{id}
    val method:     String,      // HTTP 方法：GET / POST / PUT / DELETE / PATCH
    val workflowId: String,      // 对应工作流 ID
    val enabled:    Boolean = true
)

/**
 * 路由配置存储 SPI
 * 实现方：NacosRouteConfigStore / ApolloRouteConfigStore / 自定义
 */
interface RouteConfigStore {
    /** 启动时加载所有已激活路由 */
    fun loadAll(): List<HttpRouteDefinition>
    /** 注册变更监听，每次推送完整快照 */
    fun watch(listener: RouteChangeListener)
}

fun interface RouteChangeListener {
    fun onRoutesChanged(snapshot: List<HttpRouteDefinition>)
}
```

#### 动态路由注册（WorkflowRouteRegistry）

```kotlin
/**
 * 工作流动态路由注册表
 * 实现 RouteChangeListener，接收配置变更后 diff 执行注册/注销
 */
class WorkflowRouteRegistry(
    private val requestMappingHandlerMapping: RequestMappingHandlerMapping,
    private val dynamicHandler: WorkflowDynamicHandler
) : RouteChangeListener {

    // routeKey → RequestMappingInfo（用于 unregister）
    private val registeredMappings = ConcurrentHashMap<String, RequestMappingInfo>()
    // routeKey → workflowId（供拦截器查询）
    val routeToWorkflow = ConcurrentHashMap<String, String>()
    private val lock = ReentrantLock()

    override fun onRoutesChanged(snapshot: List<HttpRouteDefinition>) {
        lock.withLock {
            val newEnabled = snapshot.filter { it.enabled }
            val newKeys    = newEnabled.map { it.routeKey }.toSet()
            val oldKeys    = registeredMappings.keys.toSet()

            // 注销已删除或禁用的路由
            (oldKeys - newKeys).forEach { unregisterRoute(it) }

            // 注册新路由 / workflowId 变更时重注册
            newEnabled.forEach { route ->
                val existing = routeToWorkflow[route.routeKey]
                when {
                    existing == null              -> registerRoute(route)
                    existing != route.workflowId  -> { unregisterRoute(route.routeKey); registerRoute(route) }
                }
            }
        }
    }

    private fun registerRoute(route: HttpRouteDefinition) {
        val options = RequestMappingInfo.BuilderConfiguration().apply {
            patternParser = requestMappingHandlerMapping.patternParser ?: PathPatternParser.defaultInstance
        }
        val info = RequestMappingInfo.paths(route.path)
            .methods(RequestMethod.valueOf(route.method.uppercase()))
            .options(options).build()
        val handleMethod = WorkflowDynamicHandler::class.java
            .getMethod("handle", HttpServletRequest::class.java, String::class.java)

        requestMappingHandlerMapping.registerMapping(info, dynamicHandler, handleMethod)
        registeredMappings[route.routeKey] = info
        routeToWorkflow[route.routeKey]    = route.workflowId
    }

    private fun unregisterRoute(routeKey: String) {
        registeredMappings.remove(routeKey)?.let {
            requestMappingHandlerMapping.unregisterMapping(it)
            routeToWorkflow.remove(routeKey)
        }
    }
}
```

#### 请求处理（MvcWorkflowHandler）

```kotlin
/**
 * Spring MVC 动态路由处理器（非 @RestController）
 * 所有动态路由均指向此 Bean 的 handle() 方法
 * workflowId 由 WorkflowRouteInterceptor 通过 request attribute 注入
 */
class MvcWorkflowHandler(
    private val inboundRouter: WorkflowRouter,
    private val objectMapper:   ObjectMapper
) {
    companion object {
        const val ATTR_WORKFLOW_ID = "workflow.route.workflowId"
    }

    fun handle(
        request: HttpServletRequest,
        @RequestBody(required = false) body: String?
    ): ResponseEntity<Any> {
        val workflowId = request.getAttribute(ATTR_WORKFLOW_ID) as? String
            ?: return ResponseEntity.notFound().build()

        val unifiedRequest = UnifiedRequest(
            workflowId = workflowId,
            params     = buildParams(request, body),
            headers    = buildHeaders(request),
            protocol   = "HTTP",
            rawBody    = body
        )
        return try {
            ResponseEntity.ok(inboundRouter.execute(unifiedRequest).data)
        } catch (ex: WorkflowException) {
            handleWorkflowError(ex)
        }
    }
}
```

#### 自动配置（SpringMvcAdapterAutoConfiguration）

```kotlin
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet::class)
class SpringMvcAdapterAutoConfiguration {

    @Bean @ConditionalOnMissingBean
    fun mvcWorkflowHandler(router: WorkflowRouter, om: ObjectMapper) =
        MvcWorkflowHandler(router, om)

    @Bean @ConditionalOnMissingBean
    fun mvcRouteRegistry(
        @Lazy mapping: RequestMappingHandlerMapping,
        handler: MvcWorkflowHandler
    ) = MvcRouteRegistry(mapping, handler)

    /** ApplicationReadyEvent 后加载初始路由并开始监听 */
    @Bean @ConditionalOnBean(RouteConfigStore::class)
    fun workflowRouteInitializer(store: RouteConfigStore, registry: MvcRouteRegistry) =
        ApplicationListener<ApplicationReadyEvent> { _ ->
            registry.init(store.loadAll())
            store.watch(registry)
        }

    /** 拦截器：根据路径注入 workflowId → request attribute */
    @Bean
    fun workflowMvcConfigurer(registry: MvcRouteRegistry) =
        object : WebMvcConfigurer {
            override fun addInterceptors(r: InterceptorRegistry) {
                r.addInterceptor(object : HandlerInterceptor {
                    override fun preHandle(req: HttpServletRequest, res: HttpServletResponse, h: Any): Boolean {
                        registry.resolveWorkflowId(req.method, req.servletPath)
                            ?.let { req.setAttribute(ATTR_WORKFLOW_ID, it) }
                        return true
                    }
                }).addPathPatterns("/**")
            }
        }
}
```

#### Nacos 实现（fluxion-adapter-http-springmvc-nacos）

```kotlin
/**
 * Nacos 路由配置存储实现
 * 监听 Nacos DataId 变更，推送完整快照给 MvcRouteRegistry
 *
 * 配置：
 *   workflow.adapter.springmvc.nacos.data-id: workflow.http.routes  # DataId
 *   workflow.adapter.springmvc.nacos.group:   WORKFLOW               # Group
 *
 * Nacos 配置 JSON 格式：
 * { "routes": [
 *     { "routeKey": "POST:/api/user/login", "path": "/api/user/login",
 *       "method": "POST", "workflowId": "user-login-workflow", "enabled": true }
 * ] }
 */
class NacosRouteConfigStore(
    private val configService: ConfigService,
    private val dataId: String,
    private val group:  String,
    private val om:     ObjectMapper
) : RouteConfigStore {

    override fun loadAll(): List<HttpRouteDefinition> {
        val content = configService.getConfig(dataId, group, 5000) ?: return emptyList()
        return parseRoutes(content)
    }

    override fun watch(listener: RouteChangeListener) {
        configService.addListener(dataId, group, object : Listener {
            override fun receiveConfigInfo(content: String?) {
                listener.onRoutesChanged(if (content.isNullOrBlank()) emptyList() else parseRoutes(content))
            }
            override fun getExecutor(): Executor? = null
        })
    }
}
```

#### Apollo 实现（fluxion-adapter-http-springmvc-apollo）

```kotlin
/**
 * Apollo 路由配置存储实现
 * 监听 Apollo namespace key 变更，推送完整快照给 MvcRouteRegistry
 *
 * 配置：
 *   workflow.adapter.springmvc.apollo.namespace:  workflow.http.routes
 *   workflow.adapter.springmvc.apollo.routes-key: routes
 */
class ApolloRouteConfigStore(
    private val config:     Config,
    private val routesKey:  String,
    private val om:         ObjectMapper
) : RouteConfigStore {

    override fun loadAll(): List<HttpRouteDefinition> =
        config.getProperty(routesKey, null)?.let { parseRoutes(it) } ?: emptyList()

    override fun watch(listener: RouteChangeListener) {
        config.addChangeListener({ event ->
            if (event.isChanged(routesKey))
                listener.onRoutesChanged(event.getChange(routesKey)?.newValue
                    ?.let { parseRoutes(it) } ?: emptyList())
        }, setOf(routesKey))
    }
}
```

#### 装配链路

```
admin 后台发布 HTTP 接口定义
        ↓ 写入配置中心（JSON 格式路由列表）
    Nacos / Apollo
        ↓ 推送变更
 NacosRouteConfigStore / ApolloRouteConfigStore（RouteConfigStore SPI）
        ↓ onRoutesChanged(snapshot)
    MvcRouteRegistry（diff 计算）
        ↓ registerMapping / unregisterMapping
 RequestMappingHandlerMapping（Spring MVC，运行时动态路由）
        ↓ HTTP 请求到达 /api/user/login
 WorkflowRouteInterceptor → request.setAttribute(workflowId)
        ↓
 MvcWorkflowHandler.handle()
        ↓
 WorkflowRouter.execute(workflowId) → WorkflowEngine
```

### 5.3 Dubbo 适配器

```kotlin
/**
 * Dubbo 泛化调用适配器
 * 通过 ServiceConfig 动态暴露 GenericService，将 Dubbo 请求路由到工作流引擎
 */
@Component
class DubboWorkflowAdapter(
    private val inboundRouter: WorkflowRouter
) : WorkflowAdapter {

    private val exportedServices = ConcurrentHashMap<String, ServiceConfig<GenericService>>()

    override fun protocol() = "DUBBO"

    override fun registerRoute(definition: WorkflowDefinition) {
        val protocolConfig = definition.protocolConfig ?: return
        val interfaceName  = protocolConfig["interface"] as? String
            ?: "com.example.workflow.${definition.name.toCamelCase()}"

        val service = ServiceConfig<GenericService>().apply {
            interfaceClass = GenericService::class.java
            ref = GenericService { method, _, args ->
                val params = args?.firstOrNull()?.let { it as? Map<String, Any> } ?: emptyMap()
                val req = UnifiedRequest(definition.id, params, emptyMap(), "DUBBO", null)
                val result = inboundRouter.execute(req)
                if (result.isSuccess) result.data else throw WorkflowExecutionException(result.errorMsg)
            }
            this.interfaceName = interfaceName
            protocolConfig["version"]?.let { version = it as String }
            protocolConfig["group"]?.let  { group   = it as String }
        }
        service.export()
        exportedServices[definition.id] = service
        log.info("Dubbo service exported: interface={} → workflow[{}]", interfaceName, definition.id)
    }

    override fun unregisterRoute(workflowId: String) {
        exportedServices.remove(workflowId)?.unexport()
    }
}
```

### 5.4 gRPC 适配器

```kotlin
/**
 * gRPC 适配器
 * 将 gRPC 请求（Protobuf Any 或 JSON 模式）路由到工作流引擎
 * 使用 workflow_gateway.proto 定义的通用服务
 */
@Component
class GrpcWorkflowAdapter(
    private val inboundRouter: WorkflowRouter
) : WorkflowAdapter, WorkflowGatewayGrpc.WorkflowGatewayImplBase() {

    override fun protocol() = "GRPC"

    override fun execute(
        request: WorkflowRequest,
        responseObserver: StreamObserver<WorkflowResponse>
    ) {
        try {
            val params = objectMapper.readValue(request.paramsJson, Map::class.java) as Map<String, Any>
            val req    = UnifiedRequest(request.workflowId, params, request.headersMap, "GRPC", request.paramsJson)
            val result = inboundRouter.execute(req)
            val resp   = WorkflowResponse.newBuilder()
                .setSuccess(result.isSuccess)
                .setDataJson(objectMapper.writeValueAsString(result.data))
                .setExecutionId(result.executionId)
                .build()
            responseObserver.onNext(resp)
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.message).asException())
        }
    }

    override fun registerRoute(definition: WorkflowDefinition) {
        // gRPC 路由通过 workflowId 匹配，无需动态注册
        log.info("gRPC route activated: workflowId={}", definition.id)
    }

    override fun unregisterRoute(workflowId: String) {
        log.info("gRPC route deactivated: workflowId={}", workflowId)
    }
}
```

### 5.5 Kafka 消费者适配器

```kotlin
/**
 * Kafka 消费者适配器
 * 动态订阅 Topic，将消费到的消息作为工作流入参触发执行
 * 使用 KafkaListenerEndpointRegistry 替代静态 @KafkaListener（支持热更新）
 */
@Component
class KafkaConsumerWorkflowAdapter(
    private val registry: KafkaListenerEndpointRegistry,
    private val kafkaListenerContainerFactory: ConcurrentKafkaListenerContainerFactory<String, String>,
    private val inboundRouter: WorkflowRouter
) : WorkflowAdapter {

    private val containerIds = ConcurrentHashMap<String, String>() // workflowId → containerId

    override fun protocol() = "KAFKA"

    override fun registerRoute(definition: WorkflowDefinition) {
        val protocolConfig = definition.protocolConfig ?: return
        val topic    = protocolConfig["topic"] as? String ?: return
        val groupId  = protocolConfig["groupId"] as? String ?: "wf-consumer-${definition.id}"
        val containerId = "wf-kafka-${definition.id}"

        // 动态注册 KafkaMessageListenerContainer
        val endpoint = SimpleKafkaListenerEndpoint<String, String>().apply {
            id           = containerId
            setTopics(topic)
            this.groupId = groupId
            messageListener = MessageListener { record ->
                try {
                    val params = objectMapper.readValue(record.value(), Map::class.java) as Map<String, Any>
                    val req    = UnifiedRequest(definition.id, params,
                        mapOf("kafka-topic" to record.topic(), "kafka-offset" to record.offset().toString()),
                        "KAFKA", record.value())
                    inboundRouter.execute(req)
                } catch (e: Exception) {
                    log.error("Kafka workflow execution failed: workflowId={}, topic={}", definition.id, topic, e)
                }
            }
        }
        registry.registerListenerContainer(endpoint, kafkaListenerContainerFactory, true)
        containerIds[definition.id] = containerId
        log.info("Kafka consumer registered: topic={} → workflow[{}]", topic, definition.id)
    }

    override fun unregisterRoute(workflowId: String) {
        containerIds.remove(workflowId)?.let { containerId ->
            registry.getListenerContainer(containerId)?.apply {
                stop(); registry.unregisterListenerContainer(containerId)
            }
        }
    }
}
```

### 5.6 MQ 消息队列适配器（通用）

```kotlin
/**
 * 通用 MQ 适配器 SPI（RocketMQ / RabbitMQ 均可实现）
 * 与 Kafka 适配器设计对称，协议标识为 "MQ"
 */
interface MqConsumerAdapter {
    fun subscribe(topic: String, groupId: String, handler: (Map<String, Any>) -> Unit)
    fun unsubscribe(topic: String, groupId: String)
}

/** RocketMQ 实现示例 */
@Component
@ConditionalOnClass(name = ["org.apache.rocketmq.spring.core.RocketMQTemplate"])
class RocketMqConsumerAdapter(
    private val defaultMQPushConsumer: DefaultMQPushConsumer
) : MqConsumerAdapter {
    override fun subscribe(topic: String, groupId: String, handler: (Map<String, Any>) -> Unit) {
        defaultMQPushConsumer.subscribe(topic, "*")
        defaultMQPushConsumer.registerMessageListener(MessageListenerConcurrently { msgs, _ ->
            msgs.forEach { msg ->
                val params = objectMapper.readValue(String(msg.body), Map::class.java) as Map<String, Any>
                handler(params)
            }
            ConsumeConcurrentlyStatus.CONSUME_SUCCESS
        })
    }
    override fun unsubscribe(topic: String, groupId: String) {
        defaultMQPushConsumer.unsubscribe(topic)
    }
}
```

### 5.7 执行版本快照（热加载一致性）

```
请求到达时序：
  t0: R1 到达 → WorkflowEngine.execute() 内 ImmutableExecutionState.start(def, input)
               → 此刻 lockedNodes = def.getNodes()（快照锁定，version=42）
  t1: 管理员发布新版本 → 缓存刷新 → definition.version = 43
  t2: R1 继续执行 → 使用 lockedNodes(v42)，完全不受影响
  t3: 新请求 R2 到达 → 读取新 definition(v43) → lockedNodes = v43 的节点列表

无需额外锁或版本快照机制 — ImmutableExecutionState.start() 创建时已原子锁定版本。
```

---

## 6. 函数注册中心与内置函数

### 6.1 注册机制（优先级修正）

```java
/**
 * 函数注册中心
 *
 * 优先级：custom > builtin > script > external
 *
 * ★ custom 优先于 builtin：
 *   允许用户通过自定义函数替换/覆盖内置函数，
 *   §6.2 内置函数清单中每个函数标注了「可替换」，依赖此优先级生效。
 */
public class FunctionRegistry {

    // 各类函数存储（ConcurrentHashMap 保证并发安全）
    private final Map<String, WorkflowFunction<?>> customFunctions   = new ConcurrentHashMap<>();
    private final Map<String, WorkflowFunction<?>> builtinFunctions  = new ConcurrentHashMap<>();
    private final Map<String, ScriptFunction>      scriptFunctions   = new ConcurrentHashMap<>();
    private final Map<String, ExternalServiceRef>  externalRefs      = new ConcurrentHashMap<>();

    /**
     * 注册自定义函数（支持覆盖内置函数）
     *
     * @param name 函数引用名（如 "validate:orderAmount"）
     * @param meta 函数元信息（Lambda 注册时必须显式传入，避免 getSimpleName() 返回匿名类名）
     * @param fn   函数实现
     */
    public <O> void register(String name, FunctionMeta meta, WorkflowFunction<O> fn) {
        customFunctions.put(name, fn);
        log.info("Registered custom function: {}", name);
    }

    /** 便捷注册（Lambda 场景，meta 从 name 自动生成） */
    public <O> void register(String name, WorkflowFunction<O> fn) {
        register(name, FunctionMeta.of(name), fn);
    }

    /**
     * 批量注册自定义函数（Spring @Component 扫描后调用）
     * 通过 WorkflowFunction.meta().getName() 推断函数名（⚠️ Lambda 实现不可用此方法）
     */
    public void registerAll(List<WorkflowFunction<?>> functions) {
        functions.forEach(fn -> {
            String name = fn.meta().getName();
            if (name == null || name.isBlank() || name.contains("$Lambda")) {
                throw new IllegalArgumentException(
                    "Cannot registerAll() for anonymous/Lambda function. " +
                    "Use register(name, fn) with explicit name.");
            }
            customFunctions.put(name, fn);
            log.info("Registered custom function (batch): {}", name);
        });
    }

    /**
     * 注销函数（热加载期间用于移除废弃函数）
     * ⚠️ 不可注销内置函数（builtinFunctions），内置函数在应用启动时由引擎框架管理
     */
    public void unregister(String name) {
        if (customFunctions.remove(name) != null) {
            log.info("Unregistered custom function: {}", name);
        } else if (scriptFunctions.remove(name) != null) {
            log.info("Unregistered script function: {}", name);
        } else {
            log.warn("Attempted to unregister non-existent function: {}", name);
        }
    }

    /** 查询所有已注册的函数名（按类型分组，供 Admin API 展示） */
    public Map<String, Set<String>> listAll() {
        return Map.of(
            "custom",   new HashSet<>(customFunctions.keySet()),
            "builtin",  new HashSet<>(builtinFunctions.keySet()),
            "script",   new HashSet<>(scriptFunctions.keySet()),
            "external", new HashSet<>(externalRefs.keySet())
        );
    }

    /** 查询函数是否已注册 */
    public boolean contains(String name) {
        return customFunctions.containsKey(name)
            || builtinFunctions.containsKey(name)
            || scriptFunctions.containsKey(name)
            || externalRefs.containsKey(name);
    }

    @SuppressWarnings("unchecked")
    public WorkflowFunction<Object> resolve(String functionRef, NodeType type) {
        // 1. 自定义函数（可覆盖内置）
        if (customFunctions.containsKey(functionRef)) return adapt(customFunctions.get(functionRef));
        // 2. 内置函数
        if (builtinFunctions.containsKey(functionRef)) return adapt(builtinFunctions.get(functionRef));
        // 3. 脚本函数（动态加载）
        if (scriptFunctions.containsKey(functionRef)) return adaptScript(scriptFunctions.get(functionRef));
        // 4. 外部服务引用
        if (externalRefs.containsKey(functionRef)) return adaptExternal(externalRefs.get(functionRef));
        throw new FunctionNotFoundException(functionRef);
    }
}
```

### 6.2 内置函数完整目录

所有内置函数均实现 `WorkflowFunction<O>` 接口，通过 `FunctionRegistry` 注册，名称前缀为 `builtin:`。

| 函数名 | 输出类型 | 职责 | 可替换 |
|--------|---------|------|--------|
| `builtin:paramValidate` | `Object`（透传） | JSON Schema 校验 | ✓ |
| `builtin:dbQuery` | `List<Map>` | 动态 SELECT | ✓ |
| `builtin:dbQueryOne` | `Map<String,Object>` | SELECT 返回单行 | ✓ |
| `builtin:dbInsert` | `Long`（主键） | 参数化 INSERT | ✓ |
| `builtin:dbUpdate` | `Integer`（affected） | 参数化 UPDATE | ✓ |
| `builtin:dbDelete` | `Integer`（affected） | 参数化 DELETE | ✓ |
| `builtin:httpGet` | `Map<String,Object>` | HTTP GET 调用外部 API | ✓ |
| `builtin:httpPost` | `Map<String,Object>` | HTTP POST 调用外部 API | ✓ |
| `builtin:jsonTransform` | `Object` | JMESPath/JSONata 数据转换 | ✓ |
| `builtin:conditionBranch` | `String`（目标节点 ID） | 条件分支计算 | ✓ |
| `builtin:loopAggregator` | `List<Object>` | 并行结果聚合 | ✓ |
| `builtin:paginate` | `PageResult` | 分页封装 | ✓ |
| `builtin:mqPublish` | `String`（messageId） | 发送 MQ 消息 | ✓ |
| `builtin:cacheGet` | `Object` | 读取缓存 | ✓ |
| `builtin:cacheSet` | `Boolean` | 写入缓存 | ✓ |
| `builtin:responseWrapper` | `ApiResponse` | 统一响应封装 | ✓ |
| `builtin:errorWrapper` | `ApiResponse` | 错误响应封装 | ✓ |

```java
/**
 * 内置：参数校验函数
 * 不再依赖 WorkflowContext，只使用 NodeInput
 */
@Component
public class ParamValidateFunction implements WorkflowFunction<Object> {

    private final SchemaValidator schemaValidator;

    @Override
    public FunctionResult<Object> apply(NodeInput input) {
        // 从 nodeParams 获取要校验的 Schema（非运行时数据）
        JsonSchema schema = (JsonSchema) input.nodeParams().get("schema");

        // 从 workflowInput 获取要校验的数据（不可变原始入参）
        Map<String, Object> toValidate = input.workflowInput();

        ValidationResult result = schemaValidator.validate(schema, toValidate);
        if (!result.isValid()) {
            throw new InvalidParamException(result.getErrors());
        }
        // 校验通过，透传入参（无副作用）
        return FunctionResult.success(input.directInput());
    }

    @Override
    public FunctionMeta meta() {
        return FunctionMeta.of("builtin:paramValidate");
    }
}

/**
 * 内置：DB 查询函数
 * nodeParams 支持：table / columns / conditions / orderBy / limit
 */
@Component
public class DbQueryFunction implements WorkflowFunction<List<Map<String, Object>>> {

    private final DynamicSqlGenerator sqlGenerator;
    private final JdbcTemplate        jdbcTemplate;

    @Override
    public FunctionResult<List<Map<String, Object>>> apply(NodeInput input) {
        String table   = (String) input.nodeParams().get("table");
        List<String> columns = (List<String>) input.nodeParams().getOrDefault("columns", List.of());

        // conditions 来自 nodeParams（静态约束） + directInput（运行时过滤）
        List<SqlCondition> conditions = mergeConditions(input);

        SqlWithParams sql = sqlGenerator.generateSelect(table, columns, conditions);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.getSql(), sql.getParams());
        return FunctionResult.success(rows);
    }
}

/**
 * 内置：DB 更新函数（显式声明副作用，支持 Saga 补偿）
 * nodeParams 支持：table / setFields / conditions / compensateFunctionRef
 */
@Component
public class DbUpdateFunction implements WorkflowFunction<Integer> {

    private final DynamicSqlGenerator sqlGenerator;
    private final JdbcTemplate        jdbcTemplate;

    @Override
    public FunctionResult<Integer> apply(NodeInput input) {
        String table  = (String) input.nodeParams().get("table");
        String compensateRef = (String) input.nodeParams().get("compensateFunctionRef");

        // 查询更新前的快照（用于 Saga 补偿）
        Map<String, Object> beforeSnapshot = queryBeforeSnapshot(table, input);
        int affected = doUpdate(input);

        // ★ 显式声明副作用（供 Saga 补偿使用）
        SideEffect sideEffect = new SideEffect("DB_UPDATE", table, beforeSnapshot, compensateRef);
        return FunctionResult.successWithEffects(affected, List.of(sideEffect));
    }
}

/**
 * 内置：HTTP 调用函数
 * nodeParams 支持：url / method / headers / connectTimeoutMs / readTimeoutMs
 * 入参 directInput 作为请求 body（POST）或 query params（GET）
 */
@Component
public class HttpCallFunction implements WorkflowFunction<Map<String, Object>> {

    private final RestTemplate restTemplate;

    @Override
    public FunctionResult<Map<String, Object>> apply(NodeInput input) {
        String url    = (String) input.nodeParams().get("url");
        String method = (String) input.nodeParams().getOrDefault("method", "POST");

        ResponseEntity<Map> response = switch (method.toUpperCase()) {
            case "GET"  -> restTemplate.getForEntity(
                buildUri(url, input.directInput()), Map.class);
            case "POST" -> restTemplate.postForEntity(
                url, input.directInput(), Map.class);
            default -> throw new UnsupportedHttpMethodException(method);
        };

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ExternalServiceException(url, response.getStatusCode().value());
        }
        // HTTP 调用有副作用（外部状态变更），声明 RPC_CALL
        return FunctionResult.successWithEffects(
            response.getBody(),
            List.of(new SideEffect("RPC_CALL", url, input.directInput(), null))
        );
    }
}

/**
 * 内置：JSON 转换函数（JMESPath / JSONata 表达式）
 * nodeParams 支持：engine（jmespath/jsonata）/ expression
 */
@Component
public class JsonTransformFunction implements WorkflowFunction<Object> {

    private final JmesPathEngine  jmesPath;
    private final JsonataEngine   jsonata;

    @Override
    public FunctionResult<Object> apply(NodeInput input) {
        String engine     = (String) input.nodeParams().getOrDefault("engine", "jmespath");
        String expression = (String) input.nodeParams().get("expression");
        Object data       = input.directInput();

        Object transformed = switch (engine.toLowerCase()) {
            case "jmespath" -> jmesPath.search(expression, data);
            case "jsonata"  -> jsonata.evaluate(expression, data);
            default -> throw new UnsupportedTransformEngineException(engine);
        };
        // 纯转换，无副作用
        return FunctionResult.success(transformed);
    }
}

/**
 * 内置：响应封装函数（统一 API 响应格式）
 * nodeParams 支持：code（默认 200）/ message（默认 "success"）
 */
@Component
public class ResponseWrapperFunction implements WorkflowFunction<ApiResponse<Object>> {

    @Override
    public FunctionResult<ApiResponse<Object>> apply(NodeInput input) {
        int    code    = (int)    input.nodeParams().getOrDefault("code",    200);
        String message = (String) input.nodeParams().getOrDefault("message", "success");
        return FunctionResult.success(ApiResponse.of(code, message, input.directInput()));
    }
}

/**
 * 内置：MQ 消息发布函数（显式副作用 MQ_SEND）
 * nodeParams 支持：topic / tag / keys / delayLevel
 */
@Component
public class MqPublishFunction implements WorkflowFunction<String> {

    private final MqProducerRegistry mqProducerRegistry;

    @Override
    public FunctionResult<String> apply(NodeInput input) {
        String topic = (String) input.nodeParams().get("topic");
        String messageId = mqProducerRegistry.send(topic, input.directInput());

        // ★ MQ 发送是副作用，需要 Saga 补偿（发送撤回消息）
        SideEffect sideEffect = new SideEffect("MQ_SEND", topic, input.directInput(),
            (String) input.nodeParams().get("compensateFunctionRef"));
        return FunctionResult.successWithEffects(messageId, List.of(sideEffect));
    }
}
```

---

## 7. 调试系统

### 7.1 无状态调试

`ImmutableExecutionState` 天然可序列化，`DebugSnapshot` 直接持有状态快照：

```java
/**
 * 调试状态快照
 * ImmutableExecutionState 本身就是快照，直接作为 DebugSnapshot 的核心
 */
public record DebugSnapshot(
    String workflowId,
    int workflowVersion,
    /** ★ 核心：完整的不可变执行状态（包含所有已执行节点的输出） */
    ImmutableExecutionState executionState,
    List<NodeExecutionRecord> traces,
    Set<String> breakpoints,
    Map<String, Object> mockServices,
    String pausedAtNodeId,
    int nextNodeIndex
) {
    /** 从 DebugSnapshot 恢复调试上下文（无服务端 Session） */
    public static DebugContext restore(DebugSnapshot snap, WorkflowDefinition def) {
        if (snap.workflowVersion() != def.getVersion()) {
            throw new WorkflowVersionMismatchException(snap.workflowId());
        }
        return new DebugContext(snap.executionState(), snap.mockServices(),
            snap.breakpoints(), snap.traces(), snap.nextNodeIndex());
    }
}
```

### 7.2 重放机制（利用不可变状态精确恢复）

```java
/**
 * 重放指定节点
 * ★ 无需重新执行前置节点，直接从 executionState.nodeOutputs 恢复
 */
public RerunResult rerunFromNode(String executionId, String targetNodeId, Object overrideInput) {

    // 1. 加载原始执行的完整状态快照（ImmutableExecutionState）
    EngineResult originalResult = executionLogRepository.findByExecutionId(executionId);
    ImmutableExecutionState originalState = originalResult.getFinalState();

    // 2. 找到目标节点的前置状态
    //    ★ 不需要重跑前置节点——直接从不可变快照中读取
    WorkflowDefinition def = loadDefinition(executionId);
    int targetIndex = findNodeIndex(def, targetNodeId);

    // 3. 构建重放起始状态：保留目标节点之前的所有节点输出
    Map<String, Object> priorOutputs = originalState.getNodeOutputs().entrySet().stream()
        .filter(e -> isBeforeNode(def, e.getKey(), targetNodeId))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    ImmutableExecutionState replayState = ImmutableExecutionState.start(def, originalState.getInputs())
        .mergeNodeOutputs(priorOutputs);

    // 4. 从目标节点开始重新执行
    Object directInput = overrideInput != null ? overrideInput
        : originalState.getNodeOutput(getPrevNodeId(def, targetNodeId));

    List<WorkflowNode> nodesToRerun = def.getNodes().subList(targetIndex, def.getNodes().size());
    return engine.executePartial(nodesToRerun, directInput, replayState);
}
```

---

## 8. 脚本引擎

脚本函数通过 `WorkflowFunction<Object>` 接口与引擎对接，`NodeInput` 作为脚本执行环境的唯一数据来源。

### 8.1 ScriptFunction 基类

```java
/**
 * 脚本函数基类
 * 双模式：initScript（初始化阶段，可建立状态）+ executeScript（每次调用，只读状态）
 *
 * ⚠️ scriptState 并发安全约束：
 *   initScript 在应用启动时单线程执行一次，结果存入 scriptState。
 *   executeScript 并发执行，只能"读取" scriptState，严禁写入。
 *   若 Groovy 脚本需要在 executeScript 中"修改"状态，应通过返回值传递，
 *   而非直接写 scriptState（会导致数据竞争）。
 */
public abstract class ScriptFunction implements WorkflowFunction<Object> {

    /** 脚本初始化阶段产生的只读状态（initScript 写入，executeScript 只读） */
    protected volatile Map<String, Object> scriptState = Collections.emptyMap();

    private final String functionName;
    private final String language;

    protected ScriptFunction(String functionName, String language) {
        this.functionName = functionName;
        this.language     = language;
    }

    /**
     * 初始化脚本（可选）
     * 在函数注册时执行一次，可以：加载资源、预热缓存、建立连接池
     * 注意：不能在此处启动后台线程（避免与引擎生命周期耦合）
     */
    public void init(String initScript, Map<String, Object> initParams) {
        // 子类实现
    }

    @Override
    public FunctionMeta meta() {
        return FunctionMeta.of(functionName);
    }
}
```

### 8.2 Groovy 脚本引擎

```java
/**
 * Groovy 脚本函数
 *
 * 安全沙箱策略：
 *   Level 1（默认）：SecureASTCustomizer 禁用危险包（仅 AST 层面，无法阻止反射）
 *   Level 2（推荐生产）：加载 Groovy 脚本时使用独立 ClassLoader，
 *                        配合 SecurityManager 限制文件/网络访问
 *   Level 3（最高）：在子进程中执行脚本（性能损耗大，适用于完全不受信任的用户脚本）
 *
 * 安全级别对照：
 *   内部开发者编写的脚本 → Level 1
 *   经审核的外部合作方脚本 → Level 2
 *   完全不受信任的用户输入 → Level 3（或禁用脚本功能）
 */
@Component
public class GroovyScriptFunction extends ScriptFunction {

    /** Groovy 脚本编译缓存（functionName → CompiledScript） */
    private final LoadingCache<String, Script> scriptCache;
    private final GroovyShell                  secureShell;
    private final GroovyShell                  initShell;

    public GroovyScriptFunction() {
        super("groovy", "Groovy");
        this.secureShell = buildSecureShell();
        this.initShell   = new GroovyShell();

        this.scriptCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build(this::compileScript);
    }

    @Override
    public void init(String initScript, Map<String, Object> initParams) {
        if (initScript == null || initScript.isBlank()) return;
        Binding binding = new Binding();
        initParams.forEach(binding::setVariable);
        Script compiled = initShell.parse(initScript);
        compiled.setBinding(binding);
        Object result = compiled.run();
        // ★ scriptState 只在 init 阶段写入，之后只读
        if (result instanceof Map) {
            this.scriptState = Collections.unmodifiableMap((Map<String, Object>) result);
        }
    }

    @Override
    public FunctionResult<Object> apply(NodeInput input) {
        String scriptContent = (String) input.nodeParams().get("script");
        String functionName  = (String) input.nodeParams().get("name");

        Script script = scriptCache.get(functionName + ":" + scriptContent.hashCode());
        Binding binding = buildBinding(input);
        script.setBinding(binding);

        try {
            Object result = script.run();
            return FunctionResult.success(result);
        } catch (SecurityException e) {
            throw new ScriptSecurityException(functionName, e);
        }
    }

    private Binding buildBinding(NodeInput input) {
        Binding binding = new Binding();
        // ★ scriptState 以只读视图注入（防止脚本写入）
        binding.setVariable("state",  Collections.unmodifiableMap(scriptState));
        // NodeInput 各字段显式注入，脚本不能访问 ImmutableExecutionState
        binding.setVariable("input",  input.directInput());
        binding.setVariable("deps",   input.declaredDeps());
        binding.setVariable("params", input.nodeParams());
        binding.setVariable("wfInput",input.workflowInput());
        binding.setVariable("meta",   input.meta());
        return binding;
    }

    private GroovyShell buildSecureShell() {
        CompilerConfiguration config = new CompilerConfiguration();
        SecureASTCustomizer secure   = new SecureASTCustomizer();
        // 禁止导入危险包（AST 层面，注意局限性：无法阻止反射调用）
        secure.setPackageAllowed(false);
        secure.setIndirectImportCheckEnabled(true);
        secure.setImportsWhitelist(List.of(
            "java.util.*", "java.lang.*", "groovy.json.*"
        ));
        // 禁用系统调用语法
        secure.setTokensBlacklist(List.of(
            org.codehaus.groovy.syntax.Types.KEYWORD_WHILE // 防止无限循环
        ));
        config.addCompilationCustomizers(secure);
        return new GroovyShell(config);
    }

    private Script compileScript(String key) {
        // key = "functionName:scriptHash"
        String scriptContent = scriptRegistry.getScriptContent(key.split(":")[0]);
        return secureShell.parse(scriptContent);
    }
}
```

### 8.3 JS 脚本引擎（GraalVM Polyglot）

```java
/**
 * JavaScript 脚本函数（GraalVM Polyglot）
 * 支持 ES2022+，通过 sandbox 限制 I/O 访问
 */
@Component
@ConditionalOnClass(name = "org.graalvm.polyglot.Context")
public class JsScriptFunction extends ScriptFunction {

    @Override
    public FunctionResult<Object> apply(NodeInput input) {
        String script = (String) input.nodeParams().get("script");

        // 每次执行创建独立 Context（无状态共享）
        try (Context context = Context.newBuilder("js")
                .allowAllAccess(false)        // 禁止访问 Java API
                .allowIO(IOAccess.NONE)        // 禁止文件/网络 I/O
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .build()) {

            context.getBindings("js").putMember("input",   input.directInput());
            context.getBindings("js").putMember("deps",    input.declaredDeps());
            context.getBindings("js").putMember("params",  input.nodeParams());
            context.getBindings("js").putMember("wfInput", input.workflowInput());

            Value result = context.eval("js", script);
            return FunctionResult.success(polyglotToJava(result));
        }
    }

    private Object polyglotToJava(Value value) {
        if (value.isNull())   return null;
        if (value.isBoolean()) return value.asBoolean();
        if (value.isNumber()) return value.asDouble();
        if (value.isString()) return value.asString();
        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) list.add(polyglotToJava(value.getArrayElement(i)));
            return list;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            value.getMemberKeys().forEach(k -> map.put(k, polyglotToJava(value.getMember(k))));
            return map;
        }
        return value.toString();
    }
}
```

---

## 9. Redis 函数体系

### 9.1 设计原则

核心设计：**`RedisClientAdapter` SPI** 屏蔽具体 Redis 客户端（Lettuce / Jedis / Redisson），
所有 Redis 操作统一通过 **`builtin:redisCommand`** 一个函数完成，避免函数爆炸（原 50+ 个 Layer1/2/3 函数）。

```
fluxion-redis/                               ← 父目录（纯目录，无 build.gradle.kts）
├── fluxion-redis-core/                      ← 零 Spring 依赖（SPI + function）
│   └── .../redis/
│       ├── spi/RedisClientAdapter.kt        ← SPI 接口
│       ├── spi/RedisRawCommand.kt           ← Pipeline 命令值对象
│       └── function/RedisCommandFunction.kt ← builtin:redisCommand
├── fluxion-redis-spring-boot/               ← Spring Boot 装配桥接层
│   └── .../redis/config/RedisWorkflowAutoConfiguration.kt
├── fluxion-redis-lettuce/                   ← Lettuce 适配器（可选引入）
│   └── .../redis/lettuce/LettuceRedisAdapter.kt
├── fluxion-redis-redisson/                  ← Redisson 适配器（可选引入）
│   └── .../redis/redisson/RedissonRedisAdapter.kt
└── fluxion-redis-spring-data/               ← Spring Data Redis 适配器（可选引入）
    └── .../redis/springdata/SpringDataRedisAdapter.kt
```

### 9.2 RedisClientAdapter SPI

```java
/**
 * Redis 客户端适配器 SPI — 屏蔽 Lettuce / Jedis / Redisson / Spring Data Redis 差异
 *
 * 设计原则：
 *   - SPI 只有 execute() / pipeline() / eval() 三个方法
 *   - 每个适配器自行决定如何将命令字符串映射到具体客户端 API
 *   - 切换 Redis 客户端只需更换 Adapter 实现，工作流配置零修改
 */
public interface RedisClientAdapter {
    /**
     * 执行单条 Redis 命令
     * @param command 命令名（GET / HSET / ZADD / LPUSH / DEL 等）
     * @param key     Redis Key（已完成模板变量替换）
     * @param args    命令参数列表（语义与 Redis CLI 一致）
     */
    Object execute(String command, String key, List<String> args);

    /** Pipeline 批量执行（减少 RTT） */
    List<Object> pipeline(List<RedisRawCommand> commands);

    /** Lua 脚本执行（EVAL） */
    Object eval(String script, List<String> keys, List<String> args);
}
```

### 9.3 通用 Redis 命令函数（builtin:redisCommand）

```java
/**
 * 通用 Redis 命令函数 — 一个函数支持所有 Redis 命令
 *
 * NodeParams（节点配置）：
 *   - command:  Redis 命令（GET / HSET / ZADD / LPUSH / XADD / DEL / EVAL / PIPELINE）
 *   - key:      Redis Key（支持模板变量 {paramName}，从 directInput Map 中取值）
 *   - args:     命令参数列表 List<String>
 *   - script:   Lua 脚本（command=EVAL 时必填）
 *   - commands: Pipeline 命令列表（command=PIPELINE 时使用）
 *
 * Key 模板替换规则：
 *   模板：user:{region}:{userId}
 *   输入：{ region:"cn", userId:123 }
 *   结果：user:cn:123
 *   优先级：nodeParams > directInput(Map) > workflowInput
 */
public class RedisCommandFunction implements WorkflowFunction<Object> {

    private final RedisClientAdapter redisAdapter;

    @Override
    public FunctionResult<Object> apply(NodeInput input) {
        String command = ((String) input.param("command")).toUpperCase();
        String rawKey  = input.param("key");

        Object result = switch (command) {
            case "PIPELINE" -> redisAdapter.pipeline(buildPipelineCommands(input));
            case "EVAL"     -> {
                String script = input.param("script");
                List<String> keys = resolveList(input.param("keys"));
                List<String> args = resolveList(input.param("args"));
                yield redisAdapter.eval(script, keys, args);
            }
            default -> {
                String resolvedKey = resolveKey(rawKey, input);
                List<String> args  = resolveList(input.param("args"));
                yield redisAdapter.execute(command, resolvedKey, args);
            }
        };
        return FunctionResult.success(result);
    }
}
```

#### 使用示例（节点配置）

```json
// 普通命令：SET key value EX 3600
{
  "functionRef": "builtin:redisCommand",
  "nodeParams": {
    "command": "SET",
    "key": "user:{userId}:token",
    "args": ["${token}", "EX", "3600"]
  }
}

// ZADD 排行榜
{
  "functionRef": "builtin:redisCommand",
  "nodeParams": {
    "command": "ZADD",
    "key": "ranking:daily:{date}",
    "args": ["${score}", "${userId}"]
  }
}

// Lua 脚本（分布式锁）
{
  "functionRef": "builtin:redisCommand",
  "nodeParams": {
    "command": "EVAL",
    "script": "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
    "keys": ["lock:{orderId}"],
    "args": ["${lockToken}"]
  }
}

// Pipeline 批量操作
{
  "functionRef": "builtin:redisCommand",
  "nodeParams": {
    "command": "PIPELINE",
    "commands": [
      { "command": "GET",  "key": "user:{userId}:profile" },
      { "command": "INCR", "key": "user:{userId}:visit_count" }
    ]
  }
}
```

### 9.4 具体适配器实现

#### Lettuce 适配器（fluxion-redis-lettuce）

```kotlin
// application.yml 配置
workflow:
  redis:
    uri: redis://localhost:6379/0  # 支持 sentinel: / rediss: 等协议

// 自动装配条件：classpath 有 io.lettuce.core.RedisClient
// 优先级：若容器中已有 RedisClientAdapter Bean，则不注册
```

Lettuce 适配器使用 `StatefulRedisConnection.sync()` 执行单命令，`async()` 配合 `setAutoFlushCommands(false)` 实现 Pipeline，支持 100+ Redis 命令。

#### Redisson 适配器（fluxion-redis-redisson）

```kotlin
// application.yml 配置
workflow:
  redis:
    uri: redis://localhost:6379/0

// 自动装配条件：classpath 有 org.redisson.api.RedissonClient
// Redisson 额外优势：内置 RedLock 分布式锁（LOCK / TRYLOCK / UNLOCK 命令原生支持）
```

Redisson 适配器通过类型化对象（`RBucket`/`RMap`/`RScoredSortedSet` 等）实现命令分发，`RBatch` 实现 Pipeline，`RScript` 执行 Lua。

### 9.5 Key Schema 管理（wf_resource_template）

Redis Key 命名规范通过 `wf_resource_template` 表统一管理，无需硬编码在函数中：

```yaml
# wf_resource_template 表数据示例（resource_type=REDIS_KEY）
resources:
  - name: "user:session"
    resource_type: "REDIS_KEY"
    template: "sess:{namespace}:{userId}"
    config: {ttlSeconds: 7200}

  - name: "order:lock"
    resource_type: "REDIS_KEY"
    template: "lock:{namespace}:order:{orderId}"
    config: {ttlSeconds: 30}

  - name: "ranking:daily"
    resource_type: "REDIS_KEY"
    template: "rank:{namespace}:daily:{date}"
    config: {ttlSeconds: 86400}

  # 同表也管理其他资源类型
  - name: "order:created"
    resource_type: "MQ_TOPIC"
    template: "order.created.{env}"

  - name: "payment:charge"
    resource_type: "HTTP_ENDPOINT"
    template: "https://payment.internal/{env}/charge"
    config: {timeoutMs: 5000}
```

### 9.6 装配链路

```
引入 fluxion-redis-lettuce / fluxion-redis-redisson / fluxion-redis-spring-data
  → Auto-configuration 提供 RedisClientAdapter Bean
    → fluxion-redis-spring-boot 的 RedisWorkflowAutoConfiguration
      (@ConditionalOnBean(RedisClientAdapter::class))
      → builtin:redisCommand 注册到 FunctionRegistry
```

---

## 10. 装饰器体系（NodeDecorator）

### 10.1 装饰器 SPI

```java
/**
 * 节点装饰器 SPI — 横切关注点实现
 * 装饰器按照 WorkflowNode.decorators 顺序链式应用
 * 建议顺序：metrics → trace → retry → rateLimit → cache → async
 */
public interface NodeDecorator {
    /** 装饰器标识（对应 WorkflowNode.decorators 中的名称） */
    String name();

    /** 包装函数，返回增强后的函数 */
    WorkflowFunction<Object> decorate(WorkflowFunction<Object> original, WorkflowNode node);
}
```

### 10.2 内置装饰器完整目录

#### metrics（指标埋点）

```java
@Component
public class MetricsDecorator implements NodeDecorator {

    private final MeterRegistry meterRegistry;

    @Override public String name() { return "metrics"; }

    @Override
    public WorkflowFunction<Object> decorate(WorkflowFunction<Object> fn, WorkflowNode node) {
        return input -> {
            long start = System.currentTimeMillis();
            Tags tags  = Tags.of("workflow", node.getWorkflowId(), "node", node.getId());
            try {
                FunctionResult<Object> result = fn.apply(input);
                meterRegistry.counter("workflow.node.success", tags).increment();
                meterRegistry.timer("workflow.node.duration", tags)
                    .record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
                return result;
            } catch (Exception e) {
                meterRegistry.counter("workflow.node.error",
                    tags.and("error", e.getClass().getSimpleName())).increment();
                throw e;
            }
        };
    }
}
```

#### trace（分布式追踪）

```java
@Component
public class TraceDecorator implements NodeDecorator {

    private final Tracer tracer;

    @Override public String name() { return "trace"; }

    @Override
    public WorkflowFunction<Object> decorate(WorkflowFunction<Object> fn, WorkflowNode node) {
        return input -> {
            Span span = tracer.spanBuilder(node.getName())
                .setParent(extractContext(input.meta()))
                .setAttribute("workflow.id",   node.getWorkflowId())
                .setAttribute("node.id",       node.getId())
                .setAttribute("execution.id",  input.meta().getExecutionId())
                .startSpan();
            try (Scope scope = span.makeCurrent()) {
                return fn.apply(input);
            } catch (Exception e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
                throw e;
            } finally {
                span.end();
            }
        };
    }
}
```

#### rateLimit（令牌桶限流）

```java
/**
 * 限流装饰器（Guava RateLimiter）
 * decoratorParams 支持：permitsPerSecond（默认 100）
 */
@Component
public class RateLimitDecorator implements NodeDecorator {

    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    @Override public String name() { return "rateLimit"; }

    @Override
    public WorkflowFunction<Object> decorate(WorkflowFunction<Object> fn, WorkflowNode node) {
        double qps = (double) node.getDecoratorParam("rateLimit", "permitsPerSecond", 100.0);
        RateLimiter limiter = limiters.computeIfAbsent(node.getId(),
            id -> RateLimiter.create(qps));

        return input -> {
            if (!limiter.tryAcquire(50, TimeUnit.MILLISECONDS)) {
                throw new RateLimitExceededException(node.getName());
            }
            return fn.apply(input);
        };
    }
}
```

#### cache（缓存）

```java
/**
 * 缓存装饰器
 * decoratorParams 支持：ttlSeconds（默认 60）/ cacheKeyExpression（SpEL）
 */
@Component
public class CacheDecorator implements NodeDecorator {

    private final CacheManager cacheManager;

    @Override public String name() { return "cache"; }

    @Override
    public WorkflowFunction<Object> decorate(WorkflowFunction<Object> fn, WorkflowNode node) {
        return input -> {
            long   ttl        = (long)   node.getDecoratorParam("cache", "ttlSeconds",       60L);
            String keyExpr    = (String) node.getDecoratorParam("cache", "cacheKeyExpression", "#input");
            String cacheKey   = buildCacheKey(node.getId(), keyExpr, input);

            Object cached = cacheManager.get(cacheKey);
            if (cached != null) return FunctionResult.success(cached);

            FunctionResult<Object> result = fn.apply(input);
            cacheManager.put(cacheKey, result.output(), ttl, TimeUnit.SECONDS);
            return result;
        };
    }
}
```

#### async（异步执行）

```java
/**
 * 异步装饰器
 * 节点立刻返回 executionId，结果通过 MQ/回调通知
 * decoratorParams 支持：callbackTopic / callbackUrl
 */
@Component
public class AsyncDecorator implements NodeDecorator {

    private final TaskExecutor asyncExecutor;
    private final MqPublisher  mqPublisher;

    @Override public String name() { return "async"; }

    @Override
    public WorkflowFunction<Object> decorate(WorkflowFunction<Object> fn, WorkflowNode node) {
        return input -> {
            String asyncId = UUID.randomUUID().toString();
            asyncExecutor.execute(() -> {
                try {
                    FunctionResult<Object> result = fn.apply(input);
                    publishCallback(node, asyncId, result.output(), null, input.meta());
                } catch (Exception e) {
                    publishCallback(node, asyncId, null, e.getMessage(), input.meta());
                }
            });
            // 立即返回 asyncId，调用方通过轮询/回调获取结果
            return FunctionResult.success(Map.of("asyncId", asyncId, "status", "PENDING"));
        };
    }
}
```

### 10.3 装饰器配置示例（JSON）

```json
{
  "id": "n1",
  "name": "查询用户信息",
  "functionRef": "builtin:dbQuery",
  "decorators": ["metrics", "trace", "rateLimit", "cache"],
  "decoratorParams": {
    "rateLimit": { "permitsPerSecond": 200 },
    "cache":     { "ttlSeconds": 120, "cacheKeyExpression": "#input.userId" }
  },
  "params": {
    "table": "user",
    "columns": ["id", "name", "email"],
    "conditions": [{"field": "status", "op": "EQ", "value": "ACTIVE"}]
  }
}
```

---

## 11. DynamicSqlGenerator（参数化 SQL 生成器）

### 11.1 支持的操作符

```java
/**
 * SQL 条件构建器
 * 完整操作符集合（防 SQL 注入：所有值通过 JDBC PreparedStatement 参数化）
 */
public class DynamicSqlGenerator {

    public enum Operator {
        EQ,          // field = ?
        NEQ,         // field != ?
        GT,          // field > ?
        GTE,         // field >= ?
        LT,          // field < ?
        LTE,         // field <= ?
        IN,          // field IN (?, ?, ?)
        NOT_IN,      // field NOT IN (?, ?, ?)
        LIKE,        // field LIKE ?（需调用方自行在 value 中添加 %）
        LIKE_PREFIX, // field LIKE '?%'（自动拼接）
        LIKE_SUFFIX, // field LIKE '%?'（自动拼接）
        IS_NULL,     // field IS NULL（无参数）
        IS_NOT_NULL, // field IS NOT NULL（无参数）
        BETWEEN,     // field BETWEEN ? AND ?
    }

    public SqlWithParams buildCondition(SqlCondition condition) {
        return switch (condition.getOp()) {
            case EQ          -> sql(condition.getField() + " = ?",          condition.getValue());
            case NEQ         -> sql(condition.getField() + " != ?",         condition.getValue());
            case GT          -> sql(condition.getField() + " > ?",          condition.getValue());
            case GTE         -> sql(condition.getField() + " >= ?",         condition.getValue());
            case LT          -> sql(condition.getField() + " < ?",          condition.getValue());
            case LTE         -> sql(condition.getField() + " <= ?",         condition.getValue());
            case LIKE        -> sql(condition.getField() + " LIKE ?",       condition.getValue());
            case LIKE_PREFIX -> sql(condition.getField() + " LIKE ?",       condition.getValue() + "%");
            case LIKE_SUFFIX -> sql(condition.getField() + " LIKE ?",       "%" + condition.getValue());
            case IS_NULL     -> sqlNoParam(condition.getField() + " IS NULL");
            case IS_NOT_NULL -> sqlNoParam(condition.getField() + " IS NOT NULL");
            case IN          -> buildIn(condition, false);
            case NOT_IN      -> buildIn(condition, true);
            case BETWEEN     -> buildBetween(condition);
        };
    }

    private SqlWithParams buildIn(SqlCondition cond, boolean not) {
        List<?> values    = (List<?>) cond.getValue();
        String  placeholders = String.join(", ", Collections.nCopies(values.size(), "?"));
        String  keyword   = not ? " NOT IN (" : " IN (";
        return new SqlWithParams(cond.getField() + keyword + placeholders + ")", values.toArray());
    }

    private SqlWithParams buildBetween(SqlCondition cond) {
        Object[] range = (Object[]) cond.getValue();
        return new SqlWithParams(cond.getField() + " BETWEEN ? AND ?", range[0], range[1]);
    }

    /** 生成完整 SELECT 语句 */
    public SqlWithParams generateSelect(String table, List<String> columns,
                                        List<SqlCondition> conditions) {
        validateIdentifier(table);
        columns.forEach(this::validateIdentifier);

        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(columns.isEmpty() ? "*" : String.join(", ", columns));
        sql.append(" FROM ").append(table);

        List<Object> params = new ArrayList<>();
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ");
            List<String> clauses = new ArrayList<>();
            for (SqlCondition cond : conditions) {
                SqlWithParams built = buildCondition(cond);
                clauses.add(built.getSql());
                params.addAll(List.of(built.getParams()));
            }
            sql.append(String.join(" AND ", clauses));
        }
        return new SqlWithParams(sql.toString(), params.toArray());
    }

    /** 防止表名/字段名注入（白名单：字母数字下划线）*/
    private void validateIdentifier(String identifier) {
        if (!identifier.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new InvalidSqlIdentifierException(identifier);
        }
    }
}
```

### 11.2 条件配置示例（JSON）

```json
{
  "table": "order",
  "columns": ["id", "user_id", "amount", "status", "created_at"],
  "conditions": [
    { "field": "status",     "op": "IN",          "value": ["PENDING", "PAID"] },
    { "field": "amount",     "op": "GTE",          "value": 100 },
    { "field": "user_name",  "op": "LIKE_PREFIX",  "value": "张" },
    { "field": "deleted_at", "op": "IS_NULL" },
    { "field": "created_at", "op": "BETWEEN",      "value": ["2026-01-01", "2026-12-31"] }
  ],
  "orderBy": [{ "field": "created_at", "direction": "DESC" }],
  "limit": 20,
  "offset": 0
}
```

---

## 12. 事务适配器（泛型 TransactionContext\<T\>）

```java
/**
 * 事务上下文 — 泛型化，避免强转
 * @param <T> 事务资源类型（Spring: TransactionStatus，Seata: GlobalTransaction）
 */
public record TransactionContext<T>(
    String transactionId,
    T      resource,            // 框架原生事务对象
    TransactionStatus status,
    long   startTime,
    String workflowId,
    String executionId
) {
    public enum TransactionStatus { ACTIVE, COMMITTED, ROLLED_BACK, SUSPENDED }
}

/**
 * 事务适配器 SPI
 * 解耦具体事务框架（Spring / Seata / Atomikos）
 */
public interface TransactionAdapter<T> {
    String type();
    TransactionContext<T> begin(TransactionConfig config);
    void commit(TransactionContext<T> ctx);
    void rollback(TransactionContext<T> ctx);
    void suspend(TransactionContext<T> ctx);
    void resume(TransactionContext<T> ctx);
}

/** Spring 本地事务实现 */
@Component
public class SpringTransactionAdapter implements TransactionAdapter<TransactionStatus> {

    private final PlatformTransactionManager txManager;

    @Override public String type() { return "SPRING_LOCAL"; }

    @Override
    public TransactionContext<TransactionStatus> begin(TransactionConfig config) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setIsolationLevel(config.getIsolationLevel());
        def.setPropagationBehavior(config.getPropagationBehavior());
        TransactionStatus status = txManager.getTransaction(def);
        return new TransactionContext<>(
            UUID.randomUUID().toString(), status,
            TransactionContext.TransactionStatus.ACTIVE,
            System.currentTimeMillis(), config.getWorkflowId(), config.getExecutionId()
        );
    }

    @Override public void commit(TransactionContext<TransactionStatus> ctx)   { txManager.commit(ctx.resource()); }
    @Override public void rollback(TransactionContext<TransactionStatus> ctx) { txManager.rollback(ctx.resource()); }
    @Override public void suspend(TransactionContext<TransactionStatus> ctx)  { txManager.getTransaction(new DefaultTransactionDefinition()); }
    @Override public void resume(TransactionContext<TransactionStatus> ctx)   { /* Spring 不支持直接 resume，通过传播行为实现 */ }
}

/** Seata 分布式事务实现 */
@Component
@ConditionalOnClass(name = "io.seata.spring.annotation.GlobalTransactional")
public class SeataTransactionAdapter implements TransactionAdapter<GlobalTransaction> {

    @Override public String type() { return "SEATA"; }

    @Override
    public TransactionContext<GlobalTransaction> begin(TransactionConfig config) {
        GlobalTransaction tx = GlobalTransactionContext.getCurrentOrCreate();
        tx.begin(config.getTimeoutMs(), config.getTransactionName());
        return new TransactionContext<>(
            RootContext.getXID(), tx,
            TransactionContext.TransactionStatus.ACTIVE,
            System.currentTimeMillis(), config.getWorkflowId(), config.getExecutionId()
        );
    }

    @Override public void commit(TransactionContext<GlobalTransaction> ctx)   { ctx.resource().commit(); }
    @Override public void rollback(TransactionContext<GlobalTransaction> ctx) { ctx.resource().rollback(); }
}
```

---

## 13. 数据库设计（DDL 最终版）

### 13.1 核心表

> DDL 完整定义已独立存放：[`workflow-ddl.sql`](./workflow-ddl.sql)

| 表名 | 说明 |
|------|------|
| `wf_definition` | 工作流定义（含节点配置、Schema、协议路由、Saga 标志） |
| `wf_version_snapshot` | 历史版本快照（发布/修改元工作流时自动创建，支持版本 diff 和回滚） |
| `wf_function` | 函数注册表（BUILTIN/CUSTOM/SCRIPT/EXTERNAL 四类，含 Schema） |
| `wf_execution_log` | **可选**：执行日志 DB 持久化（默认输出到结构化 Log，见 §13.2） |
| `wf_schema` | JSON Schema 独立存储（可跨工作流引用，格式：`$ref:schema:<name>`） |
| `wf_resource_template` | 通用资源模板（REDIS\_KEY / MQ\_TOPIC / HTTP\_ENDPOINT / DB\_TABLE 等） |

### 13.2 关键设计说明

- **版本快照隔离**：`ImmutableExecutionState.start()` 在执行开始时锁定 `nodes_config`，后续热加载刷新不影响进行中的执行。
- **执行日志 SPI（默认输出到 Log）**：引擎不直接依赖数据库，通过 `ExecutionLogStore` SPI 解耦存储介质。
  - **默认**：`LoggingExecutionLogStore`——将执行记录写入结构化日志（JSON），接入 ELK/Loki。
  - **可选**：配置 `workflow.execution-log.store=db` 激活 `DbExecutionLogStore`，启用 DB 持久化。
  - DB 持久化适用场景：精确节点重放、Admin UI 历史查询、Saga 补偿审计。
- **通用资源模板**：`wf_resource_template` 替代原 `wf_redis_key_schema`，统一管理各类具名资源（Redis Key、MQ Topic、HTTP 端点等），通过 `ResourceTemplateRegistry` 按 `resource_type` 分派解析。
- **`retry_attempt` 字段**：每次重试写独立记录（`retry_attempt=1,2,...`），便于统计重试成功率和排查抖动。
- **热加载优化索引**：`idx_status_updated(status, updated_at)` 覆盖 L1 DB 轮询的 `SELECT ... WHERE updated_at > ?` 查询。

### 13.3 ExecutionLogStore SPI

```java
/**
 * 执行日志存储 SPI — 解耦引擎与存储介质
 *
 * 默认实现：LoggingExecutionLogStore（结构化 JSON 日志）
 * 可选实现：DbExecutionLogStore（DB 持久化，需建 wf_execution_log 表）
 *
 * 选择依据：
 *   - 生产环境日志量大，DB 持久化成本高 → 默认 Log，接入 ELK/Loki 搜索
 *   - 需要精确重放 / Admin UI 查询历史 → 开启 DB 存储
 *   - 混合策略：关键工作流用 DB，其余用 Log（通过 WorkflowDefinition.logStore 字段控制）
 */
public interface ExecutionLogStore {
    /** 是否启用（false 时 WorkflowEngine 跳过 save 调用） */
    boolean enabled();

    /** 保存单个节点执行记录 */
    void save(NodeExecutionRecord record, ExecutionMeta meta);

    /**
     * 按 executionId 加载完整执行轨迹（调试/重放用）
     * ⚠️ LoggingExecutionLogStore 不支持此操作，返回空列表；
     *    需要重放能力时必须启用 DbExecutionLogStore
     */
    default List<NodeExecutionRecord> loadTrace(String executionId) {
        return List.of();
    }
}

// ─── 默认实现：结构化日志（零 DB 依赖）────────────────────────────────────────

/**
 * 默认执行日志存储：输出到结构化 JSON 日志
 * 无需建表，接入 ELK/Loki 后可按 execution_id / workflow_id 全文检索
 *
 * ★ 条件装配：若没有 DbExecutionLogStore Bean，则自动激活
 */
@Component
@ConditionalOnMissingBean(DbExecutionLogStore.class)
public class LoggingExecutionLogStore implements ExecutionLogStore {

    @Override public boolean enabled() { return true; }

    @Override
    public void save(NodeExecutionRecord record, ExecutionMeta meta) {
        log.info("{}", Map.of(
            "event",        "node.execution",
            "execution_id", meta.getExecutionId(),
            "workflow_id",  meta.getWorkflowId(),
            "workflow_ver", meta.getVersion(),
            "node_id",      record.getNodeId(),
            "node_name",    record.getNodeName(),
            "status",       record.getStatus(),
            "duration_ms",  record.getDurationMs(),
            "retry_attempt",record.getRetryAttempt()
        ));
    }
}

// ─── 可选实现：DB 持久化 ──────────────────────────────────────────────────────

/**
 * DB 执行日志存储：持久化到 wf_execution_log 表
 *
 * 激活条件：application.yml 中配置 workflow.execution-log.store=db
 * 建表前提：运行 workflow-ddl.sql 中的 wf_execution_log 建表语句
 */
@Component
@ConditionalOnProperty(name = "workflow.execution-log.store", havingValue = "db")
public class DbExecutionLogStore implements ExecutionLogStore {

    private final JdbcTemplate jdbcTemplate;

    @Override public boolean enabled() { return true; }

    @Override
    public void save(NodeExecutionRecord record, ExecutionMeta meta) {
        jdbcTemplate.update(
            """
            INSERT INTO wf_execution_log
              (execution_id, workflow_id, workflow_version, node_id, node_name,
               status, input_data, output_data, side_effects, error_msg, duration_ms, retry_attempt)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            meta.getExecutionId(), meta.getWorkflowId(), meta.getVersion(),
            record.getNodeId(), record.getNodeName(),
            record.getStatus().name(),
            toJson(record.getInput()), toJson(record.getOutput()),
            toJson(record.getSideEffects()), record.getErrorMessage(),
            record.getDurationMs(), record.getRetryAttempt()
        );
    }

    @Override
    public List<NodeExecutionRecord> loadTrace(String executionId) {
        return jdbcTemplate.query(
            "SELECT * FROM wf_execution_log WHERE execution_id = ? ORDER BY id ASC",
            (rs, i) -> mapRow(rs), executionId
        );
    }
}
```

---

## 14. NodeExecutionRecord 与执行轨迹

### 14.1 NodeExecutionRecord（执行轨迹）

```java
/**
 * 节点执行记录
 * ★ 新增 nodeInput 快照（完整记录节点"看到的世界"，而非整个 Context）
 */
@Data
@Builder
public class NodeExecutionRecord {
    private String nodeId;
    private String nodeName;
    private NodeStatus status;      // SUCCESS / FAILED / SKIPPED / FALLBACK / RETRY
    /** ★ 节点实际接收的 NodeInput 快照（调试时可精确复现节点执行环境） */
    private NodeInput input;
    private Object    output;
    private List<SideEffect> sideEffects;
    private String errorMessage;
    private long   durationMs;
    private long   timestamp;

    // ─── 工厂方法 ────────────────────────────────────────────────

    /** 节点成功执行 */
    public static NodeExecutionRecord success(WorkflowNode node, Object output, long durationMs) {
        return NodeExecutionRecord.builder()
            .nodeId(node.getId())
            .nodeName(node.getName())
            .status(NodeStatus.SUCCESS)
            .output(output)
            .durationMs(durationMs)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /** 节点执行失败 */
    public static NodeExecutionRecord failed(WorkflowNode node, Throwable error, long durationMs) {
        return NodeExecutionRecord.builder()
            .nodeId(node.getId())
            .nodeName(node.getName())
            .status(NodeStatus.FAILED)
            .errorMessage(error != null ? error.getMessage() : "unknown error")
            .durationMs(durationMs)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /** 节点被跳过（ErrorStrategy=SKIP） */
    public static NodeExecutionRecord skipped(WorkflowNode node, long durationMs) {
        return NodeExecutionRecord.builder()
            .nodeId(node.getId())
            .nodeName(node.getName())
            .status(NodeStatus.SKIPPED)
            .durationMs(durationMs)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /** 节点降级成功（ErrorStrategy=FALLBACK，调用了 fallback() 方法） */
    public static NodeExecutionRecord fallback(WorkflowNode node, Object fallbackOutput, long durationMs) {
        return NodeExecutionRecord.builder()
            .nodeId(node.getId())
            .nodeName(node.getName())
            .status(NodeStatus.FALLBACK)
            .output(fallbackOutput)
            .durationMs(durationMs)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /** 带 NodeInput 快照的成功记录（调试模式用，保存完整现场） */
    public static NodeExecutionRecord successWithInput(WorkflowNode node, NodeInput input,
                                                       Object output, long durationMs) {
        return NodeExecutionRecord.builder()
            .nodeId(node.getId())
            .nodeName(node.getName())
            .status(NodeStatus.SUCCESS)
            .input(input)
            .output(output)
            .durationMs(durationMs)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}
```

### 14.2 指标埋点（Metrics）

| 指标 | 类型 | 标签 |
|------|------|------|
| `workflow.request.total` | Counter | workflow_id, status |
| `workflow.request.duration` | Histogram | workflow_id |
| `workflow.node.duration` | Histogram | workflow_id, node_id |
| `workflow.node.error` | Counter | workflow_id, node_id, error_type |
| `workflow.node.schema.output.mismatch` | Counter | workflow_id, node_id |
| `workflow.saga.compensation.triggered` | Counter | workflow_id |
| `workflow.saga.compensation.failed` | Counter | workflow_id, node_id |

### 14.3 分布式追踪

每个工作流执行对应一个 Trace，每个节点对应一个 Span：

```java
// trace:span 装饰器（在 §6.8 装饰器体系中注册）
@Component
public class TraceSpanDecorator implements NodeDecorator {

    private final Tracer tracer;

    @Override
    public WorkflowFunction<Object> decorate(WorkflowFunction<Object> original, WorkflowNode node) {
        return input -> {
            // 从 meta 中提取上游 Trace 上下文
            Span span = tracer.spanBuilder(node.getName())
                .setParent(extractContext(input.meta()))
                .setAttribute("workflow.id", node.getWorkflowId())
                .setAttribute("node.id", node.getId())
                .startSpan();
            try (Scope scope = span.makeCurrent()) {
                return original.apply(input);
            } catch (Exception e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
                throw e;
            } finally {
                span.end();
            }
        };
    }
}
```

---

## 15. 热加载与配置同步（L1/L2/L3 三级）

### 15.1 ConfigSyncStrategy SPI

```java
/**
 * 配置同步策略 SPI — 三级热加载
 * L1（主动轮询）：DB 定时 poll，适用于所有环境
 * L2（Apollo）：配置中心 Push，毫秒级延迟
 * L3（Nacos）：服务发现+配置一体，适用于 Nacos 环境
 */
public interface ConfigSyncStrategy {
    /** 策略优先级，数字越小优先级越高 */
    int order();

    /** 是否可用（用于条件激活） */
    boolean isAvailable();

    /**
     * 启动同步监听
     * @param listener 配置变更回调（workflowId → 最新 WorkflowDefinition）
     */
    void startSync(ConfigChangeListener listener);

    void stopSync();
}

@FunctionalInterface
public interface ConfigChangeListener {
    void onDefinitionChanged(String workflowId, WorkflowDefinition newDefinition);
}
```

### 15.2 L1：DB 轮询策略

```java
/**
 * L1 DB 轮询策略
 * 每 N 秒查询 wf_definition 表中 updated_at 变更的记录
 * ★ 使用 synchronized 保证 check-then-act 原子性（防止并发双重刷新）
 */
@Component
@Order(3)  // 最低优先级（兜底）
public class DbPollingSyncStrategy implements ConfigSyncStrategy {

    private final WorkflowDefinitionRepository repository;
    private final ScheduledExecutorService     scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile Instant lastPollTime = Instant.EPOCH;

    @Override public int order()        { return 3; }
    @Override public boolean isAvailable() { return true; } // 始终可用

    @Override
    public void startSync(ConfigChangeListener listener) {
        int pollIntervalSec = environment.getProperty("workflow.sync.poll-interval-sec", Integer.class, 30);
        scheduler.scheduleAtFixedRate(() -> pollCheck(listener), 0, pollIntervalSec, TimeUnit.SECONDS);
    }

    /**
     * ★ synchronized：防止并发调用导致重复刷新
     * 原子性保证：查询到变更后立即更新 lastPollTime，再触发回调
     */
    private synchronized void pollCheck(ConfigChangeListener listener) {
        try {
            Instant now   = Instant.now();
            Instant since = lastPollTime;
            List<WorkflowDefinition> changed = repository.findUpdatedSince(since);
            if (!changed.isEmpty()) {
                lastPollTime = now; // 先更新时间戳，防止重复触发
                changed.forEach(def -> listener.onDefinitionChanged(def.getId(), def));
                log.info("L1 poll: {} definitions updated since {}", changed.size(), since);
            }
        } catch (Exception e) {
            log.error("L1 poll failed", e);
        }
    }

    @Override
    public void stopSync() { scheduler.shutdown(); }
}
```

### 15.3 L2：Apollo 推送策略

```kotlin
/**
 * L2 Apollo 配置中心策略
 * 监听 Apollo namespace "workflow.definitions"
 * 变更时 Push 到所有节点（毫秒级延迟）
 */
@Component
@Order(2)
@ConditionalOnClass(name = ["com.ctrip.framework.apollo.ConfigService"])
class ApolloSyncStrategy(
    private val apolloConfig: Config = ConfigService.getConfig("workflow.definitions")
) : ConfigSyncStrategy {

    override fun order() = 2
    override fun isAvailable() = try { Class.forName("com.ctrip.framework.apollo.ConfigService"); true } catch (e: ClassNotFoundException) { false }

    override fun startSync(listener: ConfigChangeListener) {
        apolloConfig.addChangeListener { changeEvent ->
            changeEvent.changedKeys()
                .filter { it.startsWith("wf.") }
                .forEach { key ->
                    val workflowId = key.removePrefix("wf.")
                    val newJson    = apolloConfig.getProperty(key, null) ?: return@forEach
                    val definition = objectMapper.readValue(newJson, WorkflowDefinition::class.java)
                    listener.onDefinitionChanged(workflowId, definition)
                    log.info("L2 Apollo push: workflowId={}", workflowId)
                }
        }
    }

    override fun stopSync() { /* Apollo 自动清理 */ }
}
```

### 15.4 L3：Nacos 推送策略

```kotlin
/**
 * L3 Nacos 配置中心策略
 * 监听 Nacos dataId "workflow-definitions.json"
 */
@Component
@Order(1)
@ConditionalOnClass(name = ["com.alibaba.nacos.api.NacosFactory"])
class NacosSyncStrategy(
    private val nacosDataId:  String = "workflow-definitions.json",
    private val nacosGroup:   String = "WORKFLOW_GROUP"
) : ConfigSyncStrategy {

    private var configService: ConfigService? = null

    override fun order() = 1  // 最高优先级
    override fun isAvailable() = try { Class.forName("com.alibaba.nacos.api.NacosFactory"); true } catch (e: ClassNotFoundException) { false }

    override fun startSync(listener: ConfigChangeListener) {
        val properties = Properties().apply {
            setProperty(PropertyKeyConst.SERVER_ADDR, environment.getProperty("spring.cloud.nacos.config.server-addr")!!)
        }
        configService = NacosFactory.createConfigService(properties)
        configService!!.addListener(nacosDataId, nacosGroup, object : AbstractListener() {
            override fun receiveConfigInfo(configInfo: String) {
                try {
                    val definitions: List<WorkflowDefinition> = objectMapper.readValue(configInfo)
                    definitions.forEach { listener.onDefinitionChanged(it.id, it) }
                    log.info("L3 Nacos push: {} definitions updated", definitions.size)
                } catch (e: Exception) {
                    log.error("L3 Nacos push parse failed", e)
                }
            }
        })
    }

    override fun stopSync() { configService?.shutDown() }
}
```

### 15.5 配置同步管理器

```java
/**
 * 配置同步管理器
 * 按优先级激活可用的同步策略（支持多策略同时运行，以最快到达的变更为准）
 */
@Component
public class ConfigSyncManager implements ApplicationListener<ContextRefreshedEvent> {

    private final List<ConfigSyncStrategy>        strategies;
    private final WorkflowDefinitionCache         cache;
    private final WorkflowAdapterRegistry         adapterRegistry;
    private final List<ConfigSyncStrategy>        activeStrategies = new ArrayList<>();

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        strategies.stream()
            .filter(ConfigSyncStrategy::isAvailable)
            .sorted(Comparator.comparingInt(ConfigSyncStrategy::order))
            .forEach(strategy -> {
                strategy.startSync(this::onDefinitionChanged);
                activeStrategies.add(strategy);
                log.info("ConfigSync strategy activated: {} (order={})",
                    strategy.getClass().getSimpleName(), strategy.order());
            });
    }

    private synchronized void onDefinitionChanged(String workflowId, WorkflowDefinition newDef) {
        WorkflowDefinition oldDef = cache.get(workflowId);
        if (oldDef != null && oldDef.getVersion() >= newDef.getVersion()) {
            return; // 幂等：忽略旧版本
        }
        cache.put(workflowId, newDef);
        adapterRegistry.reregisterRoute(newDef); // 更新路由
        log.info("Workflow definition updated: id={}, version={}→{}",
            workflowId, oldDef != null ? oldDef.getVersion() : 0, newDef.getVersion());
    }
}
```

### 15.6 函数注册持久化边界

| 来源 | 权威存储 | 重启后 |
|------|---------|--------|
| 代码 `registry.register(name, fn)` | DB 存储元信息（name/schema），代码提供实现 | 应用启动时 Spring 自动注册 |
| DB `wf_function`（SCRIPT 类型） | DB 是权威（脚本内容在 DB） | 自动加载并编译缓存 |
| DB `wf_function`（EXTERNAL 类型） | DB 是权威（RPC 地址在 DB） | 自动注册到 ExternalServiceRef |

---

## 16. Admin 后台 API 设计

### 16.1 工作流管理 API

```
POST   /api/admin/workflows              创建工作流（草稿）
GET    /api/admin/workflows              分页查询工作流列表
GET    /api/admin/workflows/{id}         获取工作流详情（含当前版本节点配置）
PUT    /api/admin/workflows/{id}         更新工作流（仅 DRAFT 状态可改）
POST   /api/admin/workflows/{id}/publish 发布工作流（DRAFT → PUBLISHED）
POST   /api/admin/workflows/{id}/clone   克隆为新版本草稿
DELETE /api/admin/workflows/{id}         删除（仅 DRAFT 可删，PUBLISHED 需先 DEPRECATED）
GET    /api/admin/workflows/{id}/versions 获取历史版本列表
GET    /api/admin/workflows/{id}/diff?v1={v}&v2={v} 版本 diff 对比
```

### 16.2 函数管理 API

```
POST   /api/admin/functions              注册函数（SCRIPT/EXTERNAL 类型）
GET    /api/admin/functions              查询函数列表（支持 category/node_type 过滤）
GET    /api/admin/functions/{name}       获取函数详情（含 inputSchema/outputSchema）
PUT    /api/admin/functions/{name}       更新脚本内容/配置
DELETE /api/admin/functions/{name}       删除（检查是否被工作流引用）
POST   /api/admin/functions/{name}/test  测试执行（传入 NodeInput，返回 FunctionResult）
```

### 16.3 执行日志 API

```
GET  /api/admin/executions               分页查询执行日志（workflowId/status/时间范围）
GET  /api/admin/executions/{id}          获取执行详情（含所有节点 trace）
GET  /api/admin/executions/{id}/state    获取执行的 ImmutableExecutionState 快照
POST /api/admin/executions/{id}/rerun    从指定节点重放（body: {nodeId, overrideInput?}）
```

### 16.4 调试 API

```
POST /api/admin/debug/start             启动调试会话（返回初始 DebugSnapshot）
POST /api/admin/debug/step              单步执行（body: DebugSnapshot → 返回新 DebugSnapshot）
POST /api/admin/debug/continue          执行到下一断点（body: DebugSnapshot）
POST /api/admin/debug/mock              设置函数 Mock（body: {functionRef, mockResult}）
GET  /api/admin/debug/snapshot/{id}     获取历史调试快照
```

### 16.5 Schema 管理 API

```
POST /api/admin/schemas                 创建 Schema
GET  /api/admin/schemas                 查询 Schema 列表
PUT  /api/admin/schemas/{name}          更新 Schema（同时检查引用该 Schema 的工作流兼容性）
POST /api/admin/schemas/validate        校验 JSON 数据是否符合 Schema（测试用）
```

### 16.6 元工作流 API（管理后台本身由元工作流驱动）

```
GET  /api/admin/meta/workflows          查看元工作流列表（category=META）
POST /api/admin/meta/workflows/{id}/test 测试执行元工作流
```

---

## 17. 安全设计

### 17.1 认证方案

```java
/**
 * 工作流平台安全过滤器
 * 认证：JWT（管理后台）+ API Key（服务间调用）+ 无认证（公开工作流）
 */
@Component
public class WorkflowSecurityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String path = request.getRequestURI();

        // 1. 管理后台：JWT 认证
        if (path.startsWith("/api/admin/")) {
            String jwt = extractBearerToken(request);
            if (jwt == null || !jwtValidator.validate(jwt)) {
                response.sendError(401, "Unauthorized");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(jwtValidator.parse(jwt));
        }
        // 2. 受保护的业务工作流：API Key 认证
        else if (isProtectedWorkflow(path)) {
            String apiKey = request.getHeader("X-API-Key");
            if (apiKey == null || !apiKeyValidator.validate(apiKey, path)) {
                response.sendError(403, "Forbidden");
                return;
            }
        }
        // 3. 公开工作流（is_protected=0）：无需认证
        chain.doFilter(request, response);
    }
}
```

### 17.2 权限模型（RBAC）

| 角色 | 权限 |
|------|------|
| `ADMIN` | 所有操作，含删除、发布元工作流 |
| `DEVELOPER` | 创建/编辑工作流草稿，注册函数，查看执行日志 |
| `OPERATOR` | 查看工作流，触发执行，查看日志 |
| `VIEWER` | 只读：查看工作流、函数、执行日志 |

```java
@Component
public class WorkflowPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        String role = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority).findFirst().orElse("");

        return switch (permission.toString()) {
            case "PUBLISH"  -> role.equals("ADMIN");
            case "EDIT"     -> List.of("ADMIN", "DEVELOPER").contains(role);
            case "EXECUTE"  -> List.of("ADMIN", "DEVELOPER", "OPERATOR").contains(role);
            case "VIEW"     -> true;
            default -> false;
        };
    }
}
```

### 17.3 脚本安全等级

| 脚本来源 | 安全等级 | 沙箱策略 |
|---------|---------|---------|
| 内部开发者（经代码审查） | Level 1 | SecureASTCustomizer（AST 级别） |
| 经审核的合作方 | Level 2 | 独立 ClassLoader + SecurityManager |
| 完全不受信任的用户输入 | Level 3 | 子进程隔离执行（禁用或限制使用） |

> **Level 1 局限性**：`SecureASTCustomizer` 只在 AST 解析阶段拦截，无法阻止通过反射调用的危险操作。
> 若需要更强隔离，必须使用 Level 2 或 Level 3。

### 17.4 调试安全（防止调试接口被滥用）

```java
/**
 * 调试安全守卫
 * 使用 Guava RateLimiter 本地令牌桶（无 Redis 依赖），防止调试接口被频繁调用
 */
@Component
public class DebugSecurityGuard {

    /** 每个 IP 每秒最多 2 次调试请求 */
    private final LoadingCache<String, RateLimiter> ipLimiters = Caffeine.newBuilder()
        .expireAfterAccess(5, TimeUnit.MINUTES)
        .build(ip -> RateLimiter.create(2.0));

    /** 全局调试并发数限制 */
    private final Semaphore globalConcurrencyGuard = new Semaphore(20);

    public void checkPermission(String userIp, String workflowId, Authentication auth) {
        // 1. 限流
        if (!ipLimiters.get(userIp).tryAcquire()) {
            throw new DebugRateLimitException("Debug rate limit exceeded for IP: " + userIp);
        }
        // 2. 全局并发控制
        if (!globalConcurrencyGuard.tryAcquire()) {
            throw new DebugCapacityException("Debug capacity full, try again later");
        }
        // 3. 权限校验（开发者及以上可调试）
        if (!auth.getAuthorities().stream().anyMatch(a ->
                List.of("ADMIN", "DEVELOPER").contains(a.getAuthority()))) {
            // ★ 权限拒绝时需先释放 Semaphore，否则已 acquire 的许可泄漏
            globalConcurrencyGuard.release();
            throw new AccessDeniedException("Debug requires DEVELOPER role");
        }
        // 4. 禁止调试元工作流（is_protected=1 的工作流）
        WorkflowDefinition def = workflowCache.get(workflowId);
        if (def != null && def.isProtected()) {
            // ★ 同上，抛出前必须释放
            globalConcurrencyGuard.release();
            throw new AccessDeniedException("Cannot debug protected meta-workflow: " + workflowId);
        }
    }

    /**
     * 释放调试并发许可
     *
     * ★ 使用规范（调用方必须在 finally 中调用，或使用 acquireGuard() 的 try-with-resources）：
     * <pre>
     *   debugSecurityGuard.checkPermission(ip, workflowId, auth);
     *   try {
     *       // 执行调试逻辑
     *   } finally {
     *       debugSecurityGuard.releasePermit();
     *   }
     * </pre>
     */
    public void releasePermit() {
        globalConcurrencyGuard.release();
    }

    /**
     * try-with-resources 风格的调试许可（推荐使用，自动释放，防止额度泄漏）
     *
     * 使用示例：
     * <pre>
     *   try (var ignored = debugSecurityGuard.acquireGuard(ip, workflowId, auth)) {
     *       // 执行调试逻辑，结束后自动 release
     *   }
     * </pre>
     */
    public AutoCloseable acquireGuard(String userIp, String workflowId, Authentication auth) {
        checkPermission(userIp, workflowId, auth);
        return globalConcurrencyGuard::release;
    }
}
```

---

## 18. 可观测性完整设计

### 18.1 全量 Metrics 清单

```java
/**
 * 工作流平台 Metrics 注册
 * 使用 Micrometer 统一采集，支持 Prometheus / InfluxDB / CloudWatch
 */
@Component
public class WorkflowMetrics {

    // ─── 请求层 ────────────────────────────────────────
    /** 工作流总执行次数（成功/失败） */
    public static final String WF_REQUEST_TOTAL    = "workflow.request.total";
    /** 工作流端到端执行耗时 */
    public static final String WF_REQUEST_DURATION = "workflow.request.duration";

    // ─── 节点层 ────────────────────────────────────────
    /** 节点执行成功次数 */
    public static final String NODE_SUCCESS        = "workflow.node.success";
    /** 节点执行失败次数（含错误类型标签） */
    public static final String NODE_ERROR          = "workflow.node.error";
    /** 节点执行耗时 */
    public static final String NODE_DURATION       = "workflow.node.duration";
    /** 节点重试次数 */
    public static final String NODE_RETRY          = "workflow.node.retry";
    /** 节点 Schema 输出不匹配（devMode） */
    public static final String NODE_SCHEMA_MISMATCH = "workflow.node.schema.output.mismatch";

    // ─── Saga 层 ────────────────────────────────────────
    /** Saga 补偿触发次数 */
    public static final String SAGA_COMPENSATION_TRIGGERED = "workflow.saga.compensation.triggered";
    /** Saga 补偿失败（需人工介入） */
    public static final String SAGA_COMPENSATION_FAILED    = "workflow.saga.compensation.failed";

    // ─── 热加载层 ────────────────────────────────────────
    /** 配置同步成功次数（按策略分层） */
    public static final String CONFIG_SYNC_SUCCESS = "workflow.config.sync.success";
    /** 配置同步失败次数 */
    public static final String CONFIG_SYNC_FAILURE = "workflow.config.sync.failure";

    // ─── 函数注册层 ────────────────────────────────────
    /** 函数调用次数（按 functionRef） */
    public static final String FUNCTION_CALL       = "workflow.function.call";
    /** 脚本函数编译耗时 */
    public static final String SCRIPT_COMPILE_TIME = "workflow.script.compile.duration";

    /** 注册所有 Metrics */
    public void registerAll(MeterRegistry registry) {
        // Counters
        registry.counter(WF_REQUEST_TOTAL,      "status", "success");
        registry.counter(WF_REQUEST_TOTAL,      "status", "failure");
        registry.counter(SAGA_COMPENSATION_TRIGGERED);
        registry.counter(SAGA_COMPENSATION_FAILED);
        // Timers (Histogram)
        Timer.builder(WF_REQUEST_DURATION).publishPercentiles(0.5, 0.95, 0.99).register(registry);
        Timer.builder(NODE_DURATION).publishPercentiles(0.5, 0.95, 0.99).register(registry);
    }

    /** 工作流执行完毕后统一上报 */
    public void recordExecution(WorkflowDefinition def, EngineResult result, long durationMs) {
        Tags tags = Tags.of(
            "workflow_id",   def.getId(),
            "workflow_name", def.getName(),
            "status",        result.isSuccess() ? "success" : "failure"
        );
        registry.counter(WF_REQUEST_TOTAL, tags).increment();
        registry.timer(WF_REQUEST_DURATION, tags)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }
}
```

### 18.2 结构化日志规范

```java
/**
 * 统一日志格式（JSON 结构化日志）
 * 字段约定：所有日志必须包含 execution_id 和 workflow_id，方便链路查询
 */
@Slf4j
public class WorkflowLogger {

    public static void logNodeStart(WorkflowNode node, NodeInput input) {
        log.info("{}", Map.of(
            "event",        "node.start",
            "workflow_id",  node.getWorkflowId(),
            "execution_id", input.meta().getExecutionId(),
            "node_id",      node.getId(),
            "node_name",    node.getName(),
            "function_ref", node.getFunctionRef()
        ));
    }

    public static void logNodeComplete(WorkflowNode node, NodeExecutionRecord record) {
        log.info("{}", Map.of(
            "event",        "node.complete",
            "workflow_id",  node.getWorkflowId(),
            "node_id",      node.getId(),
            "status",       record.getStatus(),
            "duration_ms",  record.getDurationMs()
        ));
    }

    public static void logSagaCompensation(WorkflowNode node, boolean success, Exception ex) {
        if (success) {
            log.info("{}", Map.of("event", "saga.compensation.success", "node_id", node.getId()));
        } else {
            log.error("{}", Map.of("event", "saga.compensation.failed",
                "node_id", node.getId(), "error", ex.getMessage()));
        }
    }
}
```

### 18.3 分布式追踪（OpenTelemetry）

工作流执行对应一个 Trace，每个节点对应一个 Span，Span 关系：

```
Trace: wf-{executionId}
  └── Span: adapter.receive (HTTP/Dubbo/gRPC)
        └── Span: engine.execute (WorkflowEngine)
              ├── Span: node.n1 (functionRef=builtin:dbQuery)
              ├── Span: node.n2 (functionRef=builtin:httpPost)
              └── Span: node.n3 (functionRef=custom:buildResponse)
```

---

## 19. 元工作流自举设计（完整版）

### 19.1 设计原则

管理后台的所有 API（CRUD 工作流、发布、查询日志）均由**元工作流**驱动，
元工作流本身也是 `WorkflowDefinition`，category=META，is_protected=1。

### 19.2 元工作流示例

```yaml
# 创建工作流 API 对应的元工作流定义
id: "meta-wf-create-workflow"
name: "创建工作流"
category: META
is_protected: true
protocol: HTTP
method: POST
path: /api/admin/workflows
nodes:
  - id: "n1"
    name: "认证校验"
    functionRef: "builtin:authCheck"
    params:
      requiredRole: DEVELOPER
    next: "n2"

  - id: "n2"
    name: "参数校验"
    functionRef: "builtin:paramValidate"
    params:
      schema: "$ref:schema:workflow-create-request"
    next: "n3"

  - id: "n3"
    name: "MetaWorkflowGuard 校验"
    functionRef: "custom:metaWorkflowGuard"
    params:
      action: CREATE
    next: "n4"

  - id: "n4"
    name: "保存草稿"
    functionRef: "builtin:dbInsert"
    params:
      table: "wf_definition"
      compensateFunctionRef: "custom:deleteWorkflowDraft"
    next: "n5"

  - id: "n5"
    name: "返回创建结果"
    functionRef: "builtin:responseWrapper"
```

### 19.3 MetaWorkflowGuard（元工作流保护）

```java
/**
 * 元工作流保护器
 * 核心职责：保护 is_protected=true 的元工作流不被误删/误覆盖
 *
 * ★ 修正：先校验新内容合法性，再创建快照
 */
@Component
public class MetaWorkflowGuard {

    /**
     * 校验工作流变更操作的合法性
     * @param existingDef  现有工作流定义（可能为 null，表示新建）
     * @param newDefinition 要保存的新定义
     */
    public void validate(WorkflowDefinition existingDef, WorkflowDefinition newDefinition) {
        // ★ 先校验新内容，再备份（避免备份无效内容）
        if (newDefinition.isProtected() && !newDefinition.isValid()) {
            throw new InvalidMetaWorkflowException("新元工作流内容校验失败，拒绝变更");
        }

        if (existingDef != null && existingDef.isProtected()) {
            // 受保护的元工作流：不允许删除，不允许修改 protocol/path/method
            if (!Objects.equals(existingDef.getPath(),     newDefinition.getPath()) ||
                !Objects.equals(existingDef.getMethod(),   newDefinition.getMethod()) ||
                !Objects.equals(existingDef.getProtocol(), newDefinition.getProtocol())) {
                throw new ProtectedWorkflowModificationException(existingDef.getId(),
                    "不允许修改受保护元工作流的路由配置（protocol/path/method）");
            }

            // 创建快照（先校验通过后才备份）
            versionSnapshotRepository.save(VersionSnapshot.from(existingDef));
        }
    }
}
```

### 19.4 Bootstrap 失败安全（三级加载）

```java
/**
 * 元工作流加载器 — 三级降级保护
 * 防止自举死锁（管理后台依赖 DB，DB 连接失败时平台完全无法启动）
 *
 * 三级策略：
 *   Level 1（DB）：正常启动，从 DB 加载所有元工作流（含 is_protected=1）
 *   Level 2（classpath）：DB 不可用时，从 classpath:/meta-workflows/*.yaml 加载最小可用集
 *   Level 3（LegacyController fallback）：classpath 文件损坏时，退化到硬编码的最小 REST 接口
 */
@Component
public class MetaWorkflowLoader {

    @Value("${workflow.bootstrap-mode:false}")
    private boolean bootstrapMode;  // --bootstrap-mode=true 强制使用 classpath 模式

    public List<WorkflowDefinition> load() {
        if (!bootstrapMode) {
            try {
                List<WorkflowDefinition> fromDb = repository.findByCategory("META");
                if (!fromDb.isEmpty()) {
                    log.info("Bootstrap Level 1: loaded {} meta-workflows from DB", fromDb.size());
                    return fromDb;
                }
            } catch (DataAccessException e) {
                log.warn("Bootstrap Level 1 failed (DB unavailable), falling back to Level 2", e);
            }
        }

        // Level 2：classpath 保底
        try {
            List<WorkflowDefinition> fromClasspath = loadFromClasspath("classpath:/meta-workflows/*.yaml");
            if (!fromClasspath.isEmpty()) {
                log.warn("Bootstrap Level 2: loaded {} meta-workflows from classpath", fromClasspath.size());
                return fromClasspath;
            }
        } catch (Exception e) {
            log.error("Bootstrap Level 2 failed", e);
        }

        // Level 3：硬编码最小接口（仅保留修复 DB 必要的 API）
        log.error("Bootstrap Level 3: using minimal hardcoded fallback endpoints");
        return LegacyMetaWorkflows.getMinimalSet();
    }
}
```

### 19.5 Bootstrap 保护规则

| 保护规则 | 说明 |
|---------|------|
| `is_protected=1` 的工作流不可删除 | 防止管理员误操作删除核心 API 元工作流 |
| 不可修改 protocol/path/method | 路由变更可能导致 Admin 后台失联 |
| 变更需要先通过 MetaWorkflowGuard 校验 | 确保新内容合法再备份旧版本 |
| classpath 保底文件版本控制 | 随应用 JAR 发版，防止线上 classpath 与 DB 不一致 |

---

## 20. 技术选型与实施路线图

### 20.1 核心技术栈

| 分层 | 选型 | 版本 | 说明 |
|------|------|------|------|
| **语言** | Java 21 + Kotlin 2.1 | — | Java 核心，Kotlin 协程+适配器 |
| **框架** | Spring Boot 3.x | 3.2+ | 主框架，AOT 优化 |
| **协程** | Kotlin Coroutines | 1.7+ | DAG 并行执行 |
| **持久化** | MyBatis-Plus / JdbcTemplate | — | 工作流元数据存储 |
| **缓存** | Caffeine（L1）+ Redis（L2）| — | 工作流定义缓存 |
| **消息队列** | Kafka / RocketMQ（可选）| — | 异步工作流触发 |
| **远程调用** | gRPC + Dubbo（可选）| — | Function Gateway |
| **脚本** | Groovy 4.x + GraalVM JS | — | 动态脚本函数 |
| **指标** | Micrometer + Prometheus | — | 全量 Metrics |
| **追踪** | OpenTelemetry Java Agent | 1.x | 分布式 Trace |
| **日志** | Logback + JSON 结构化 | — | ELK/Loki 接入 |
| **构建** | Gradle 8.x（多模块）| — | 模块化构建 |
| **容器** | Docker + Kubernetes | — | 部署运行 |

### 20.2 实施路线图

```
Phase 1  【核心层】 ✅ 已完成
  ✓ ImmutableExecutionState / NodeInput / FunctionResult
  ✓ WorkflowEngine（线性流）
  ✓ DagExecutor（Kotlin Coroutines 并行）
  ✓ SagaExecutor 补偿事务
  ✓ SchemaValidator（JSON Schema Draft-07）
  ✓ DynamicSqlGenerator（参数化 SQL）
  ✓ RetryScheduler（HashedWheelTimer 时间轮）

Phase 2  【函数层】 ✅ 已完成
  ✓ FunctionRegistry（优先级 custom > builtin > script > external）
  ✓ builtin:httpCall / builtin:redisCommand
  ✓ GroovyScriptFunction + JsScriptFunction（GraalVM）
  ✓ Redis SPI（Lettuce + Redisson 双适配器）
  ✓ 装饰器体系（metrics/trace/rateLimit/cache/async）

Phase 3  【适配器层】 ✅ 已完成
  ✓ UnifiedRequest + WorkflowRouter SPI
  ✓ SpringMVC 动态路由适配器（RouteConfigStore SPI）
  ✓ Kafka 消费者适配器
  ✓ Dubbo 泛化调用 / gRPC 适配器

Phase 4  【配置中心 + 实例注册】 ✅ 已完成
  ✓ DefinitionProvider + ConfigBackedDefinitionProvider
  ✓ 配置中心 SPI（Nacos / Apollo / HTTP）
  ✓ InstanceRegistry SPI（Nacos / Apollo / HTTP）
  ✓ 定向发布（targetGroups）

Phase 5  【Admin 后台】 ⚠️ 部分完成
  ✓ 工作流 CRUD / 发布 / 下线
  ✓ 调试系统（DebugService + DebugController）
  ✓ 实例管理 API
  ✗ 版本 Diff 对比（§22.1）
  ✗ 工作流回滚（§22.2）
  ✗ 执行审计日志完整 API（§22.6）

Phase 6  【安全 + 灰度】 ⚠️ 待实现
  ✗ RBAC 完整实现（§22.4）
  ✗ 灰度发布策略（§22.3）
  ✗ MetaWorkflow 三级自举（§22.9）
  ✗ AdapterManager 统一管理（§22.8）

Phase 7  【扩展功能】 ⚠️ 待实现
  ✗ MqConsumerAdapter 统一 SPI + RocketMQ 适配器（§22.5）
  ✗ OpenAPI 导入/导出（§22.7）
  ✗ 性能压测 + 监控告警配置
```

### 20.3 性能目标

| 指标 | 目标值 | 测量方法 |
|------|--------|---------|
| 工作流执行 P99 | < 50ms（3 节点以内） | JMeter 压测 |
| 节点执行 P99 | < 10ms（不含 I/O） | Micrometer |
| 热加载延迟 | < 30s（L1）/ < 500ms（L2/L3） | 端到端测试 |
| 内存占用 | < 2GB（1000 并发工作流） | JVM Heap Dump |
| GC 停顿 | < 5ms（ZGC） | GC Log 分析 |

---

## 21. 多语言接入方案（Polyglot）

### 21.1 方案概述

非 JVM 语言（Python / Go / Node.js）通过 **Function Gateway** 接入，以 gRPC/HTTP 协议提供函数实现：

```
JVM 内部（fluxion-core）
  WorkflowEngine → FunctionRegistry
                        │
                        └──► ExternalServiceRef (EXTERNAL 类型)
                                    │
                        ────────────┼────────────────
                        gRPC / HTTP │（跨进程调用）
                        ────────────┼────────────────
                                    │
                    Python Runtime   Go Runtime   Node.js Runtime
                    (Port 9001)     (Port 9002)   (Port 9003)
```

### 21.2 Function Gateway 协议定义

```protobuf
// function_gateway.proto
syntax = "proto3";
package workflow.gateway;

service FunctionGateway {
    // 同步调用
    rpc InvokeFunction(FunctionRequest) returns (FunctionResponse);
    // 流式调用（大数据量场景）
    rpc InvokeFunctionStream(FunctionRequest) returns (stream FunctionChunk);
    // 健康检查
    rpc Health(HealthRequest) returns (HealthResponse);
}

message FunctionRequest {
    string function_ref   = 1;  // 函数引用名
    string node_input_json = 2; // NodeInput 序列化（JSON）
    string execution_id   = 3;
    string workflow_id    = 4;
    int32  timeout_ms     = 5;
}

message FunctionResponse {
    bool   success       = 1;
    string output_json   = 2;   // FunctionResult.output 序列化
    string error_message = 3;
    repeated SideEffectProto side_effects = 4;
}

message SideEffectProto {
    string type    = 1;
    string target  = 2;
    string payload_json = 3;
    string compensate_function_ref = 4;
}
```

### 21.3 Python 客户端 SDK 示例

```python
# workflow_function.py — Python 函数 SDK
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any, Dict, List, Optional
import grpc
from workflow_gateway_pb2 import FunctionResponse
from workflow_gateway_pb2_grpc import FunctionGatewayServicer

@dataclass
class NodeInput:
    direct_input: Any
    declared_deps: Dict[str, Any]
    workflow_input: Dict[str, Any]
    node_params: Dict[str, Any]
    execution_id: str
    workflow_id: str

@dataclass
class SideEffect:
    type: str           # "DB_UPDATE" / "MQ_SEND" / "RPC_CALL"
    target: str
    payload: Any
    compensate_function_ref: Optional[str] = None

@dataclass
class FunctionResult:
    output: Any
    side_effects: List[SideEffect] = None

    @staticmethod
    def success(output: Any) -> 'FunctionResult':
        return FunctionResult(output=output, side_effects=[])

class WorkflowFunction(ABC):
    """Python 工作流函数基类（与 Java 接口语义对齐）"""

    @abstractmethod
    def apply(self, input: NodeInput) -> FunctionResult:
        pass

    @property
    def name(self) -> str:
        return self.__class__.__name__

# ─── 函数网关 gRPC 服务 ──────────────────────────────────────────
class FunctionGatewayServer(FunctionGatewayServicer):

    def __init__(self):
        self.functions: Dict[str, WorkflowFunction] = {}

    def register(self, fn: WorkflowFunction):
        self.functions[fn.name] = fn

    def InvokeFunction(self, request, context):
        fn = self.functions.get(request.function_ref)
        if fn is None:
            return FunctionResponse(success=False, error_message=f"Function not found: {request.function_ref}")
        try:
            node_input = json.loads(request.node_input_json)
            result = fn.apply(NodeInput(**node_input))

            # ★ 序列化 sideEffects 回传，确保 Java 侧 SagaExecutor 可感知非 JVM 函数的副作用
            side_effects_proto = []
            for se in (result.side_effects or []):
                side_effects_proto.append(SideEffectProto(
                    type=se.type,
                    target=se.target,
                    payload_json=json.dumps(se.payload),
                    compensate_function_ref=se.compensate_function_ref or ""
                ))

            return FunctionResponse(
                success=True,
                output_json=json.dumps(result.output),
                side_effects=side_effects_proto  # ★ 必须回传，否则跨语言 Saga 补偿无效
            )
        except Exception as e:
            return FunctionResponse(success=False, error_message=str(e))

# ─── 使用示例 ────────────────────────────────────────────────────
class RecommendFunction(WorkflowFunction):
    """Python ML 推荐函数示例"""
    def apply(self, input: NodeInput) -> FunctionResult:
        user_id     = input.direct_input.get("userId")
        model_name  = input.node_params.get("modelName", "default")
        # ... 调用 ML 模型 ...
        recommendations = recommend_model.predict(user_id, model_name)
        return FunctionResult.success({"recommendations": recommendations})
```

---

## 22. 待实现功能详细设计

> 以下功能在第一版方案（workflow-config-platform-design.md）中提出，当前方案中已有设计但尚未编码实现。

### 22.1 版本 Diff 对比

**API 端点**：`GET /api/admin/workflows/{id}/diff?v1={v}&v2={v}`

```java
/**
 * 版本对比服务 — 对比两个版本的 WorkflowDefinition 差异
 *
 * 输出：结构化 diff（新增/删除/修改的节点、函数引用变更、Schema 变更）
 * 前端用途：可视化 diff 展示（类似 Git diff）
 */
public class VersionDiffService {

    /**
     * 对比两个版本
     * @param workflowId 工作流 ID
     * @param v1 版本号 A
     * @param v2 版本号 B
     * @return 差异报告
     */
    public DiffReport diff(String workflowId, int v1, int v2) {
        WorkflowDefinition defA = definitionRepository.findByVersion(workflowId, v1);
        WorkflowDefinition defB = definitionRepository.findByVersion(workflowId, v2);
        return DiffReport.builder()
            .addedNodes(findAddedNodes(defA, defB))
            .removedNodes(findRemovedNodes(defA, defB))
            .modifiedNodes(findModifiedNodes(defA, defB))
            .schemaChanges(diffSchema(defA, defB))
            .build();
    }
}

/**
 * Diff 结果值对象
 */
public record DiffReport(
    List<WorkflowNode> addedNodes,
    List<WorkflowNode> removedNodes,
    List<NodeDiff> modifiedNodes,
    List<SchemaDiff> schemaChanges
) {}

public record NodeDiff(
    String nodeId,
    String field,      // "functionRef" / "params" / "dependsOn" / "decorators"
    Object oldValue,
    Object newValue
) {}
```

### 22.2 工作流回滚

**API 端点**：`POST /api/admin/workflows/{id}/rollback?targetVersion={v}`

```java
/**
 * 回滚策略：
 *   1. 将目标版本的 WorkflowDefinition 复制为新版本（version = current + 1）
 *   2. 状态设置为 DRAFT（需手动 publish）
 *   3. 或直接回滚并发布（rollbackAndPublish）
 *
 * 安全约束：
 *   - 仅 ADMIN 角色可执行回滚
 *   - 元工作流不可回滚（is_protected=1）
 *   - 回滚前检查函数引用是否仍然存在
 */
public class WorkflowRollbackService {

    public WfDefinition rollback(String workflowId, int targetVersion) {
        WfDefinition target = repository.findByVersion(workflowId, targetVersion);
        if (target == null) throw new VersionNotFoundException(workflowId, targetVersion);

        // 复制为新草稿版本
        WfDefinition draft = target.copyAsDraft(nextVersion(workflowId));
        // 校验函数引用有效性
        validateFunctionRefs(draft);
        return repository.save(draft);
    }

    public WfDefinition rollbackAndPublish(String workflowId, int targetVersion,
                                            List<String> targetGroups) {
        WfDefinition draft = rollback(workflowId, targetVersion);
        return publishService.publish(draft.getId(), targetGroups);
    }
}
```

### 22.3 灰度发布策略

在现有 `targetGroups` 定向发布基础上，扩展为完整灰度发布能力：

```java
/**
 * 灰度发布策略 — 渐进式流量切换
 *
 * 支持三种模式：
 *   1. TARGET_GROUPS：按应用分组定向发布（已实现）
 *   2. PERCENTAGE：按流量百分比灰度
 *   3. CANARY：金丝雀实例发布
 */
public interface GrayReleaseStrategy {
    /** 判断当前请求是否应路由到新版本 */
    boolean shouldRouteToNewVersion(UnifiedRequest request, GrayReleaseRule rule);
}

/**
 * 灰度规则（存储在 wf_definition 或独立表）
 */
public record GrayReleaseRule(
    String workflowId,
    int newVersion,
    int oldVersion,
    GrayMode mode,           // TARGET_GROUPS / PERCENTAGE / CANARY
    int percentage,          // mode=PERCENTAGE 时：0-100
    List<String> targetGroups, // mode=TARGET_GROUPS 时
    List<String> canaryInstances, // mode=CANARY 时
    long startTime,
    long endTime             // 灰度窗口
) {}

public enum GrayMode { TARGET_GROUPS, PERCENTAGE, CANARY }

/**
 * 百分比灰度实现（基于请求特征哈希，确保同一用户始终命中同版本）
 */
public class PercentageGrayStrategy implements GrayReleaseStrategy {
    @Override
    public boolean shouldRouteToNewVersion(UnifiedRequest request, GrayReleaseRule rule) {
        String hashKey = extractHashKey(request); // userId / requestId / IP
        int bucket = Math.abs(hashKey.hashCode() % 100);
        return bucket < rule.percentage();
    }
}
```

**DDL 补充**：

```sql
CREATE TABLE wf_gray_release (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id     VARCHAR(64) NOT NULL,
    new_version     INT NOT NULL,
    old_version     INT NOT NULL,
    mode            VARCHAR(20) NOT NULL DEFAULT 'TARGET_GROUPS',
    percentage      INT DEFAULT 0,
    target_groups   JSON,
    canary_instances JSON,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / COMPLETED / ABORTED
    start_time      DATETIME NOT NULL,
    end_time        DATETIME,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_workflow_status (workflow_id, status)
);
```

### 22.4 RBAC 完整实现

§17 已定义权限模型，以下为完整实现方案：

```java
/**
 * RBAC 用户管理 Controller
 *
 * API：
 *   POST   /api/admin/users              创建用户
 *   GET    /api/admin/users              用户列表
 *   PUT    /api/admin/users/{id}/role    修改角色
 *   DELETE /api/admin/users/{id}         删除用户
 *   POST   /api/admin/auth/login         登录（返回 JWT）
 *   POST   /api/admin/auth/refresh       刷新 Token
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserController {
    // DDL 中 wf_user 表已定义（含 role: ADMIN/DEVELOPER/OPERATOR/VIEWER）
}

/**
 * Spring Security 配置（SecurityFilterChain）
 *
 * 路径权限映射：
 *   /api/admin/workflows/*/publish  → ADMIN only
 *   /api/admin/workflows/**         → ADMIN, DEVELOPER
 *   /api/admin/executions/**        → ADMIN, DEVELOPER, OPERATOR
 *   /api/admin/debug/**             → ADMIN, DEVELOPER
 *   /api/admin/meta/**              → ADMIN only
 *   /api/admin/users/**             → ADMIN only
 */
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/admin/auth/**").permitAll()
                .requestMatchers("/api/admin/users/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/meta/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/admin/workflows/*/publish").hasRole("ADMIN")
                .requestMatchers("/api/admin/debug/**").hasAnyRole("ADMIN", "DEVELOPER")
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "DEVELOPER", "OPERATOR")
                .anyRequest().authenticated()
            ).build();
    }
}
```

### 22.5 MqConsumerAdapter SPI（统一 MQ 适配）

当前 Kafka 适配器为独立实现，缺少统一的 MQ 消费 SPI：

```java
/**
 * MQ 消费者适配器 SPI — 统一异步事件触发模式
 *
 * 与 WorkflowAdapter（同步请求-响应）互补：
 *   - 无 HTTP 响应返回（仅 ACK/NACK）
 *   - 消息到达时主动触发工作流
 *   - 支持批量消费、重试、死信队列
 *
 * 实现方：
 *   - KafkaMqConsumerAdapter（已有 fluxion-adapter-mq-kafka，需适配此 SPI）
 *   - RocketMqConsumerAdapter（待实现）
 *   - RabbitMqConsumerAdapter（按需）
 */
public interface MqConsumerAdapter {

    /** 将 MQ 消息转为统一请求 */
    UnifiedRequest toUnified(Object mqMessage);

    /** 处理引擎执行结果（ACK/NACK/记录日志） */
    void handleResult(EngineResult result, Object mqMessage);

    /** MQ 类型标识 */
    String mqType();  // "KAFKA" / "ROCKETMQ" / "RABBITMQ"

    /**
     * 订阅主题并绑定工作流
     * @param topic         主题/队列名
     * @param consumerGroup 消费者组
     * @param workflowId    绑定的工作流 ID
     */
    void subscribe(String topic, String consumerGroup, String workflowId);

    /** 取消订阅 */
    void unsubscribe(String topic, String consumerGroup);
}
```

**RocketMQ 适配器模块**：

```
fluxion-adapter-mq-rocketmq/                      ← 嵌套在 fluxion-adapter-mq 能力域下
├── build.gradle.kts
└── src/main/kotlin/com/fluxion/adapter/mq/rocketmq/
    ├── RocketMqConsumerAdapter.kt       ← MqConsumerAdapter 实现
    ├── RocketMqWorkflowListener.kt      ← MessageListenerConcurrently
    └── config/RocketMqAdapterAutoConfiguration.kt
```

### 22.6 执行审计日志 API

DDL 中已有 `wf_execution_log` 表，补充完整的查询/管理能力：

```java
/**
 * 执行日志 Controller（补充完整实现）
 *
 * 功能：
 *   - 分页查询（按 workflowId / status / 时间范围 / IP）
 *   - 获取执行详情（含所有节点 trace 记录）
 *   - 获取执行的 ImmutableExecutionState 快照（用于重放）
 *   - 统计聚合（成功率、P99 延迟趋势、错误 Top N）
 *   - 日志清理策略（保留天数配置）
 */
@RestController
@RequestMapping("/api/admin/executions")
public class ExecutionLogController {

    /** 分页查询 */
    @GetMapping
    public Page<ExecutionLogSummary> list(ExecutionLogQuery query, Pageable pageable);

    /** 执行详情（含完整 trace） */
    @GetMapping("/{executionId}")
    public ExecutionLogDetail detail(@PathVariable String executionId);

    /** 获取执行状态快照（调试/重放用） */
    @GetMapping("/{executionId}/state")
    public ImmutableExecutionState getState(@PathVariable String executionId);

    /** 统计聚合 */
    @GetMapping("/stats")
    public ExecutionStats stats(@RequestParam String workflowId,
                                @RequestParam @DateTimeFormat long from,
                                @RequestParam @DateTimeFormat long to);

    /** 从指定节点重放 */
    @PostMapping("/{executionId}/rerun")
    public EngineResult rerun(@PathVariable String executionId,
                              @RequestBody RerunRequest request);
}

public record ExecutionLogQuery(
    String workflowId,
    String status,       // SUCCESS / FAILED / TIMEOUT
    Long fromTime,
    Long toTime,
    String clientIp
) {}
```

### 22.7 OpenAPI 导入/导出

从 OpenAPI 3.0 规范自动生成工作流定义骨架：

```java
/**
 * OpenAPI 导入服务
 *
 * 功能：
 *   - 解析 OpenAPI 3.0 YAML/JSON 文件
 *   - 为每个 path + method 生成工作流草稿
 *   - 自动提取 requestBody → inputSchema
 *   - 自动提取 responses.200 → outputSchema
 *   - 生成默认节点链：validate → process → response
 *
 * API：
 *   POST /api/admin/import/openapi   （multipart file upload）
 *   GET  /api/admin/export/openapi/{workflowId}  （导出为 OpenAPI 规范）
 */
public class OpenApiImportService {

    public List<WfDefinition> importFromOpenApi(String openApiSpec) {
        OpenAPI api = new OpenAPIV3Parser().readContents(openApiSpec).getOpenAPI();
        List<WfDefinition> drafts = new ArrayList<>();

        api.getPaths().forEach((path, pathItem) -> {
            pathItem.readOperationsMap().forEach((method, operation) -> {
                WfDefinition draft = WfDefinition.builder()
                    .id(generateId(path, method))
                    .name(operation.getSummary())
                    .inputSchema(extractInputSchema(operation))
                    .outputSchema(extractOutputSchema(operation))
                    .status("DRAFT")
                    .protocolBinding(Map.of("HTTP", path, "method", method.name()))
                    .build();
                drafts.add(draft);
            });
        });
        return drafts;
    }
}
```

### 22.8 AdapterManager 统一生命周期管理

当前各适配器独立装配，缺少统一的适配器管理器：

```java
/**
 * 适配器统一管理器
 *
 * 职责：
 *   1. 统一管理所有 WorkflowAdapter + MqConsumerAdapter 的生命周期
 *   2. 工作流发布时自动注册路由到对应适配器
 *   3. 工作流下线时自动注销
 *   4. 提供适配器状态查询（健康检查）
 */
public class AdapterManager {

    private final Map<String, WorkflowAdapter> syncAdapters;       // protocol → adapter
    private final Map<String, MqConsumerAdapter> asyncAdapters;    // mqType → adapter

    /**
     * 工作流发布时调用 — 根据 protocolBinding 注册到对应适配器
     */
    public void onWorkflowPublished(WorkflowDefinition def) {
        Map<String, String> binding = def.getProtocolBinding();
        if (binding == null) return;

        String protocol = binding.get("protocol");
        if (syncAdapters.containsKey(protocol)) {
            syncAdapters.get(protocol).registerRoute(def);
        } else if (asyncAdapters.containsKey(protocol)) {
            asyncAdapters.get(protocol).subscribe(
                binding.get("topic"), binding.get("consumerGroup"), def.getId());
        }
    }

    /**
     * 工作流下线时调用
     */
    public void onWorkflowDeprecated(String workflowId, String protocol) {
        if (syncAdapters.containsKey(protocol)) {
            syncAdapters.get(protocol).unregisterRoute(workflowId);
        }
    }

    /** 适配器健康状态 */
    public Map<String, AdapterHealth> healthCheck() {
        // 返回各适配器当前注册的路由数、活跃连接数等
    }
}
```

### 22.9 MetaWorkflow 三级自举加载器

§19 已有设计概念，以下为完整加载器实现方案：

```java
/**
 * 元工作流三级自举加载器
 *
 * 启动顺序（严格降级）：
 *   Level 1: DB 加载 → 优先从 wf_definition 表加载 category=META 的定义
 *   Level 2: Classpath 加载 → DB 不可用时从 classpath:meta-workflows/*.json 加载
 *   Level 3: 硬编码兜底 → classpath 也没有时使用内置最小化定义（仅保障 CRUD）
 *
 * 自举保护：
 *   - 元工作流 is_protected=1，不允许通过普通 API 删除/修改
 *   - MetaWorkflowGuard 拦截器在每次修改前校验
 *   - 启动时 Bootstrap 完成后才开放普通工作流加载
 */
public class MetaWorkflowBootstrapLoader {

    private static final Logger log = LoggerFactory.getLogger(MetaWorkflowBootstrapLoader.class);

    public List<WorkflowDefinition> bootstrap() {
        // Level 1: DB
        try {
            List<WorkflowDefinition> metaDefs = repository.findByCategory("META");
            if (!metaDefs.isEmpty()) {
                log.info("Meta-workflow bootstrap: loaded {} definitions from DB", metaDefs.size());
                return metaDefs;
            }
        } catch (Exception e) {
            log.warn("Meta-workflow bootstrap: DB unavailable, falling back to classpath", e);
        }

        // Level 2: Classpath
        List<WorkflowDefinition> classpathDefs = loadFromClasspath("meta-workflows/");
        if (!classpathDefs.isEmpty()) {
            log.info("Meta-workflow bootstrap: loaded {} definitions from classpath", classpathDefs.size());
            return classpathDefs;
        }

        // Level 3: 硬编码最小化定义
        log.warn("Meta-workflow bootstrap: using hardcoded minimal definitions");
        return List.of(
            MinimalMetaWorkflows.CRUD_WORKFLOW,
            MinimalMetaWorkflows.PUBLISH_WORKFLOW
        );
    }
}

/**
 * 元工作流修改保护拦截器
 */
@Component
public class MetaWorkflowGuard {

    public void checkModification(String workflowId, String operation) {
        WorkflowDefinition def = definitionCache.get(workflowId);
        if (def != null && def.isProtected()) {
            throw new MetaWorkflowProtectedException(
                "Cannot " + operation + " protected meta-workflow: " + workflowId);
        }
    }
}
```

---

## 附录 A：模块划分与构建

#### SPI + Spring Boot 分离原则

所有功能模块遵循统一分层：

| 子模块类型 | 依赖约束 | 职责 |
|-----------|---------|------|
| `*-core` | 零 Spring Boot 依赖（纯 Java/Kotlin） | SPI 接口、值对象、核心实现逻辑 |
| `*-spring-boot` | `spring-boot-autoconfigure`（compileOnly） | `@AutoConfiguration` + `@Conditional*` 装配桥接 |
| 具体实现模块 | 引入对应三方库 | 通过 `@ConditionalOnMissingBean` 互斥注册 |

#### 完整模块树

```
fluxion-platform/
│
├── fluxion-core/                               ★ 纯 Kotlin，零框架依赖
│   ├── engine/WorkflowEngine.kt                ← 引擎（ImmutableExecutionState）
│   ├── engine/DagExecutor.kt                   ← Kotlin Coroutines 并行 DAG
│   ├── engine/SagaExecutor.kt                  ← Saga 补偿事务
│   ├── model/ImmutableExecutionState.kt        ← 核心：不可变执行状态
│   ├── model/WorkflowNode.kt
│   ├── function/WorkflowFunction.kt            ← 函数接口
│   ├── decorator/NodeDecorator.kt              ← 装饰器 SPI
│   ├── log/ExecutionLogStore.kt                ← 执行日志 SPI
│   ├── metrics/WorkflowMetrics.kt              ← 指标 SPI
│   └── schema/SchemaValidator.kt
│
├── fluxion-adapter-spi/                        ★ 纯 Kotlin，零框架依赖
│   ├── UnifiedRequest.kt
│   ├── WorkflowRouter.kt
│   └── WorkflowAdapter.kt
│
├── fluxion-adapter-http/                       HTTP 能力域（嵌套子模块）
│   ├── fluxion-adapter-http-core/              ★ 零 Spring 依赖
│   │   ├── HttpRouteDefinition.kt
│   │   └── RouteConfigStore.kt
│   └── fluxion-adapter-http-springmvc/         Spring MVC 动态路由适配器
│       ├── handler/WorkflowHttpHandler.kt      ← 统一请求处理器
│       ├── registry/WorkflowRouteRegistry.kt   ← 动态 registerMapping
│       ├── config/SpringMvcAdapterAutoConfiguration.kt
│       ├── fluxion-adapter-http-springmvc-nacos/   ← Nacos 路由配置（可选）
│       │   └── NacosRouteConfigStore.kt
│       ├── fluxion-adapter-http-springmvc-apollo/  ← Apollo 路由配置（可选）
│       │   └── ApolloRouteConfigStore.kt
│       └── fluxion-adapter-http-springmvc-spring-boot/  ← 统一自动装配
│           └── HttpAdapterAutoConfiguration.kt
│
├── fluxion-adapter-rpc/                        RPC 能力域（嵌套子模块）
│   ├── fluxion-adapter-rpc-dubbo/              Dubbo 泛化调用适配器 (Kotlin)
│   ├── fluxion-adapter-rpc-grpc/               gRPC 适配器 (Kotlin + protobuf)
│   └── fluxion-adapter-rpc-spring-boot/        RPC 适配器统一装配
│
├── fluxion-adapter-mq/                         MQ 能力域（嵌套子模块）
│   ├── fluxion-adapter-mq-kafka/               Kafka 消费者适配器 (Kotlin)
│   └── fluxion-adapter-mq-spring-boot/         MQ 适配器统一装配
│   └── fluxion-adapter-mq-rocketmq/            RocketMQ 消费者适配器（待实现）
│
├── fluxion-decorator-impl/                     装饰器实现（嵌套子模块）
│   ├── fluxion-decorator-impl-core/            ★ 零 Spring Boot 依赖
│   │   ├── impl/AsyncDecorator.kt
│   │   ├── impl/CacheDecorator.kt
│   │   ├── impl/MetricsDecorator.kt
│   │   ├── impl/RateLimitDecorator.kt
│   │   ├── impl/TraceDecorator.kt
│   │   └── config/DecoratorImplRegistrar.kt
│   └── fluxion-decorator-impl-spring-boot/     ← @AutoConfiguration 装配
│       └── config/DecoratorImplAutoConfiguration.kt
│
├── fluxion-redis/                              Redis 函数体系（嵌套子模块）
│   ├── fluxion-redis-core/                     ★ 零 Spring Boot 依赖
│   │   ├── spi/RedisClientAdapter.kt           ← SPI 接口
│   │   ├── spi/RedisRawCommand.kt
│   │   └── function/RedisCommandFunction.kt    ← builtin:redisCommand（单一函数）
│   ├── fluxion-redis-spring-boot/              ← @AutoConfiguration 装配
│   │   └── config/RedisWorkflowAutoConfiguration.kt
│   ├── fluxion-redis-lettuce/                  ← Lettuce 适配器（可选引入）
│   │   └── lettuce/LettuceRedisAdapter.kt      120+ 命令 + 真 Pipeline
│   ├── fluxion-redis-redisson/                 ← Redisson 适配器（可选引入）
│   │   └── redisson/RedissonRedisAdapter.kt    类型化 API + 原生分布式锁
│   └── fluxion-redis-spring-data/              ← Spring Data Redis 适配器（可选引入）
│       └── springdata/SpringDataRedisAdapter.kt
│
├── fluxion-builtin-functions/                  内置函数库（嵌套子模块）
│   ├── fluxion-builtin-functions-core/         ★ 零 Spring 依赖
│   │   ├── http/HttpCallFunction.kt
│   │   ├── json/JsonExtractFunction.kt
│   │   └── mq/MqPublishFunction.kt
│   ├── fluxion-builtin-functions-jdbc/         JDBC 函数实现（可选）
│   └── fluxion-builtin-functions-spring-boot/  ← @AutoConfiguration 装配
│
├── fluxion-script-engine/                      脚本引擎（嵌套子模块）
│   ├── fluxion-script-engine-core/             ★ 零 Spring 依赖
│   │   ├── groovy/GroovyScriptFunction.kt
│   │   └── js/JsScriptFunction.kt
│   └── fluxion-script-engine-spring-boot/      ← @AutoConfiguration 装配
│
├── fluxion-config/                             配置中心能力域（嵌套子模块）
│   ├── fluxion-config-core/                    ★ 零 Spring 依赖
│   ├── fluxion-config-http/                    HTTP 配置中心（Admin → Worker 推送）
│   ├── fluxion-config-apollo/                  Apollo 配置中心实现
│   ├── fluxion-config-nacos/                   Nacos 配置中心实现
│   └── fluxion-config-spring-boot/             ← @AutoConfiguration 装配
│
├── fluxion-di/                                 依赖注入能力域（嵌套子模块）
│   ├── fluxion-di-core/                        ★ 零 Spring 依赖
│   └── fluxion-di-spring/                      Spring 上下文桥接
│
├── fluxion-runtime/                            运行面 / sidecar（嵌套子模块）
│   ├── fluxion-runtime-core/                   ★ 零 Spring 依赖
│   │   ├── router/RuntimeInboundRouter.kt     ← Runtime 侧 WorkflowRouter 实现
│   │   └── spi/ExecutionSnapshotStore.kt       ← 执行快照 SPI
│   ├── fluxion-runtime-spring-boot/            ← @AutoConfiguration 装配
│   │   ├── FluxionRuntimeAutoConfiguration.kt
│   │   └── store/LoggingExecutionSnapshotStore.kt
│   └── fluxion-runtime/                        可独立启动的 Spring Boot 应用
│       ├── controller/RuntimeWorkflowController.kt
│       └── FluxionRuntimeApplication.kt
│
└── fluxion-admin/                              配置管理后台 (Kotlin + Spring Boot)
    ├── controller/WfDefinitionController.kt
    ├── controller/WfNodeController.kt
    └── entity/WfDefinition.kt
```

#### settings.gradle.kts（Gradle 嵌套子模块声明）

```kotlin
include(
    "fluxion-core",
    // fluxion-decorator-impl 子模块群
    "fluxion-decorator-impl:fluxion-decorator-impl-core",
    "fluxion-decorator-impl:fluxion-decorator-impl-spring-boot",
    "fluxion-adapter-spi",
    // fluxion-adapter-http 子模块群（HTTP 能力域）
    "fluxion-adapter-http:fluxion-adapter-http-core",
    "fluxion-adapter-http:fluxion-adapter-http-springmvc",
    "fluxion-adapter-http:fluxion-adapter-http-springmvc:fluxion-adapter-http-springmvc-nacos",
    "fluxion-adapter-http:fluxion-adapter-http-springmvc:fluxion-adapter-http-springmvc-apollo",
    "fluxion-adapter-http:fluxion-adapter-http-springmvc:fluxion-adapter-http-springmvc-spring-boot",
    // fluxion-adapter-rpc 子模块群（RPC 能力域）
    "fluxion-adapter-rpc:fluxion-adapter-rpc-dubbo",
    "fluxion-adapter-rpc:fluxion-adapter-rpc-grpc",
    "fluxion-adapter-rpc:fluxion-adapter-rpc-spring-boot",
    // fluxion-adapter-mq 子模块群（MQ 能力域）
    "fluxion-adapter-mq:fluxion-adapter-mq-kafka",
    "fluxion-adapter-mq:fluxion-adapter-mq-spring-boot",
    // fluxion-builtin-functions 子模块群（内置函数能力域）
    "fluxion-builtin-functions:fluxion-builtin-functions-core",
    "fluxion-builtin-functions:fluxion-builtin-functions-spring-boot",
    // fluxion-redis 子模块群（Redis 能力域）
    "fluxion-redis:fluxion-redis-core",
    "fluxion-redis:fluxion-redis-spring-boot",
    "fluxion-redis:fluxion-redis-lettuce",
    "fluxion-redis:fluxion-redis-redisson",
    "fluxion-redis:fluxion-redis-spring-data",
    // fluxion-script-engine 子模块群（脚本引擎能力域）
    "fluxion-script-engine:fluxion-script-engine-core",
    "fluxion-script-engine:fluxion-script-engine-spring-boot",
    // fluxion-config 子模块群（配置中心能力域）
    "fluxion-config:fluxion-config-core",
    "fluxion-config:fluxion-config-apollo",
    "fluxion-config:fluxion-config-nacos",
    "fluxion-config:fluxion-config-http",
    "fluxion-config:fluxion-config-spring-boot",
    // fluxion-di 子模块群（依赖注入能力域）
    "fluxion-di:fluxion-di-core",
    "fluxion-di:fluxion-di-spring",
    // fluxion-runtime 子模块群（运行面 / sidecar）
    "fluxion-runtime:fluxion-runtime-core",
    "fluxion-runtime:fluxion-runtime-spring-boot",
    "fluxion-runtime",
    "fluxion-admin"
)
```

---

## 附录 B：旧版函数兼容迁移路径

### 附录 B.1 兼容层设计

为了让依赖 `WorkflowContext` 的旧版函数不需要立刻重写，提供一个兼容适配器：

```java
/**
 * 旧版函数适配器 — 将旧签名的函数包装为当前接口
 * 迁移期间使用，新函数请直接实现 WorkflowFunction<O>
 *
 * @deprecated 请迁移到 WorkflowFunction<O> 接口
 */
@Deprecated
public class V1FunctionAdapter<I, O> implements WorkflowFunction<O> {

    private final apijson.demo.legacy.WorkflowFunction<I, O> legacyFunction;

    @Override
    public FunctionResult<O> apply(NodeInput input) {
        // 构建一个"只读"的 WorkflowContext 兼容层
        // 注意：此处的 ctx 禁止写入，任何 put() 调用会抛出 UnsupportedOperationException
        ReadOnlyWorkflowContext ctx = ReadOnlyWorkflowContext.from(input);
        try {
            O result = v1Function.apply((I) input.directInput(), ctx);
            return FunctionResult.success(result);
        } catch (Exception e) {
            throw e;
        }
    }
}
```

### 附录 B.2 迁移步骤

```
阶段 0（当前）：旧版函数 + 旧引擎
阶段 1：部署新引擎 + V1FunctionAdapter（全部旧函数用适配器包装）
         ↓ 验证功能一致性（A/B 流量对比）
阶段 2：逐个将高频/关键函数迁移到新接口
         ↓ 每个函数迁移后去掉适配器
阶段 3：所有函数迁移完成，移除 V1FunctionAdapter
```

---

## 附录 C：设计决策对照

| 问题 | 旧方案 | 当前方案 | 理论依据 |
|------|---------|---------|---------|
| 节点间数据共享 | `WorkflowContext.put/get`（可变堆） | `ImmutableExecutionState.withNodeOutput`（不可变快照） | State Monad |
| 函数签名 | `apply(I input, WorkflowContext ctx)` | `apply(NodeInput input): FunctionResult<O>` | 调用栈帧隔离 |
| 节点依赖声明 | 运行时 `ctx.get("n1")` | 配置时 `dependsOn: ["n1"]` | Dataflow 显式依赖 |
| DAG 并行安全 | 需要 `volatile`/`ConcurrentHashMap` | `ImmutableExecutionState` 天然无锁 | 不可变对象 |
| 调试重放 | 需重跑前置节点 | 直接从状态快照恢复，任意节点精确重放 | 不可变状态历史 |
| 副作用管理 | 隐式（函数内部随时发生） | `FunctionResult.sideEffects` 显式声明 | Actor 消息模型 |
| Saga 补偿 | 无（旧版缺失） | `SagaExecutor` + `compensateFunctionRef` 精确逆序回滚 | 补偿事务模式 |
| 热加载一致性 | 需要额外快照机制 | `ImmutableExecutionState.start()` 创建时天然锁定版本 | 不可变对象 |
| 脚本安全 | SecureASTCustomizer（仅 AST 层） | 三级安全策略（AST/ClassLoader/子进程） | 纵深防御 |
| 函数优先级 | builtin > custom（错误） | custom > builtin > script > external | 可替换性 |
| 引用透明性 | 不满足（ctx 可写） | 满足（相同 NodeInput → 相同输出） | 函数式编程 |
| 多语言支持 | 仅 JVM | JVM 内置 + Function Gateway（gRPC/HTTP） | Polyglot 架构 |

---

## 附录 D：异常体系与错误码

> 代码实现见 `fluxion-core/src/main/kotlin/com/fluxion/core/exception/`，以代码为准。

### 附录 D.1 设计规范

- 基类：`WorkflowException extends RuntimeException`，携带 `errorCode` 字段
- errorCode 格式：`WF-{HTTP状态码}-{序号}`（如 `WF-400-001`）
- 全局异常处理器从 errorCode 自动提取 HTTP 状态码返回客户端

### 附录 D.2 错误码表

| 错误码 | 异常类 | HTTP | 含义 |
|--------|--------|------|------|
| WF-400-001 | InvalidParamException | 400 | 参数校验失败 |
| WF-400-002 | SchemaValidationException | 400 | JSON Schema 不匹配 |
| WF-400-003 | UnsupportedHttpMethodException | 400 | 不支持的 HTTP 方法 |
| WF-400-004 | UnsupportedTransformEngineException | 400 | 不支持的转换引擎 |
| WF-400-005 | InvalidSqlIdentifierException | 400 | 非法 SQL 标识符（防注入） |
| WF-400-006 | InvalidWorkflowDefinitionException | 400 | 工作流定义内容无效 |
| WF-403-001 | AccessDeniedException | 403 | 无权限 |
| WF-403-002 | ProtectedWorkflowModificationException | 403 | 尝试修改受保护元工作流 |
| WF-404-001 | FunctionNotFoundException | 404 | 函数引用不存在 |
| WF-404-002 | WorkflowNotFoundException | 404 | 工作流定义不存在 |
| WF-404-003 | DecoratorNotFoundException | 404 | 装饰器不存在 |
| WF-409-001 | WorkflowVersionMismatchException | 409 | 调试快照版本与定义不匹配 |
| WF-429-001 | RateLimitExceededException | 429 | 节点限流（令牌桶） |
| WF-429-002 | DebugRateLimitException | 429 | 调试接口 IP 限流 |
| WF-429-003 | DebugCapacityException | 429 | 全局调试并发数已满 |
| WF-500-001 | WorkflowNodeException | 500 | 节点执行失败（包装原始异常） |
| WF-500-002 | WorkflowExecutionException | 500 | 工作流级别执行失败 |
| WF-500-003 | DependencyNotReadyException | 500 | dependsOn 节点输出未就绪 |
| WF-500-004 | DuplicateNodeIdException | 500 | 节点 ID 重复 |
| WF-500-005 | CyclicDependencyException | 500 | DAG 存在环（拓扑排序失败） |
| WF-500-006 | SagaExecutionException | 500 | Saga 执行阶段失败（附补偿结果） |
| WF-500-007 | ExternalFunctionException | 500 | 外部函数调用失败 |
| WF-500-008 | ScriptSecurityException | 500 | 脚本安全违规 |
| WF-500-009 | MetaWorkflowBootstrapException | 500 | 元工作流自举失败 |
| WF-500-010 | NodeTimeoutException | 500 | 节点执行超时 |
| WF-500-011 | RetryExhaustedException | 500 | 重试次数耗尽 |
| WF-500-012 | FallbackFailedException | 500 | 降级函数也执行失败 |
| WF-500-013 | WorkflowTransactionException | 500 | 事务执行异常 |

### 附录 D.3 客户端处理建议

| HTTP 状态码 | 分类 | 客户端处理建议 |
|------------|------|----------------|
| 400 | 请求错误 | 检查入参格式和 Schema，修复后重试 |
| 403 | 权限不足 | 检查角色权限，联系管理员 |
| 404 | 资源不存在 | 检查 workflowId / functionRef / decoratorName |
| 409 | 版本冲突 | 重新发起调试会话 |
| 429 | 限流 | 降低调用频率，稍后重试（参考 Retry-After 头） |
| 500 | 服务端错误 | 查看执行日志排查，部分可安全重试 |

---

## 附录 E：配置项完整清单

所有配置项以 `workflow.` 为前缀，在 `application.properties` 或 `config.yaml` 中配置。

### 附录 E.1 引擎配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `workflow.engine.dev-mode` | boolean | `false` | 开发模式：开启出参 Schema 严格校验 |
| `workflow.engine.validate-node-output` | boolean | `false` | 是否对每个节点输出做 Schema 校验（性能影响较大，建议仅测试环境开启） |
| `workflow.engine.default-timeout-ms` | int | `30000` | 节点默认超时（ms），节点未配置 timeoutMs 时使用 |
| `workflow.engine.timeout-executor-core-size` | int | `10` | withTimeout 线程池核心线程数 |

### 附录 E.2 重试配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `workflow.retry.default-max-retries` | int | `3` | 节点未配置 retryCount 时的最大重试次数 |
| `workflow.retry.default-base-ms` | int | `100` | 指数退避基础间隔（ms），实际间隔 = base * 2^attempt |
| `workflow.retry.max-sleep-ms` | int | `30000` | 重试间隔上限（ms），防止无限阻塞 |

### 附录 E.3 热加载（配置同步）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `workflow.sync.poll-interval-sec` | int | `30` | L1 DB 轮询间隔（秒） |
| `workflow.sync.cache-max-size` | int | `1000` | 工作流定义缓存最大条目数（Caffeine） |
| `workflow.sync.cache-expire-sec` | int | `300` | 缓存过期时间（秒，0 = 不过期，依赖 DB 轮询刷新） |
| `workflow.bootstrap-mode` | boolean | `false` | 强制使用 classpath 元工作流（DB 不可用时的紧急启动） |

### 附录 E.4 脚本引擎

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `workflow.script.groovy.cache-max-size` | int | `500` | Groovy 编译脚本缓存上限 |
| `workflow.script.groovy.cache-expire-hours` | int | `1` | 缓存过期时间（小时） |
| `workflow.script.groovy.security-level` | int | `1` | 安全等级：1=AST，2=ClassLoader，3=子进程 |
| `workflow.script.js.allow-all-access` | boolean | `false` | GraalVM JS 是否允许访问 Java API（生产禁止开启） |

### 附录 E.5 调试（DebugSecurityGuard）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `workflow.debug.ip-rate-limit-per-sec` | double | `2.0` | 每个 IP 每秒最多调试请求数 |
| `workflow.debug.global-concurrency-limit` | int | `20` | 全局调试并发数上限（Semaphore permits） |
| `workflow.debug.ip-limiter-expire-min` | int | `5` | IP 限流器缓存过期时间（分钟） |
| `workflow.debug.enable` | boolean | `true` | 是否启用调试接口（生产环境建议设 false） |

### 附录 E.6 Function Gateway（多语言接入）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `workflow.gateway.grpc.port` | int | `9090` | Function Gateway gRPC 监听端口 |
| `workflow.gateway.http.port` | int | `9091` | Function Gateway HTTP 监听端口 |
| `workflow.gateway.connect-timeout-ms` | int | `5000` | 连接外部 Runtime 超时（ms） |
| `workflow.gateway.call-timeout-ms` | int | `30000` | 单次函数调用超时（ms） |

### 附录 E.7 可观测性

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `workflow.metrics.enabled` | boolean | `true` | 是否开启 Micrometer Metrics |
| `workflow.metrics.percentiles` | list | `0.5,0.95,0.99` | Histogram 百分位数 |
| `workflow.tracing.enabled` | boolean | `true` | 是否开启 OpenTelemetry Trace |
| `workflow.tracing.sampling-ratio` | double | `0.1` | 采样率（0.1 = 10%，生产环境建议 ≤ 0.1） |
| `workflow.logging.log-node-input` | boolean | `false` | 是否在日志中记录节点 NodeInput（含敏感数据时关闭） |

### 附录 E.8 示例配置（application.yml）

```yaml
workflow:
  engine:
    dev-mode: false
    validate-node-output: false
    default-timeout-ms: 30000

  retry:
    default-max-retries: 3
    default-base-ms: 100
    max-sleep-ms: 30000

  sync:
    poll-interval-sec: 30
    cache-max-size: 1000
    cache-expire-sec: 300

  script:
    groovy:
      cache-max-size: 500
      security-level: 1   # 生产建议升级到 2
    js:
      allow-all-access: false

  debug:
    enable: true           # 生产环境建议设 false
    ip-rate-limit-per-sec: 2.0
    global-concurrency-limit: 20

  gateway:
    grpc:
      port: 9090
    call-timeout-ms: 30000

  metrics:
    enabled: true
    percentiles: 0.5,0.95,0.99

  tracing:
    enabled: true
    sampling-ratio: 0.1

  logging:
    log-node-input: false  # 生产建议关闭，避免敏感数据入日志
```

---

## 7. 后续演进方向（Phase 2~5）

以下阶段按优先级排列，是对当前 Phase 1（函数多版本共存）的延伸，分别借鉴 Clojure、Erlang、Scala 的函数式思想。

### 7.1 Phase 2：持久化 ExecutionContext 与结构共享

**思想来源**：Clojure 持久化数据结构（Persistent Data Structures）

**当前问题**：
`ImmutableExecutionState.withNodeOutput()` 内部使用 `nodeOutputs.toMutableMap()` 进行全量拷贝。节点数较多或单节点输出较大时，每次快照都会产生 O(n) 的拷贝开销，不适合高频、长链路工作流。

**目标**：
1. 让 `ImmutableExecutionState` 的 `withNodeOutput` / `mergeNodeOutputs` 实现 **结构共享**（structural sharing），将不可变快照的增量成本降到接近 O(log n) 或 O(1)。
2. 持久化执行轨迹与分支快照，支持：
   - **精确重放**：给定初始输入和轨迹，可逐节点重放并比对输出。
   - **断点恢复**：从任意节点状态快照继续执行，而不必从头运行。
   - **审计与调试**：完整保留每次节点调用的输入、输出、副作用声明。

**关键设计点**：
- 引入持久化 Map 实现（如 Hash Array Mapped Trie，HAMT）替换 `kotlin.collections.HashMap`。
- 定义 `ExecutionTrace` 作为 `List<NodeExecutionRecord>` 的持久化等价物，支持追加共享。
- 在 `EngineResult` 中可选择性地保留完整 `finalState` 与 `trace`；生产环境可关闭以降低存储。

### 7.2 Phase 3：FunctionActor 与 Supervisor 树

**思想来源**：Erlang Actor 模型与 Supervision Tree

**当前问题**：
节点函数目前由引擎直接同步调用，错误处理策略（FAIL / SKIP / FALLBACK / RETRY）集中在 `WorkflowEngine` 内部。节点 failures 无法做到进程级隔离，也无法表达「某个子树失败时只重启该子树」的语义。

**目标**：
1. 把每次函数调用封装为 **FunctionActor**：带邮箱、生命周期、状态隔离。
2. 为每个工作流节点或子图配置 **Supervisor**，支持策略：
   - **Restart**：按指数退避重启当前节点（对应现有 RETRY）。
   - **Compensate**：触发 Saga 补偿链，回滚已执行节点的副作用。
   - **Terminate**：终止当前分支，让上游 Supervisor 决策。
3. 实现节点级故障隔离：一个节点异常不会污染其他节点或全局状态。

**关键设计点**：
- `FunctionActor` 持有指向 `FunctionVersion` 的引用，与 Phase 1 的版本模型打通。
- Supervisor 决策通过 `NodeStatus` + `ErrorStrategy` 组合表达，保持向后兼容。
- Actor 邮箱可用于后续扩展异步调用、批处理、背压（back-pressure）。

### 7.3 Phase 4：Pattern Matching DSL 与 ADT

**思想来源**：Scala 模式匹配（Pattern Matching）与代数数据类型（ADT）

**当前问题**：
条件分支 `ConditionalNext` 使用 SpEL / JsonPath 表达式，对复杂类型（如密封类型的多个变体）表达能力有限，且无法在编译期检查分支穷尽性。

**目标**：
1. 在 DSL 中引入 `match` / `when` 语法，支持对 `NodeInput.output`、`declaredDeps`、`workflowInput` 进行结构与类型匹配。
2. 结合 ADT，让函数输出可以是密封类型（sealed class / sealed interface），每个分支对应一个变体。
3. 替代部分 SpEL 条件表达式，使分支逻辑更声明式、更可静态分析。

**示例方向**：
```kotlin
// 节点输出为 ApprovalResult ADT 时
when (input.output<ApprovalResult>()) {
    is ApprovalResult.Approved -> "node:sendSuccess"
    is ApprovalResult.Rejected -> "node:sendRejection"
    is ApprovalResult.NeedManual -> "node:manualReview"
}
```

**关键设计点**：
- DSL 保持向后兼容：`condition` 字段仍可写 SpEL，新增 `match` 字段用于模式匹配。
- 引擎在加载定义时检查 `match` 分支是否穷尽（可选，开发模式开启）。

### 7.4 Phase 5：Data-as-Code 的 Workflow DSL

**思想来源**：Clojure 数据即代码（Data as Code）

**当前问题**：
工作流定义虽然是 JSON/YAML，但节点函数、条件、装饰器等仍需遵循引擎预定义的结构。更高阶的抽象（如「根据模板批量生成节点」）无法在定义层直接表达。

**目标**：
1. 让完整的 Workflow DSL 本身就是可存储、可传输、可程序化生成的纯数据结构。
2. 支持元工作流自举：用工作流定义来生成、校验、部署其他工作流定义。
3. 降低「配置即代码」的边界：业务方可以用数据操作（map/filter/merge）组合出复杂流程，而不仅是在 UI 画布上拖拽。

**关键设计点**：
- 统一使用 EDN / JSON / Kotlin DSL 表达节点、边、函数引用、条件、装饰器。
- 引入「宏节点」概念：一个节点在加载时被展开为多个普通节点，展开规则本身也是数据。
- 与 Phase 4 的 ADT / Pattern Matching 结合，使 DSL 具备类型安全的高级抽象能力。

---

## 附录 F：文档拆分说明

### 为什么建议拆分？

本文档现已超过 **4800 行**，覆盖架构设计、引擎实现、函数适配器、可观测性、安全、运维部署等多个领域，不同角色的读者很难快速定位所需内容。

### 推荐拆分方案（按读者角色）

| 文件名 | 对应章节 | 目标读者 | 预估行数 |
|--------|----------|---------|---------|
| `workflow-architecture.md` | §1、§2.1-2.5 | 架构师、高级研发 | ~400 行 |
| `workflow-core-types.md` | §2.6、附录D、附录E | 所有研发 | ~500 行 |
| `workflow-engine.md` | §3、§4 | 核心引擎研发 | ~700 行 |
| `workflow-functions.md` | §5、§6、§7、§8、§9 | 函数研发 | ~800 行 |
| `workflow-platform.md` | §10-§16 | 平台研发、前端 | ~900 行 |
| `workflow-operations.md` | §17、§18、§20、附录E | 运维、SRE | ~500 行 |
| `workflow-meta.md` | §19 | 平台研发 | ~300 行 |
| `workflow-polyglot.md` | §21 | 多语言接入研发 | ~200 行 |
| `workflow-migration.md` | §20、附录A、B、C | 迁移负责人 | ~400 行 |

### 拆分时机建议

- **当前阶段**（方案设计期）：保持单文件，便于整体审阅和 CR
- **进入实施阶段**后：按上表拆分，在每个子文档顶部添加指向主文档的链接
- 建议使用 `docs/fluxion/` 子目录存放拆分后的文件

---

## 附录 G：监控与审计低优先级补充方案

> **定位**：本附录记录与核心执行链路非强相关的监控、审计增强能力，作为后续迭代的候选需求池。当前阶段以文档化为主，不进入核心实现排期。

### G.1 与核心能力的边界

已在正文中落地的可观测能力：

| 能力 | 所在章节 | 状态 |
|------|---------|------|
| Metrics 清单（Counter/Timer） | §18.1 | 已设计 |
| 结构化日志规范 | §18.2 | 已设计 |
| OpenTelemetry 分布式追踪 | §18.3 | 已设计 |
| 执行审计日志 API | §22.6 | 已设计 |

本附录补充的是**增强型、运营型、合规型**能力，优先级低于核心执行、函数注册、发布部署等主线功能。

### G.2 监控大盘与告警（P2）

#### 大盘视图规划

1. **执行概览**：总执行量、成功率、P50/P95/P99 端到端耗时、当前运行中工作流数。
2. **节点热力图**：按 functionRef 聚合的节点失败率 / 平均耗时 Top N。
3. **配置同步健康度**：L1/L2/L3 配置同步成功率、延迟分布、各实例版本一致性。
4. **资源关联**：JVM 内存、线程池、DB 连接池与执行吞吐的关联曲线。

#### 告警规则建议

| 告警项 | 阈值示例 | 级别 |
|--------|---------|------|
| 工作流执行失败率 | > 5% 持续 5min | P1 |
| 节点 P99 耗时 | > 10s 持续 5min | P2 |
| 配置同步失败 | 任意实例连续 3 次失败 | P1 |
| Saga 补偿失败 | 发生即告警 | P0 |
| 函数编译耗时 | > 5s | P3 |

**实现方式**：基于 Micrometer + Prometheus Alertmanager / 云监控告警，管理后台提供告警规则 CRUD 与通知渠道配置（钉钉/企业微信/Webhook）。

### G.3 审计日志长期归档与合规（P2）

#### 审计范围扩展

在 `wf_execution_log` 已有的执行级审计基础上，补充：

1. **管理操作审计**：工作流 CRUD、发布/下线、函数注册、角色权限变更。
2. **数据变更审计**：工作流定义版本 diff、回滚操作、参数变更。
3. **安全审计**：登录/登出、JWT 异常、越权访问尝试。

#### 存储与 retention

| 阶段 | 存储 | 保留策略 |
|------|------|---------|
| 热数据（0-7 天） | MySQL / HBase | 全量保留，支持秒级查询 |
| 温数据（7-90 天） | 对象存储（S3/OSS）+ 压缩 JSON | 按天归档，支持按 executionId 检索 |
| 冷数据（>90 天） | 冷存 / 日志服务（如 Loki/SLS） | 按合规要求保留 1-3 年 |

#### 合规能力

- **不可篡改**：关键审计记录写入只读日志流或区块链存证（按需）。
- **导出报表**：支持按时间范围 / 操作人 / 资源类型导出 CSV/PDF 审计报告。
- **隐私合规**：敏感字段（如输入参数）支持脱敏后再入审计库。

### G.4 节点级性能剖析（P3）

针对高频或慢节点，提供可选的节点级 profiling：

1. **CPU 火焰图**：在 script/groovy 节点中集成采样 profiler。
2. **内存分配追踪**：对大数据量节点（如 dbQuery / jsonTransform）记录堆分配峰值。
3. **IO 耗时拆分**：HTTP / DB / Redis 节点的 connect / send / wait / receive 阶段耗时。

触发方式：
- 手动：管理后台对某个节点开启「采样模式」。
- 自动：节点耗时超过阈值时自动触发一次采样并关联到执行 trace。

### G.5 执行链路可视化增强（P3）

在已有的 trace 列表基础上，增强前端展示：

1. **时序瀑布图**：每个节点的起止时间、依赖等待耗时、并行执行区间。
2. **状态演进回放**：按时间轴播放执行过程，高亮当前激活节点。
3. **输入输出 diff**：对比重放前后同一节点的输入输出变化。
4. **错误根因链路**：对 FAILED 节点自动标红并聚合上游依赖状态。

### G.6 多维度成本/容量分析（P3）

为大规模部署提供成本视角：

1. **按工作流/节点统计**：执行次数 × 平均耗时 → 估算 CPU/内存成本。
2. **容量预测**：基于历史趋势预测未来 7/30 天资源需求。
3. **低效工作流识别**：高失败率、高重试率、长尾耗时的工作流清单。

### G.7 实施建议

- **Phase 1（当前）**：保持文档化，核心实现聚焦执行稳定性与功能完整性。
- **Phase 2（上线后 1-3 个月）**：接入 Prometheus/Grafana 大盘，补充基础告警；完善管理操作审计。
- **Phase 3（上线后 3-6 个月）**：引入审计归档、节点 profiling、可视化增强。
- **Phase 4（长期）**：成本/容量分析、AI 辅助根因定位（按需）。

> 上述方案可根据实际业务规模裁剪，避免过早引入复杂的可观测基础设施。

---

## H. Java 8 兼容与运行时架构演进方向

### H.1 当前架构（方案 B）

为在保持主技术栈（Java 21 + Spring Boot 3.2 + Kotlin 2.1）先进性的同时兼容 Java 8 业务系统，项目采用 **sidecar / 控制面-运行面分离** 架构：

- **控制面**：`fluxion-admin` 负责元数据管理、工作流定义发布、函数管理，通过配置中心向运行面推送定义与函数。
- **运行面**：`fluxion-runtime` 作为独立的 `worker` 角色应用部署，从配置中心拉取定义/函数，通过 HTTP / RPC / MQ 暴露执行能力。
- **模块拆分**：
  - `fluxion-runtime-core`：零 Spring 的运行时核心，包含 `RuntimeWorkflowRouter`、`ExecutionSnapshotStore` SPI。
  - `fluxion-runtime-spring-boot`：自动装配层，基于 `DefinitionConfigSubscriber` / `ConfigBackedDefinitionProvider` 加载定义，并初始化函数热更新。
  - `fluxion-runtime`：可独立启动的 Spring Boot 应用，默认端口 `8081`，供 Java 8 业务系统远程调用。

### H.2 演进方向（方案 C）

若未来需要让 Java 8 业务系统获得类型安全、更贴近原生 SDK 的接入体验，可进一步演进：

1. **抽取 `fluxion-api`（Java 8 兼容）**
   - 将远程调用契约 `UnifiedRequest`、`EngineResult` 以及执行相关的 DTO 从 `fluxion-core` / `fluxion-adapter-spi` 中迁移出来。
   - `fluxion-api` 使用 Java 8 目标编译，不依赖 Spring / Jakarta EE。
   - Java 8 客户端可直接依赖 `fluxion-api` 获得类型安全的请求/响应对象。

2. **提供 `fluxion-client-java8`**
   - 基于 `fluxion-api` 封装对 `fluxion-runtime` 的 HTTP 调用。
   - 屏蔽序列化、重试、链路追踪 header 注入等细节，降低老项目接入成本。

3. **协议 stub 下沉**
   - 若使用 gRPC / Dubbo，可将 `.proto` / 接口单独生成到 Java 8 模块，供老项目直接依赖。
   - 服务端仍保持 Java 21，仅共享通信契约层。

> 方案 C 属于长期演进，建议在 `fluxion-runtime` 稳定、执行契约固化后再实施，避免早期频繁改动契约导致客户端反复升级。

## I. 双模部署架构

Fluxion 天然支持两种部署模式，分别面向不同规模和诉求的使用场景。

### I.1 嵌入式模式（Embedded）

**适用场景**：团队拥有自有基础设施，希望将工作流能力内嵌到现有服务中。

**部署方式**：
1. 用户在自有 Spring Boot 项目中引入 Fluxion 模块依赖
2. 自行部署 `fluxion-admin` 后台
3. 函数运行在用户自己的服务进程内

**架构特点**：
- 零额外基础设施开销，复用用户已有的 DB / Redis / MQ
- 函数与业务代码同进程部署，调用延迟最低
- 用户自主掌控扩缩容策略和运维节奏
- 适合对数据隔离有严格要求的私有化部署场景

**接入成本**：引入 Maven 依赖 + 配置 application.yml + 部署 Admin UI

### I.2 云函数模式（Serverless-style）

**适用场景**：平台方统一部署大规模 Fluxion 集群，多租户共享，用户无需部署任何服务。

**部署方式**：
1. 平台运维方部署 Fluxion 大规模集群（Admin + Runtime 多实例）
2. 用户通过 Admin 后台创建「应用」（租户）
3. 用户在后台配置 DB / Redis 等数据源连接
4. 用户编排工作流、注册函数，函数在共享集群中执行

**对函数开发者的 Serverless 体验**：
- 开发者无需关心底层基础设施，只需注册函数和编排 DAG
- 函数热发布、多版本共存，开发者无感知部署
- DAG 自动调度，并发执行、失败隔离

**从「引擎」到「Serverless 平台」需要补齐的能力**：

| 能力 | 现状 | 差距 |
|------|------|------|
| 多租户隔离 | 缺失 | 需要租户/应用级数据与执行隔离 |
| 资源隔离与配额 | 缺失 | 函数执行需要沙箱（容器/进程级隔离） |
| 按需弹性 | 缺失 | 需对接 K8s / KEDA 或 Serverless 容器实现自动扩缩容 |
| 数据源代理 | 缺失 | 用户配置 DB/Redis，平台统一管理连接池与生命周期 |
| 计量计费 | 缺失 | 按调用次数/执行时长/资源消耗计费 |
| 安全沙箱 | 缺失 | SCRIPT 类型函数需在安全隔离环境中执行 |

**关键差距说明**：

1. **多租户隔离**：当前架构为单租户模型，云函数模式需在元数据（工作流定义、函数注册）和执行态（运行时上下文、状态存储）两个层面实现租户隔离。
2. **安全沙箱**：SCRIPT 函数（Groovy/JS）当前在宿主进程内执行，多租户场景下必须隔离到独立容器或受限沙箱中，防止代码互相影响。
3. **弹性伸缩**：嵌入式模式下用户自行扩缩容即可；云函数模式需实现事件驱动的自动弹性（无请求时缩到零、突发流量时快速扩容）。

**典型对标**：
- 嵌入式模式 → 类似 Temporal / Cadence 的自托管部署
- 云函数模式 → 类似 AWS Step Functions + Lambda / 阿里云函数计算

> 云函数模式属于长期愿景，建议在嵌入式模式充分稳定、多租户隔离方案明确后再推进。核心编排引擎已具备 Serverless 内核能力，主要差距在于平台化治理层。
