package com.fluxion.functionmeta.spring.boot

import com.fluxion.adapter.spi.config.FunctionConfigSubscriber
import com.fluxion.adapter.spi.config.KeyedConfigSubscriber
import com.fluxion.functionmeta.DefaultFunctionMetaManager
import com.fluxion.functionmeta.api.FunctionMetaManager
import com.fluxion.functionmeta.api.FunctionMetaRegistry
import com.fluxion.functionmeta.registry.InMemoryFunctionMetaRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring Boot auto-configuration for fluxion-function:meta module.
 *
 * Provides default wiring for [FunctionMetaManager] backed by an
 * [InMemoryFunctionMetaRegistry], unless a custom [FunctionMetaRegistry] bean
 * is already present (e.g. `JdbcFunctionMetaRegistry` on Admin nodes).
 *
 * Contains the nested [FunctionMetaHotReloadConfiguration] which activates on
 * Worker nodes when a [FunctionConfigSubscriber] bean is on the classpath
 * (HTTP/Apollo/Nacos adapter present). It creates a [FunctionMetaConfigApplier]
 * that subscribes to config-center push events and materializes FunctionMeta
 * records into the in-memory registry for runtime lookup.
 *
 * Admin nodes: typically supply `JdbcFunctionMetaRegistry` directly, which
 * takes precedence via @ConditionalOnMissingBean.
 * Worker nodes: rely on the in-memory registry populated from config-center
 * events broadcast by the Admin's publisher (Nacos/Apollo config or HTTP
 * registry).
 *
 * @see com.fluxion.schema.spring.boot.FluxionSchemaAutoConfiguration for the
 *   sibling schema auto-config that this module typically pairs with.
 */
@Configuration
@ConditionalOnProperty(prefix = "fluxion.function-meta", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class FunctionMetaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun functionMetaRegistry(): InMemoryFunctionMetaRegistry = InMemoryFunctionMetaRegistry()

    @Bean
    @ConditionalOnMissingBean
    fun functionMetaManager(registry: FunctionMetaRegistry): FunctionMetaManager =
        DefaultFunctionMetaManager(registry)

    /**
     * Worker-side hot-reload configuration.
     *
     * Activates when the config-adapter SPI classes are on the classpath AND a
     * [KeyedConfigSubscriber] bean has been registered (Nacos, Apollo, or HTTP
     * registry adapter). Wires the [FunctionMetaConfigApplier] which performs
     * the initial bulk load and then watches for incremental push updates.
     */
    @Configuration
    @ConditionalOnClass(name = ["com.fluxion.adapter.spi.config.FunctionConfigSubscriber"])
    @ConditionalOnBean(KeyedConfigSubscriber::class)
    class FunctionMetaHotReloadConfiguration {

        private val log = LoggerFactory.getLogger(javaClass)

        @Bean
        fun functionMetaConfigApplier(
            subscriber: FunctionConfigSubscriber,
            registry: InMemoryFunctionMetaRegistry
        ): FunctionMetaConfigApplier {
            val applier = FunctionMetaConfigApplier(
                subscriber = subscriber,
                registry = registry
            )
            applier.init()
            log.info { "FunctionMetaConfigApplier initialized" }
            return applier
        }
    }
}
