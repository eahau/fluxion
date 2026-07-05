package com.fluxion.core.spring.boot

import com.fluxion.core.decorator.DecoratorRegistry
import com.fluxion.core.decorator.WorkflowDecoratorRegistry
import com.fluxion.core.engine.CaffeineIdempotencyStore
import com.fluxion.core.engine.DagExecutor
import com.fluxion.core.engine.DeadLetterQueue
import com.fluxion.core.engine.ExecutionTracker
import com.fluxion.core.engine.IdempotencyCacheSettings
import com.fluxion.core.engine.IdempotencyStore
import com.fluxion.core.engine.TaskInterceptor
import com.fluxion.core.engine.WorkflowEngine
import com.fluxion.core.engine.WorkflowIdempotencyCacheSettings
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.core.function.external.ExternalFunctionTransportRegistry
import com.fluxion.core.log.ExecutionLogStore
import com.fluxion.core.metrics.WorkflowMetrics
import com.fluxion.core.retry.RetryScheduler
import com.fluxion.core.schema.SchemaValidator
import com.fluxion.core.signal.SignalBroker
import com.fluxion.core.value.ExecutionMeta
import com.fluxion.core.value.NodeExecutionRecord
import com.fluxion.adapter.spi.config.IdempotencyConfig
import com.fluxion.adapter.spi.config.IdempotencyConfigSubscriber
import org.slf4j.*
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.core.env.Environment
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Fluxion 核心引擎的 Spring Boot 自动装配。
 *
 * fluxion-core 是零框架依赖的纯 Kotlin 模块，此配置类负责将核心组件装配到 Spring 容器。
 * 供 fluxion-admin（控制面）和 fluxion-runtime（运行面）共用。
 */
@Configuration
@ImportRuntimeHints(FluxionNativeImageHints::class)
class FluxionCoreAutoConfiguration {

    /** 函数注册表（内置函数、脚本函数、Redis 函数均注册到此） */
    @Bean
    fun functionRegistry(): FunctionRegistry = FunctionRegistry()

    /** 外部函数 Transport 注册表（HTTP/gRPC/Dubbo 等 outbound 实现注入） */
    @Bean(destroyMethod = "shutdown")
    fun externalFunctionTransportRegistry(): ExternalFunctionTransportRegistry =
        ExternalFunctionTransportRegistry(useServiceLoader = false)

    /**
     * 外部函数 Transport 注册器。
     * 收集所有 [ExternalFunctionTransport] Bean 并注册到 [ExternalFunctionTransportRegistry]，
     * 与 [com.fluxion.builtin.config.BuiltinFunctionRegistrar] 保持风格一致。
     */
    @Bean
    fun externalFunctionTransportRegistrar(
        registry: ExternalFunctionTransportRegistry,
        transports: List<ExternalFunctionTransport>
    ): ExternalFunctionTransportRegistrar = ExternalFunctionTransportRegistrar(registry, transports)

    /** 装饰器注册表（缓存、限流、链路追踪等节点级装饰器） */
    @Bean
    fun decoratorRegistry(): DecoratorRegistry = DecoratorRegistry()

    /** 工作流级装饰器注册表（事务、分布式锁、限流等） */
    @Bean
    fun workflowDecoratorRegistry(): WorkflowDecoratorRegistry = WorkflowDecoratorRegistry()

    /** 信号投递代理（Signal/Query 能力的核心） */
    @Bean
    fun signalBroker(): SignalBroker = SignalBroker()

    /** 运行态执行追踪器（供 Signal/Query API 查询） */
    @Bean
    fun executionTracker(signalBroker: SignalBroker): ExecutionTracker =
        ExecutionTracker(signalBroker)

    /** JSON Schema 校验器（委托 SchemaManager 支持多格式） */
    @Bean
    fun schemaValidator(
        @Autowired(required = false) schemaManager: com.fluxion.schema.api.SchemaManager?
    ): SchemaValidator = SchemaValidator(schemaManager)

    /** 非阻塞重试调度器（Netty 时间轮） */
    @Bean(destroyMethod = "shutdown")
    fun retryScheduler(
        @Autowired(required = false) taskInterceptor: TaskInterceptor?
    ): RetryScheduler = RetryScheduler(taskInterceptor = taskInterceptor ?: TaskInterceptor.NOOP)

