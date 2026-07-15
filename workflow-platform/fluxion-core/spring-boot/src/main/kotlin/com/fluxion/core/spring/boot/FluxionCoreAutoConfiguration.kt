package com.fluxion.core.spring.boot

import com.fluxion.cache.FluxionCacheFactory
import com.fluxion.decorator.decorator.DecoratorRegistry
import com.fluxion.decorator.decorator.WorkflowDecoratorRegistry
import com.fluxion.decorator.engine.TaskInterceptor
import com.fluxion.core.engine.CaffeineIdempotencyStore
import com.fluxion.core.engine.DagExecutor
import com.fluxion.core.engine.DeadLetterQueue
import com.fluxion.core.engine.ExecutionTracker
import com.fluxion.core.engine.IdempotencyCacheSettings
import com.fluxion.core.engine.IdempotencyStore
import com.fluxion.core.engine.WorkflowEngine
import com.fluxion.core.engine.WorkflowIdempotencyCacheSettings
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.outbound.OutboundTransport
import com.fluxion.outbound.OutboundTransportRegistry
import com.fluxion.core.log.ExecutionLogStore
import com.fluxion.core.metrics.WorkflowMetrics
import com.fluxion.core.retry.RetryScheduler
import com.fluxion.schema.json.SchemaValidator
import com.fluxion.core.signal.SignalBroker
import com.fluxion.core.value.ExecutionMeta
import com.fluxion.core.value.NodeExecutionRecord
import com.fluxion.config.core.IdempotencyConfig
import com.fluxion.config.core.IdempotencyConfigSubscriber
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
 * Spring Boot auto-configuration for the Fluxion engine core.
 *
 * Registers the Kotlin-flavoured runtime beans that `fluxion-admin` and
 * `fluxion-runtime` share: registry, engine, DAG executor, retry
 * scheduler, and sensible logging-only defaults for the observability
 * SPIs (ExecutionLogStore, WorkflowMetrics, DeadLetterQueue).
 *
 * All wiring uses `@ConditionalOnMissingBean` for the pluggable SPIs
 * so that the admin/runtime modules can override with real DB /
 * Micrometer / Redis implementations just by declaring their own
 * beans.
 */
@Configuration
@ImportRuntimeHints(FluxionNativeImageHints::class)
class FluxionCoreAutoConfiguration {

    /** In-memory function registry — builtins + user registrations. */
    @Bean
    fun functionRegistry(): FunctionRegistry = FunctionRegistry()

    /**
     * External-function transport registry (HTTP / gRPC / Dubbo
     * outbound adapters).  `useServiceLoader=false` because Spring
     * handles discovery via `@Bean` instead.
     */
    @Bean(destroyMethod = "shutdown")
    fun externalFunctionTransportRegistry(): OutboundTransportRegistry =
        OutboundTransportRegistry(useServiceLoader = false)

    /**
     * Bridges every Spring `OutboundTransport` bean into the
     * registry.  Also invoked by `BuiltinFunctionRegistrar` so that
     * the built-in HTTP client wires up cleanly.
     */
    @Bean
    fun externalFunctionTransportRegistrar(
        registry: OutboundTransportRegistry,
        transports: List<OutboundTransport>
    ): ExternalFunctionTransportRegistrar = ExternalFunctionTransportRegistrar(registry, transports)

    /** Per-node decorator registry — populated by `fluxion-decorator` modules. */
    @Bean
    fun decoratorRegistry(): DecoratorRegistry = DecoratorRegistry()

    /** Per-workflow decorator registry — transaction / lock / rate-limit wrappers. */
    @Bean
    fun workflowDecoratorRegistry(): WorkflowDecoratorRegistry = WorkflowDecoratorRegistry()

    /** In-memory signal broker — supports WAIT nodes and the Query API. */
    @Bean
    fun signalBroker(): SignalBroker = SignalBroker()

    /** Execution lifecycle tracker — feeds Signal/Query APIs with running-state snapshots. */
    @Bean
    fun executionTracker(signalBroker: SignalBroker): ExecutionTracker =
        ExecutionTracker(signalBroker)

    /**
     * JSON Schema validator; optionally delegates to a `SchemaManager`
     * bean if the admin/runtime module provides one (for Protobuf /
     * Avro / cross-reference resolution).
     */
    @Bean
    fun schemaValidator(
        @Autowired(required = false) schemaManager: com.fluxion.schema.api.SchemaManager?
    ): SchemaValidator = SchemaValidator(schemaManager)

    /** Delayed-retry scheduler (virtual-thread backed); destroyed on context close. */
    @Bean(destroyMethod = "shutdown")
    fun retryScheduler(
        @Autowired(required = false) taskInterceptor: TaskInterceptor?
    ): RetryScheduler = RetryScheduler(taskInterceptor = taskInterceptor ?: TaskInterceptor.NOOP)

