package com.fluxion.config.apollo

import com.ctrip.framework.apollo.openapi.client.ApolloOpenApiClient
import com.fluxion.adapter.spi.config.DefinitionConfigPublisher
import com.fluxion.adapter.spi.config.DefinitionConfigSubscriber
import com.fluxion.adapter.spi.config.FunctionConfigPublisher
import com.fluxion.adapter.spi.config.FunctionConfigSubscriber
import com.fluxion.adapter.spi.config.IdempotencyConfig
import com.fluxion.adapter.spi.config.IdempotencyConfigSubscriber
import com.fluxion.adapter.spi.config.SchemaConfigPublisher
import com.fluxion.adapter.spi.config.SchemaConfigSnapshot
import com.fluxion.adapter.spi.config.SchemaConfigSubscriber
import com.fluxion.adapter.spi.config.WorkflowDefinitionSnapshot
import com.fluxion.adapter.spi.registry.InstanceDiscovery
import com.fluxion.adapter.spi.registry.InstanceRegistry
import com.fluxion.core.util.JsonUtil
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

/**
 * Apollo 閰嶇疆涓績 + 娉ㄥ唽涓績鑷姩瑁呴厤
 *
 * ## 澹版槑寮忛厤缃敞鍐岋紙瀵规爣 NacosConfigs.java锛?
 *
 * 鎵€鏈?Subscriber/Publisher 鍧囦娇鐢ㄦ硾鍨嬪熀绫荤洿鎺ュ疄渚嬪寲锛屾棤闇€涓烘瘡绉嶉厤缃畾涔夌嫭绔嬪瓙绫汇€?
 * 鏂板 keyed 閰嶇疆锛氫竴琛?`ApolloKeyedConfigSubscriber(mapper = { ... }, removedMapper = { ... })`
 * 鏂板鍗曞€奸厤缃細涓€琛?`ApolloConfigSubscriber(parser = { cfg -> ... })`
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
                // 优先读取 JSON 内容，支持工作流级覆盖；兼容旧版扁平属性配置
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