    /** 工作流执行引擎 */
    @Bean
    fun workflowEngine(
        functionRegistry: FunctionRegistry,
        schemaValidator: SchemaValidator,
        decoratorRegistry: DecoratorRegistry,
        metrics: WorkflowMetrics,
        retryScheduler: RetryScheduler,
        @Value("\${workflow.dev-mode:false}") devMode: Boolean,
        @Value("\${workflow.validate-node-input:\${workflow.dev-mode:false}}") validateNodeInput: Boolean,
        @Value("\${workflow.validate-node-output:\${workflow.dev-mode:false}}") validateNodeOutput: Boolean,
        @Autowired(required = false) taskInterceptor: TaskInterceptor?,
        @Autowired(required = false) deadLetterQueue: DeadLetterQueue?,
        @Autowired(required = false) providerRegistry: com.fluxion.schema.api.SchemaDataProviderRegistry?
    ): WorkflowEngine = WorkflowEngine(
        functionRegistry, schemaValidator, decoratorRegistry,
        metrics, retryScheduler, devMode, validateNodeInput, validateNodeOutput,
        taskInterceptor = taskInterceptor ?: TaskInterceptor.NOOP,
        deadLetterQueue = deadLetterQueue ?: DeadLetterQueue.NOOP,
        providerRegistry = providerRegistry
    )

    /** DAG 执行器（Kotlin Coroutines，依赖 WorkflowEngine） */
    @Bean
    fun dagExecutor(
        workflowEngine: WorkflowEngine,
        executionTracker: ExecutionTracker,
        workflowDecoratorRegistry: WorkflowDecoratorRegistry,
        @Autowired(required = false) taskInterceptor: TaskInterceptor?,
        @Autowired(required = false) providerRegistry: com.fluxion.schema.api.SchemaDataProviderRegistry?
    ): DagExecutor = DagExecutor(
        workflowEngine,
        taskInterceptor = taskInterceptor ?: TaskInterceptor.NOOP,
        executionTracker = executionTracker,
        workflowDecoratorRegistry = workflowDecoratorRegistry,
        providerRegistry = providerRegistry
    )

    // ─── 虚拟线程池（Java 21+）─────────────────────────────────────────