    /** Top-level workflow engine facade — callers inject this to execute definitions. */
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
        functionRegistry = functionRegistry,
        schemaValidator = schemaValidator,
        decoratorRegistry = decoratorRegistry,
        metrics = metrics,
        retryScheduler = retryScheduler,
        devMode = devMode,
        validateNodeInput = validateNodeInput,
        validateNodeOutput = validateNodeOutput,
        taskInterceptor = taskInterceptor ?: TaskInterceptor.NOOP,
        deadLetterQueue = deadLetterQueue ?: DeadLetterQueue.NOOP,
        providerRegistry = providerRegistry
    )

    /**
     * DAG executor — drives node parallelism (Kotlin coroutines-style
     * fan-out via the workflow virtual-thread executor).  Wraps the
     * WorkflowEngine call path with DAG-level decorators.
     */
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

    /**
     * Dedicated virtual-thread-per-task executor for workflow nodes.
     *
     * Uses Java 21+ `Executors.newThreadPerTaskExecutor` +
     * `Thread.ofVirtual()` so that blocking (I/O, JDBC, HTTP calls)
     * inside a node does not starve sibling nodes in the same DAG.
     */
    @Bean
    fun workflowExecutor(): Executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("fluxion-workflow", 0).factory()
    )

    /**
     * Default ExecutionLogStore — logs failed nodes at WARN and
     * everything else at DEBUG via SLF4J.  Override with a DB-backed
     * bean (e.g. DbExecutionLogStore) for full lifecycle replay.
     */
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
     * Default WorkflowMetrics — no-op zero-allocation recorder.
     *
     * If `fluxion-decorator-impl` is on the classpath AND a
     * Micrometer `MeterRegistry` bean is present,
     * `DecoratorImplAutoConfiguration` registers
     * `MicrometerWorkflowMetrics` instead; `@ConditionalOnMissingBean`
     * here lets that bean take precedence.
     */
    @Bean
    @ConditionalOnMissingBean(WorkflowMetrics::class)
    fun workflowMetrics(): WorkflowMetrics = WorkflowMetrics.noOp()

    /**
     * Default DeadLetterQueue — error-only SLF4J logger.
     *
     * Override with `DbDeadLetterQueue` or `MqDeadLetterQueue` in the
     * admin/runtime module for durable retries.
     */
    @Bean
    @ConditionalOnMissingBean(DeadLetterQueue::class)
    fun deadLetterQueue(): DeadLetterQueue = object : DeadLetterQueue {
        private val log = LoggerFactory.getLogger("fluxion.dlq")

        override fun enqueue(entry: com.fluxion.core.engine.DeadLetterEntry) {
            log.error {
                "DLQ enqueued kind=${entry.failureKind} executionId=${entry.executionId} " +
                    "workflow=${entry.workflowId} node=${entry.nodeName} error=${entry.errorMessage}"
            }
        }
    }

    /**
     * Default Caffeine-backed idempotency store.
     *
     * `X-Idempotency-Key` requests hit `IdempotencyRouter` which
     * consults this bean before running the DAG.  Override with
     * `RedisIdempotencyStore` for multi-instance deployments.
     *
     * Configurable via `application.yaml`:
     * ```yaml
     * workflow:
     *   idempotency:
     *     enabled: true
     *     ttl-hours: 24
     *     max-size: 1000
     *     spec: ""          # extra Caffeine spec (recordStats, weakKeys, ...)
     *     workflows:        # per-workflow overrides, keyed by workflowId
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
        return CaffeineIdempotencyStore()
    }

    /**
     * Bridges an [IdempotencyConfigSubscriber] (Apollo / Nacos dynamic
     * config) to the [CaffeineIdempotencyStore] runtime — on start:
     *  1. Registers a watch that re-applies config on every change.
     *  2. Immediately applies the initial subscriber snapshot so the
     *     first request doesn't race config load.
     *
     * Implemented as an `InitializingBean` returning lambda so the
     * logic runs after every other bean is wired.
     */
    @Bean
    fun idempotencyConfigRefresher(
        @Autowired(required = false) subscriber: IdempotencyConfigSubscriber?,
        @Autowired(required = false) idempotencyStore: IdempotencyStore?
    ): InitializingBean = InitializingBean {
        val store = idempotencyStore as? CaffeineIdempotencyStore ?: return@InitializingBean
        if (subscriber == null) return@InitializingBean

        val log = LoggerFactory.getLogger("fluxion.idempotency.config")

        fun applyConfig(config: IdempotencyConfig) {
            val settings = IdempotencyCacheSettings(
                enabled = config.enabled,
                ttlHours = config.ttlHours,
                workflows = config.workflows.mapValues { (_, wf) ->
                    WorkflowIdempotencyCacheSettings(
                        enabled = wf.enabled,
                        ttlHours = wf.ttlHours
                    )
                }
            )
            store.apply(settings)
            log.info {
                "Idempotency config applied: enabled=${config.enabled} " +
                    "ttlHours=${config.ttlHours} workflows=${config.workflows.keys}"
            }
        }

        subscriber.watch { applyConfig(it) }
        applyConfig(subscriber.load())
    }
}
