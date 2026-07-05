package com.fluxion.runtime.spring.boot

import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.adapter.spi.config.DefinitionConfigSubscriber
import com.fluxion.adapter.spi.registry.InstanceRegistry
import com.fluxion.core.engine.DagExecutor
import com.fluxion.core.engine.IdempotencyStore
import com.fluxion.core.lock.DistributedLockProvider
import com.fluxion.runtime.core.provider.ConfigBackedDefinitionProvider
import com.fluxion.runtime.core.provider.DefinitionProvider
import com.fluxion.runtime.core.router.DistributedLockRouter
import com.fluxion.runtime.core.router.IdempotencyRouter
import com.fluxion.runtime.core.router.RuntimeWorkflowRouter
import com.fluxion.runtime.core.spi.ExecutionSnapshotStore
import com.fluxion.runtime.spring.boot.registry.RuntimeInstanceRegistrar
import com.fluxion.runtime.spring.boot.store.LoggingExecutionSnapshotStore
import org.slf4j.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

/**
 * Fluxion Runtime Spring Boot 自动装配。
 *
 * 当应用角色为 worker 时生效，提供：
 * - 基于配置中心的 [DefinitionProvider]
 * - 面向执行引擎的 [WorkflowRouter] 实现
 * - 默认日志型 [ExecutionSnapshotStore]
 *
 * 必须在配置中心 AutoConfiguration 之后加载，
 * 否则 @ConditionalOnBean(DefinitionConfigSubscriber) 会因目标 Bean 尚未注册而评估为 false。
 */
@AutoConfiguration(afterName = [
    "com.fluxion.config.http.HttpConfigAutoConfiguration",
    "com.fluxion.config.nacos.NacosConfigAutoConfiguration",
    "com.fluxion.config.apollo.ApolloConfigAutoConfiguration",
])
@ConditionalOnClass(DagExecutor::class)
@ConditionalOnProperty(name = ["workflow.instance.role"], havingValue = "worker")
class FluxionRuntimeAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 基于配置中心的工作流定义提供者。
     *
     * 优先使用用户自定义的 [DefinitionProvider]；
     * 若不存在且存在 [DefinitionConfigSubscriber]，则使用配置中心缓存实现。
     */
    @Bean
    @ConditionalOnMissingBean(DefinitionProvider::class)
    @ConditionalOnBean(DefinitionConfigSubscriber::class)
    fun configCenterDefinitionProvider(
        subscriber: DefinitionConfigSubscriber
    ): DefinitionProvider {
        val provider = ConfigBackedDefinitionProvider(subscriber)
        provider.init()
        log.info { "ConfigCenterDefinitionProvider initialized with ${provider.size()} definitions" }
        return provider
    }

    /**
     * Runtime 侧工作流路由器，供 HTTP / RPC / MQ 适配器统一注入。
     *
     * 装饰顺序（由内到外）：RuntimeWorkflowRouter → IdempotencyRouter → DistributedLockRouter。
     * - 幂等在内层：命中缓存时直接返回，避免无意义加锁。
     * - 分布式锁在外层：为未命中缓存的并发请求提供严格互斥。
     */
    @Bean
    @ConditionalOnMissingBean(WorkflowRouter::class)
    fun runtimeWorkflowRouter(
        definitionProvider: DefinitionProvider,
        dagExecutor: DagExecutor,
        snapshotStore: ExecutionSnapshotStore?,
        @Autowired(required = false) idempotencyStore: IdempotencyStore?,
        @Autowired(required = false) lockProvider: DistributedLockProvider?
    ): WorkflowRouter {
        var router: WorkflowRouter = RuntimeWorkflowRouter(definitionProvider, dagExecutor, snapshotStore)

        if (idempotencyStore != null && idempotencyStore != IdempotencyStore.NOOP) {
            log.info { "IdempotencyRouter enabled, wrapping RuntimeWorkflowRouter" }
            router = IdempotencyRouter(router, idempotencyStore)
        }

        if (lockProvider != null && lockProvider != DistributedLockProvider.NOOP) {
            log.info { "DistributedLockRouter enabled, wrapping workflow router" }
            router = DistributedLockRouter(router, lockProvider)
        }

        return router
    }

    /**
     * 默认快照存储：仅记录日志，不引入 DB 依赖。
     */
    @Bean
    @ConditionalOnMissingBean(ExecutionSnapshotStore::class)
    fun loggingExecutionSnapshotStore(): ExecutionSnapshotStore =
        LoggingExecutionSnapshotStore()

    /**
     * Runtime 实例注册器：启动时向 Admin 注册并开启心跳，关闭时注销。
     */
    @Bean
    @ConditionalOnBean(InstanceRegistry::class)
    fun runtimeInstanceRegistrar(
        registry: InstanceRegistry,
        env: Environment
    ): RuntimeInstanceRegistrar = RuntimeInstanceRegistrar(registry, env)
}