    /**
     * 工作流执行线程池（虚拟线程）
     */
    @Bean
    fun workflowExecutor(): Executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("fluxion-workflow", 0).factory()
    )

    // ─── 其他 ─────────────────────────────────────────────────────────

    /** 默认日志存储（输出到 SLF4J 结构化日志，可替换为 DB 实现） */
    @Bean
    @ConditionalOnMissingBean(ExecutionLogStore::class)
    fun executionLogStore(): ExecutionLogStore = object : ExecutionLogStore {
        private val log = LoggerFactory.getLogger("WorkflowExecutionLog")

        override fun enabled(): Boolean = true

        override fun save(record: NodeExecutionRecord, meta: ExecutionMeta) {
            if (record.status.name == "FAILED") {
                log.warn { "Node [${record.nodeId}] FAILED err=[${record.errorMessage}]" }
            } else {
                log.debug { "Node [${record.nodeId}] ${record.status}" }
            }
        }
    }

    /**
     * WorkflowMetrics 默认 No-Op 实现（零依赖）
     * 若 workflow-decorator-impl 在 classpath 且有 MeterRegistry Bean，
     * DecoratorImplAutoConfiguration 会自动提供 MicrometerWorkflowMetrics
     * 并通过 @ConditionalOnMissingBean 覆盖此 Bean。
     */
    @Bean
    @ConditionalOnMissingBean(WorkflowMetrics::class)
    fun workflowMetrics(): WorkflowMetrics = WorkflowMetrics.noOp()

    /**
     * 默认死信队列：仅记录结构化日志，不引入外部依赖。
     * 生产环境应替换为 DbDeadLetterQueue 或 MqDeadLetterQueue。
     */
    @Bean
    @ConditionalOnMissingBean(DeadLetterQueue::class)
    fun deadLetterQueue(): DeadLetterQueue = object : DeadLetterQueue {
        private val log = LoggerFactory.getLogger("fluxion.dlq")

        override fun enqueue(entry: com.fluxion.core.engine.DeadLetterEntry) {
            log.error(
                "DLQ enqueued kind=${entry.failureKind} executionId=${entry.executionId} " +
                    "workflow=${entry.workflowId} node=${entry.nodeName} error=${entry.errorMessage}"
            )
        }
    }

    /**
     * 默认幂等存储：Caffeine 内存实现，支持运行期动态刷新。
     *
     * IdempotencyRouter 仅在请求携带 X-Idempotency-Key 头时生效，无副作用。
     * 分布式环境应替换为 RedisIdempotencyStore。
     *
     * 初始值从 application.yaml 读取；若存在 [IdempotencyConfigSubscriber]（Apollo/Nacos），
     * 则由其推送配置变更事件，调用 [CaffeineIdempotencyStore.apply] 热更新，无需轮询。
     *
     * 支持应用级默认 + 工作流级覆盖的二维策略：
     * ```yaml
     * workflow:
     *   idempotency:
     *     enabled: true       # 设为 false 则退回 NOOP（不缓存）
     *     ttl-hours: 24       # 缓存条目 TTL（小时），运行期即时生效
     *     max-size: 1000      # 最大缓存条目数，变更时热替换缓存实例
     *     spec: ""            # 可选 Caffeine 高级表达式（如 recordStats,weakKeys）
     *     workflows:          # 按工作流 ID 覆盖，未指定字段继承应用默认
     *       order-service:
     *         ttl-hours: 48
     *         max-size: 5000
     * ```
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyStore::class)
    fun idempotencyStore(env: Environment): IdempotencyStore {
        val enabled = env.getProperty("workflow.idempotency.enabled", Boolean::class.java, true)
        if (!enabled) return IdempotencyStore.NOOP

        val ttlHours = env.getProperty("workflow.idempotency.ttl-hours", Long::class.java, 24L)
        val maxSize  = env.getProperty("workflow.idempotency.max-size",  Long::class.java, 1000L)
        val spec     = env.getProperty("workflow.idempotency.spec", String::class.java)

        return CaffeineIdempotencyStore(
            ttl     = Duration.ofHours(ttlHours),
            maxSize = maxSize,
            spec    = spec
        )
    }

    /**
     * 幂等配置动态刷新器 — 基于配置中心推送事件驱动。
     *
     * 当 classpath 存在 [IdempotencyConfigSubscriber]（Apollo/Nacos 实现）时：
     * 1. 注册监听器（watch），配置变更时热更新
     * 2. 加载初始配置（load），作为首次事件走同一条应用管线
     *
     * 初始加载和后续推送共用 [applyConfig]，消除重复。
     */
    @Bean
    fun idempotencyConfigRefresher(
        @Autowired(required = false) subscriber: IdempotencyConfigSubscriber?,
        @Autowired(required = false) idempotencyStore: IdempotencyStore?
    ): InitializingBean = InitializingBean {
        val store = idempotencyStore as? CaffeineIdempotencyStore ?: return@InitializingBean
        if (subscriber == null) return@InitializingBean

        val log = LoggerFactory.getLogger("fluxion.idempotency.config")

        // 统一的配置应用管线 — 初始加载和后续推送共用
        fun applyConfig(config: IdempotencyConfig) {
            val settings = IdempotencyCacheSettings(
                enabled = config.enabled,
                ttlHours = config.ttlHours,
                maxSize = config.maxSize,
                spec = config.spec,
                workflows = config.workflows.mapValues { (_, wf) ->
                    WorkflowIdempotencyCacheSettings(
                        enabled = wf.enabled,
                        ttlHours = wf.ttlHours,
                        maxSize = wf.maxSize,
                        spec = wf.spec
                    )
                }
            )
            store.apply(settings)
            log.info(
                "Idempotency config applied: enabled=${config.enabled} " +
                    "ttlHours=${config.ttlHours} maxSize=${config.maxSize} spec=${config.spec} " +
                    "workflows=${config.workflows.keys}"
            )
        }

        // 先注册监听器，后续推送走回调管线
        subscriber.watch { applyConfig(it) }

        // 初始加载作为首次事件，走同一条 applyConfig 管线
        applyConfig(subscriber.load())
    }
}
