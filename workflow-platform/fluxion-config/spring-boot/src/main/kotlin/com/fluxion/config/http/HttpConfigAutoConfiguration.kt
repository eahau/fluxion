package com.fluxion.config.http

import com.fluxion.config.core.DefinitionConfigPublisher
import com.fluxion.config.core.DefinitionConfigSubscriber
import com.fluxion.config.core.FunctionConfigPublisher
import com.fluxion.config.core.FunctionConfigSubscriber
import com.fluxion.discovery.core.InstanceDiscovery
import com.fluxion.registry.core.InstanceRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment

@AutoConfiguration
@ConditionalOnProperty(name = ["workflow.config.type"], havingValue = "http", matchIfMissing = true)
class HttpConfigAutoConfiguration {

    @Configuration
    @ConditionalOnProperty(name = ["workflow.instance.role"], havingValue = "admin")
    class AdminConfiguration {

        @Bean
        fun httpDefinitionConfigPublisher(
            instanceDiscovery: InstanceDiscovery
        ): DefinitionConfigPublisher = HttpDefinitionConfigPublisher(instanceDiscovery)

        @Bean
        fun httpFunctionConfigPublisher(
            instanceDiscovery: InstanceDiscovery
        ): FunctionConfigPublisher = HttpFunctionConfigPublisher(instanceDiscovery)
    }

    @Configuration
    @ConditionalOnProperty(name = ["workflow.instance.role"], havingValue = "worker")
    class WorkerConfiguration {

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
        fun httpInstanceRegistry(env: Environment): HttpInstanceRegistry {
            val adminUrl = env.getRequiredProperty("workflow.config.http.admin-url")
            return HttpInstanceRegistry(adminUrl)
        }

        @Bean
        @Primary
        fun instanceRegistry(httpRegistry: HttpInstanceRegistry): InstanceRegistry = httpRegistry
    }
}