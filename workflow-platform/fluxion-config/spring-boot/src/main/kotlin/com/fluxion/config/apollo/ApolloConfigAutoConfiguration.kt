package com.fluxion.config.apollo

import com.ctrip.framework.apollo.openapi.client.ApolloOpenApiClient
import com.fluxion.config.core.DefinitionConfigPublisher
import com.fluxion.config.core.DefinitionConfigSubscriber
import com.fluxion.config.core.FunctionConfigPublisher
import com.fluxion.config.core.FunctionConfigSubscriber
import com.fluxion.config.core.IdempotencyConfig
import com.fluxion.config.core.IdempotencyConfigSubscriber
import com.fluxion.config.core.SchemaConfigPublisher
import com.fluxion.config.core.SchemaConfigSnapshot
import com.fluxion.config.core.SchemaConfigSubscriber
import com.fluxion.config.core.WorkflowDefinitionSnapshot
import com.fluxion.config.core.InstanceDiscovery
import com.fluxion.config.core.InstanceRegistry
import com.fluxion.core.util.JsonUtil
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

/**
 * Apollo config center + service discovery auto-configuration.
 *
 * ## Builder-style bindings (similar to NacosConfigs.java)
 *
 * All Subscriber/Publisher implementations use base classes directly; no need to define separate subclasses for each config type.
 * Add keyed config: one line `ApolloKeyedConfigSubscriber(mapper = { ... }, removedMapper = { ... })`
 * Add single-key config: one line `ApolloConfigSubscriber(parser = { cfg -> ... })`
 */
@AutoConfiguration
@ConditionalOnClass(name = ["com.ctrip.framework.apollo.ConfigService"])
@ConditionalOnProperty(name = ["workflow.config.type"], havingValue = "apollo")
class ApolloConfigAutoConfiguration {

    @Bean
    fun apolloOpenApiClient(env: Environment): ApolloOpenApiClient {
        val portalUrl = env.getRequiredProperty("workflow.config.apollo.portal-url")
        val token = env.getRequiredProperty("workflow.config.apollo.token")
        return ApolloOpenApiClient.newBuilder()
            .withPortalUrl(portalUrl)
            .withToken(token)
            .build()
    }

    @Bean
    fun apolloDefinitionConfigPublisher(
        client: ApolloOpenApiClient,
        env: Environment
    ): DefinitionConfigPublisher {
        val appId = env.getRequiredProperty("workflow.config.apollo.app-id")
        val envName = env.getProperty("workflow.config.apollo.env", "DEV")!!
        val cluster = env.getProperty("workflow.config.apollo.cluster", "default")!!
        val operator = env.getProperty("workflow.config.apollo.operator", "workflow-admin")!!
        return ApolloDefinitionConfigPublisher(client, appId, envName, cluster, operator)
    }

    @Bean
    fun apolloDefinitionConfigSubscriber(): DefinitionConfigSubscriber =
        ApolloKeyedConfigSubscriber(
            namespace = "workflow-definitions",
            mapper = { key, content -> WorkflowDefinitionSnapshot.of(key, content, 0, true) },
            removedMapper = { key -> WorkflowDefinitionSnapshot.removed(key) }
        )

    @Bean
    fun apolloFunctionConfigPublisher(
        client: ApolloOpenApiClient,
        env: Environment
    ): FunctionConfigPublisher {
        val appId = env.getRequiredProperty("workflow.config.apollo.app-id")
        val envName = env.getProperty("workflow.config.apollo.env", "DEV")!!
        val cluster = env.getProperty("workflow.config.apollo.cluster", "default")!!
        val operator = env.getProperty("workflow.config.apollo.operator", "workflow-admin")!!
        return ApolloFunctionConfigPublisher(client, appId, envName, cluster, operator)
    }

    @Bean
    fun apolloFunctionConfigSubscriber(): FunctionConfigSubscriber =
        ApolloFunctionConfigSubscriber()

    @Bean
    fun apolloSchemaConfigPublisher(
        client: ApolloOpenApiClient,
        env: Environment
    ): SchemaConfigPublisher {
        val appId = env.getRequiredProperty("workflow.config.apollo.app-id")
        val envName = env.getProperty("workflow.config.apollo.env", "DEV")!!
        val cluster = env.getProperty("workflow.config.apollo.cluster", "default")!!
        val operator = env.getProperty("workflow.config.apollo.operator", "workflow-admin")!!
        return ApolloSchemaConfigPublisher(client, appId, envName, cluster, operator)
    }

    @Bean
    fun apolloSchemaConfigSubscriber(): SchemaConfigSubscriber =
        ApolloKeyedConfigSubscriber(
            namespace = "workflow-schemas",
            mapper = { _, content -> JsonUtil.deserialize(content, SchemaConfigSnapshot::class.java) },
            removedMapper = { key -> SchemaConfigSnapshot.removed(key) }
        )

    @Bean
    fun apolloIdempotencyConfigSubscriber(): IdempotencyConfigSubscriber =
        ApolloConfigSubscriber(
            namespace = "workflow-idempotency",
            parser = { cfg ->
                // Prefer JSON content first to support workflow-level overrides; fallback to legacy flat property config
                cfg.getProperty("content", null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { JsonUtil.deserialize(it, IdempotencyConfig::class.java) }
                    ?: IdempotencyConfig(
                        enabled  = cfg.getProperty("enabled", "true").toBoolean(),
                        ttlHours = cfg.getProperty("ttl-hours", "24").toLong(),
                        maxSize  = cfg.getProperty("max-size", "1000").toLong(),
                        spec     = cfg.getProperty("spec", null)
                    )
            }
        )

    @Bean
    fun apolloInstanceRegistry(
        client: ApolloOpenApiClient,
        env: Environment
    ): InstanceRegistry {
        val appId = env.getRequiredProperty("workflow.config.apollo.app-id")
        val envName = env.getProperty("workflow.config.apollo.env", "DEV")
        val cluster = env.getProperty("workflow.config.apollo.cluster", "default")
        val operator = env.getProperty("workflow.config.apollo.operator", "workflow-admin")
        return ApolloInstanceRegistry(client, appId, envName, cluster, operator)
    }

    @Bean
    fun apolloInstanceDiscovery(): InstanceDiscovery = ApolloInstanceDiscovery()
}
