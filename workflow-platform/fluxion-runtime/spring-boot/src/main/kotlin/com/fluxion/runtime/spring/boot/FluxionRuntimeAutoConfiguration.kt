package com.fluxion.runtime.spring.boot

import com.fluxion.inbound.spi.InboundRouter
import com.fluxion.config.core.DefinitionConfigSubscriber
import com.fluxion.config.core.InstanceRegistry
import com.fluxion.core.engine.DagExecutor
import com.fluxion.core.engine.IdempotencyStore
import com.fluxion.core.lock.DistributedLockProvider
import com.fluxion.runtime.core.provider.ConfigBackedDefinitionProvider
import com.fluxion.runtime.core.provider.DefinitionProvider
import com.fluxion.runtime.core.router.DistributedLockRouter
import com.fluxion.runtime.core.router.IdempotencyRouter
import com.fluxion.runtime.core.router.RuntimeInboundRouter
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
 * Spring Boot auto-configuration for the Fluxion Worker (data plane).
 *
 * Activated ONLY when:
 * - The core `DagExecutor` class is on the classpath (i.e. `fluxion-core` pulled in).
 * - `workflow.instance.role=worker` is set in the application environment.
 *
 * Bean ordering via `afterName` is deliberate: the three config-center
 * auto-configurations (HTTP polling, Nacos, Apollo) MUST register their
 * `DefinitionConfigSubscriber` beans first, otherwise the
 * `@ConditionalOnBean(DefinitionConfigSubscriber)` guard here evaluates to
 * false and the Worker has no runtime definitions.
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
     * Build the Worker-side [DefinitionProvider] backed by the live config
     * center subscriber.
     *
     * Falls back to user-supplied beans via `@ConditionalOnMissingBean`. If no
     * custom provider exists but a subscriber is present, we instantiate the
     * config-center-backed version and eagerly call `init()` so the cache is
     * hot before the first inbound request hits a transport adapter.
     *
     * @param subscriber Config-center watcher (Nacos / Apollo / Admin-HTTP)
     * @return Initialized definition provider ready for lookups
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
     * Assemble the canonical [InboundRouter] bean consumed by every
     * transport adapter (HTTP, Dubbo, gRPC, Kafka).
     *
     * Decorator stacking order is INNER → OUTER:
     * 1. `RuntimeInboundRouter` (DAG engine — innermost, always present).
     * 2. `IdempotencyRouter` (if store is wired, result cache BEFORE locking).
     * 3. `DistributedLockRouter` (if provider wired, cluster serialization — outermost).
     *
     * Idempotency goes INSIDE the lock so that cache hits don't waste a
     * cluster lock; the lock protects only cache-miss executions.
     */
    @Bean
    @ConditionalOnMissingBean(InboundRouter::class)
    fun runtimeWorkflowRouter(
        definitionProvider: DefinitionProvider,
        dagExecutor: DagExecutor,
        snapshotStore: ExecutionSnapshotStore?,
        @Autowired(required = false) idempotencyStore: IdempotencyStore?,
        @Autowired(required = false) lockProvider: DistributedLockProvider?
    ): InboundRouter {
        var router: InboundRouter = RuntimeInboundRouter(definitionProvider, dagExecutor, snapshotStore)

        if (idempotencyStore != null && idempotencyStore != IdempotencyStore.NOOP) {
            log.info { "IdempotencyRouter enabled, wrapping RuntimeInboundRouter" }
            router = IdempotencyRouter(router, idempotencyStore)
        }

        if (lockProvider != null && lockProvider != DistributedLockProvider.NOOP) {
            log.info { "DistributedLockRouter enabled, wrapping workflow router" }
            router = DistributedLockRouter(router, lockProvider)
        }

        return router
    }

    /**
     * Default snapshot store implementation — pure structured logging.
     *
     * Intentionally dependency-free so a bare Worker deployment still emits
     * useful audit logs without a DB. Replace via `@ConditionalOnMissingBean`
     * override if durable snapshots are required.
     */
    @Bean
    @ConditionalOnMissingBean(ExecutionSnapshotStore::class)
    fun loggingExecutionSnapshotStore(): ExecutionSnapshotStore =
        LoggingExecutionSnapshotStore()

    /**
     * Wire the Worker instance lifecycle registrar if an `InstanceRegistry`
     * (Admin-side discovery bridge) is present on the classpath.
     *
     * Triggers on `ApplicationRunner#run` for initial register/heartbeat
     * start, and on `DisposableBean#destroy` for graceful deregister.
     */
    @Bean
    @ConditionalOnBean(InstanceRegistry::class)
    fun runtimeInstanceRegistrar(
        registry: InstanceRegistry,
        env: Environment
    ): RuntimeInstanceRegistrar = RuntimeInstanceRegistrar(registry, env)
}
