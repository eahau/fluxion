package com.fluxion.decorator.config

import com.fluxion.core.decorator.AsyncCallback
import com.fluxion.core.decorator.CacheStore
import com.fluxion.core.decorator.DecoratorRegistry
import com.fluxion.core.decorator.NodeDecorator
import com.fluxion.core.engine.TaskInterceptor
import com.fluxion.core.decorator.WorkflowDecorator
import com.fluxion.core.decorator.WorkflowDecoratorRegistry
import com.fluxion.core.lock.DistributedLockProvider
import com.fluxion.core.lock.WorkflowLockDecorator
import com.fluxion.core.metrics.WorkflowMetrics
import com.fluxion.core.ratelimit.RateLimitStore
import com.fluxion.core.ratelimit.WorkflowRateLimitDecorator
import com.fluxion.decorator.impl.*
import com.fluxion.decorator.impl.lock.DistributedLockDecorator
import com.fluxion.decorator.impl.ratelimit.LocalRateLimitStore
import com.fluxion.decorator.impl.tracing.OtelContextTaskInterceptor
import com.fluxion.decorator.metrics.MicrometerWorkflowMetrics
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
 * 装饰器实现 Spring Boot 自动配置
 */
@AutoConfiguration
class DecoratorImplAutoConfiguration {

    @Bean @ConditionalOnBean(MeterRegistry::class) @ConditionalOnMissingBean(WorkflowMetrics::class)
    fun workflowMetrics(meterRegistry: MeterRegistry): WorkflowMetrics = MicrometerWorkflowMetrics(meterRegistry)

    @Bean @ConditionalOnBean(MeterRegistry::class)
    fun metricsDecorator(meterRegistry: MeterRegistry) = MetricsDecorator(meterRegistry)

    @Bean @ConditionalOnMissingBean(RateLimitDecorator::class)
    fun rateLimitDecorator(store: RateLimitStore?) = RateLimitDecorator(store ?: LocalRateLimitStore())

    @Bean @ConditionalOnBean(CacheStore::class)
    fun cacheDecorator(cacheStore: CacheStore) = CacheDecorator(cacheStore)

    @Bean @ConditionalOnBean(AsyncCallback::class)
    fun asyncDecorator(
        asyncCallback: AsyncCallback,
        taskInterceptor: TaskInterceptor,
        @Autowired(required = false) taskExecutor: Executor?
    ) = AsyncDecorator(
        taskExecutor ?: Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("fluxion-async", 0).factory()),
        asyncCallback, taskInterceptor
    )

    @Bean @ConditionalOnMissingBean(LoggingDecorator::class)
    fun loggingDecorator() = LoggingDecorator()

    @Bean @ConditionalOnBean(DistributedLockProvider::class)
    fun distributedLockDecorator(lockProvider: DistributedLockProvider) = DistributedLockDecorator(lockProvider)

    @Bean @ConditionalOnBean(DistributedLockProvider::class)
    fun workflowLockDecorator(lockProvider: DistributedLockProvider) = WorkflowLockDecorator(lockProvider)

    @Bean @ConditionalOnMissingBean(WorkflowRateLimitDecorator::class)
    fun workflowRateLimitDecorator(store: RateLimitStore?) = WorkflowRateLimitDecorator(store ?: LocalRateLimitStore())

    /** OTel Context 跨线程传播拦截器（DagExecutor async(Dispatchers.IO) 场景） */
    @Bean @ConditionalOnMissingBean(TaskInterceptor::class)
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
 * 装饰器注册器 — 自动收集所有 [NodeDecorator] Bean 并注册
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
 * 工作流级装饰器注册器 — 自动收集所有 [WorkflowDecorator] Bean 并注册
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
