package com.fluxion.config.http

import com.fluxion.config.core.DefinitionConfigPublisher
import com.fluxion.config.core.DefinitionConfigSubscriber
import com.fluxion.config.core.FunctionConfigPublisher
import com.fluxion.config.core.FunctionConfigSubscriber
import com.fluxion.config.core.SchemaConfigPublisher
import com.fluxion.config.core.SchemaConfigSubscriber
import com.fluxion.config.core.InstanceDiscovery
import com.fluxion.config.core.InstanceRegistry
import com.fluxion.registry.http.HttpAdminInstanceDiscovery
import com.fluxion.registry.http.HttpAdminInstanceRegistry
import com.fluxion.registry.http.HttpInstanceRegistry
import com.fluxion.registry.http.InMemoryInstanceStore
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment

/**
 * HTTP config center + service discovery auto-configuration.
 */
@AutoConfiguration
@ConditionalOnProperty(name = ["workflow.config.type"], havingValue = "http", matchIfMissing = true)
class HttpConfigAutoConfiguration {

    // ===== Admin-side Beans ======================================================

    @Configuration
    @ConditionalOnProperty(name = ["workflow.instance.role"], havingValue = "admin")
    class AdminConfiguration {

        @Bean
        fun inMemoryInstanceStore(env: Environment): InMemoryInstanceStore {
            val timeout = env.getProperty("workflow.config.http.heartbeat-timeout-ms", Long::class.java, 30_000L)!!
            return InMemoryInstanceStore(timeout)
        }

        @Bean
        fun instanceDiscovery(store: InMemoryInstanceStore): InstanceDiscovery =
            HttpAdminInstanceDiscovery(store)

        @Bean
        fun instanceRegistry(store: InMemoryInstanceStore): InstanceRegistry =
            HttpAdminInstanceRegistry(store)

        @Bean
        fun instanceRegistrationController(registry: InstanceRegistry): InstanceRegistrationController =
            InstanceRegistrationController(registry)

        @Bean
        fun httpDefinitionConfigPublisher(
            instanceDiscovery: InstanceDiscovery
        ): DefinitionConfigPublisher = HttpDefinitionConfigPublisher(instanceDiscovery)

        @Bean
        fun httpFunctionConfigPublisher(
            instanceDiscovery: InstanceDiscovery
        ): FunctionConfigPublisher = HttpFunctionConfigPublisher(instanceDiscovery)

        @Bean
        fun httpSchemaConfigPublisher(
            instanceDiscovery: InstanceDiscovery
        ): SchemaConfigPublisher = HttpSchemaConfigPublisher(instanceDiscovery)
    }

    // ===== Worker-side Beans =====================================================

    @Configuration
    @ConditionalOnProperty(name = ["workflow.instance.role"], havingValue = "worker")
    class WorkerConfiguration {

        @Bean
        fun httpInstanceRegistry(env: Environment): InstanceRegistry {
            val adminUrl = env.getRequiredProperty("workflow.config.http.admin-url")
            return HttpInstanceRegistry(adminUrl)
        }

        @Bean
        fun httpDefinitionConfigSubscriber(
            env: Environment
        ): HttpDefinitionConfigSubscriber {
            val adminUrl = env.getRequiredProperty("workflow.config.http.admin-url")
            return HttpDefinitionConfigSubscriber(adminUrl)
        }

        @Bean
        @Primary
        fun definitionConfigSubscriber(
            httpSubscriber: HttpDefinitionConfigSubscriber
        ): DefinitionConfigSubscriber = httpSubscriber

        @Bean
        fun definitionPushController(
            httpSubscriber: HttpDefinitionConfigSubscriber
        ): DefinitionPushController = DefinitionPushController(httpSubscriber)

        @Bean
        fun httpFunctionConfigSubscriber(
            env: Environment
        ): HttpFunctionConfigSubscriber {
            val adminUrl = env.getRequiredProperty("workflow.config.http.admin-url")
            return HttpFunctionConfigSubscriber(adminUrl)
        }

        @Bean
        @Primary
        fun functionConfigSubscriber(
            httpSubscriber: HttpFunctionConfigSubscriber
        ): FunctionConfigSubscriber = httpSubscriber

        @Bean
        fun functionPushController(
            httpSubscriber: HttpFunctionConfigSubscriber
        ): FunctionPushController = FunctionPushController(httpSubscriber)

        @Bean
        fun httpSchemaConfigSubscriber(
            env: Environment
        ): HttpSchemaConfigSubscriber {
            val adminUrl = env.getRequiredProperty("workflow.config.http.admin-url")
            return HttpSchemaConfigSubscriber(adminUrl)
        }

        @Bean
        @Primary
        fun schemaConfigSubscriber(
            httpSubscriber: HttpSchemaConfigSubscriber
        ): SchemaConfigSubscriber = httpSubscriber

        @Bean
        fun schemaPushController(
            httpSubscriber: HttpSchemaConfigSubscriber
        ): SchemaPushController = SchemaPushController(httpSubscriber)
    }
}
