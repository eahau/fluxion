package com.fluxion.decorator.config

import com.fluxion.core.lock.DistributedLockProvider
import com.fluxion.core.metrics.WorkflowMetrics
import com.fluxion.decorator.decorator.AsyncCallback
import com.fluxion.decorator.decorator.CacheStore
import com.fluxion.decorator.decorator.DecoratorRegistry
import com.fluxion.decorator.decorator.NodeDecorator
import com.fluxion.decorator.decorator.WorkflowDecorator
import com.fluxion.decorator.decorator.WorkflowDecoratorRegistry
import com.fluxion.decorator.engine.TaskInterceptor
import com.fluxion.decorator.lock.DistributedLockDecorator
import com.fluxion.decorator.ratelimit.LocalRateLimitStore
import com.fluxion.decorator.tracing.OtelContextTaskInterceptor
import com.fluxion.decorator.lock.WorkflowLockDecorator
import com.fluxion.decorator.metrics.MicrometerWorkflowMetrics
import com.fluxion.core.ratelimit.RateLimitStore
import com.fluxion.decorator.decorator.MetricsDecorator
import com.fluxion.decorator.decorator.RateLimitDecorator
import com.fluxion.decorator.decorator.CacheDecorator
import com.fluxion.decorator.decorator.AsyncDecorator
import com.fluxion.decorator.decorator.LoggingDecorator
import com.fluxion.decorator.ratelimit.WorkflowRateLimitDecorator
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Spring Boot auto-configuration that wires up all built-in decorator
 * implementations.
 *
 * Beans are registered on an opt-in basis: each decorator bean is only
 * created when its prerequisite dependency is present (e.g. `MetricsDecorator`
 * requires a Micrometer `MeterRegistry`), giving deployments a no-op default
 * for infrastructure they haven't opted into.
 */
@AutoConfiguration
class DecoratorImplAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry::class)
    @ConditionalOnMissingBean(WorkflowMetrics::class)
    fun workflowMetrics(meterRegistry: MeterRegistry): WorkflowMetrics =
        MicrometerWorkflowMetrics(meterRegistry)

    @Bean
    @ConditionalOnBean(MeterRegistry::class)
    fun metricsDecorator(meterRegistry: MeterRegistry) = MetricsDecorator(meterRegistry)

    @Bean
    @ConditionalOnMissingBean(RateLimitDecorator::class)
    fun rateLimitDecorator(store: RateLimitStore?) =
        RateLimitDecorator(store ?: LocalRateLimitStore())

    @Bean
    @ConditionalOnBean(CacheStore::class)
    fun cacheDecorator(cacheStore: CacheStore) = CacheDecorator(cacheStore)

    @Bean
    @ConditionalOnBean(AsyncCallback::class)
    fun asyncDecorator(
        asyncCallback: AsyncCallback,
        taskInterceptor: TaskInterceptor,
        @Autowired(required = false) taskExecutor: Executor?
    ) = AsyncDecorator(
        taskExecutor ?: Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("fluxion-async", 0).factory()
        ),
        asyncCallback, taskInterceptor
    )

    @Bean
    @ConditionalOnMissingBean(LoggingDecorator::class)
    fun loggingDecorator() = LoggingDecorator()

    @Bean
    @ConditionalOnBean(DistributedLockProvider::class)
    fun distributedLockDecorator(lockProvider: DistributedLockProvider) =
        DistributedLockDecorator(lockProvider)

    @Bean
    @ConditionalOnBean(DistributedLockProvider::class)
    fun workflowLockDecorator(lockProvider: DistributedLockProvider) =
        WorkflowLockDecorator(lockProvider)

    @Bean
    @ConditionalOnMissingBean(WorkflowRateLimitDecorator::class)
    fun workflowRateLimitDecorator(store: RateLimitStore?) =
        WorkflowRateLimitDecorator(store ?: LocalRateLimitStore())

    /** Default OTel-based context propagator; replaced by a NOOP if OTel is absent. */
    @Bean
    @ConditionalOnMissingBean(TaskInterceptor::class)
    fun otelContextTaskInterceptor(): TaskInterceptor = OtelContextTaskInterceptor()

    @Bean
    fun decoratorImplRegistrar(
        registry: DecoratorRegistry,
        decorators: List<NodeDecorator>
    ) = DecoratorImplRegistrar(registry, decorators)

    @Bean
    fun workflowDecoratorRegistrar(
        registry: WorkflowDecoratorRegistry,
        decorators: List<WorkflowDecorator>
    ) = WorkflowDecoratorRegistrarBean(registry, decorators)
}

/**
 * Startup registrar -- collects every [NodeDecorator] bean in the application
 * context and hands them to [DecoratorRegistry.registerAll].
 */
class DecoratorImplRegistrar(
    registry: DecoratorRegistry,
    decorators: List<NodeDecorator>
) {
    init {
        registry.registerAll(decorators)
        log.info { "Registered ${decorators.size} NodeDecorator implementations" }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DecoratorImplRegistrar::class.java)
    }
}

/**
 * Startup registrar -- collects every [WorkflowDecorator] bean and hands them
 * to [WorkflowDecoratorRegistry.registerAll].
 */
class WorkflowDecoratorRegistrarBean(
    registry: WorkflowDecoratorRegistry,
    decorators: List<WorkflowDecorator>
) {
    init {
        registry.registerAll(decorators)
        log.info { "Registered ${decorators.size} WorkflowDecorator implementations" }
    }

    companion object {
        private val log = LoggerFactory.getLogger(WorkflowDecoratorRegistrarBean::class.java)
    }
}
