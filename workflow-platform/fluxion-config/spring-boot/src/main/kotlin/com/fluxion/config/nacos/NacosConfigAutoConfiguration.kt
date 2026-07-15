package com.fluxion.config.nacos

import com.alibaba.nacos.api.NacosFactory
import com.alibaba.nacos.api.config.ConfigService
import com.fluxion.config.core.DefinitionConfigPublisher
import com.fluxion.config.core.DefinitionConfigSubscriber
import com.fluxion.config.core.FunctionConfigPublisher
import com.fluxion.config.core.FunctionConfigSubscriber
import com.fluxion.config.core.CacheConfig
import com.fluxion.config.core.CacheConfigSubscriber
import com.fluxion.config.core.IdempotencyConfig
import com.fluxion.config.core.IdempotencyConfigSubscriber
import com.fluxion.config.core.KeyedConfigSubscriber
import com.fluxion.config.core.SchemaConfigPublisher
import com.fluxion.config.core.SchemaConfigSnapshot
import com.fluxion.config.core.SchemaConfigSubscriber
import com.fluxion.config.core.WorkflowDefinitionSnapshot
import com.fluxion.core.util.JsonUtil
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import java.util.Properties

/**
 * Nacos config center + service discovery auto-configuration.
 *
 * Activation conditions:
 *   1. ConfigService class is present (nacos-client jar)
 *   2. Configuration property workflow.config.type=nacos
 *
 * ## Builder-style bindings (see NacosConfigs.java for details)
 *
 * Uses [NacosConfigFactory] to assemble ConfigService + group.
 * New configurations only require business parameters; no need to re-implement infrastructure.
 */
@AutoConfiguration
@ConditionalOnClass(name = ["com.alibaba.nacos.api.config.ConfigService"])
@ConditionalOnProperty(name = ["workflow.config.type"], havingValue = "nacos")
class NacosConfigAutoConfiguration {

    @Bean
    fun nacosConfigService(env: Environment): ConfigService =
        NacosFactory.createConfigService(buildProperties(env))

    // ===== Config Factory (one-time binding, eliminates duplicate parameter passing) =====

    @Bean
    fun nacosConfigFactory(configService: ConfigService): NacosConfigFactory =
        NacosConfigFactory(configService)

    // ===== Publishers =====

    @Bean
    fun nacosDefinitionConfigPublisher(configService: ConfigService): DefinitionConfigPublisher =
        NacosDefinitionConfigPublisher(configService)

    @Bean
    fun nacosFunctionConfigPublisher(configService: ConfigService): FunctionConfigPublisher =
        NacosFunctionConfigPublisher(configService)

    @Bean
    fun nacosSchemaConfigPublisher(configService: ConfigService): SchemaConfigPublisher =
        NacosSchemaConfigPublisher(configService)

    // ===== Subscribers (builder-style binding via factory) =====

    @Bean
    fun nacosDefinitionConfigSubscriber(f: NacosConfigFactory): DefinitionConfigSubscriber =
        f.keyedSubscriber(
            dataIdPrefix = "workflow.definition.",
            indexDataId  = "workflow.definition.__index__",
            mapper       = { key, content -> WorkflowDefinitionSnapshot.of(key, content, 0, true) }
        )

    @Bean
    fun nacosFunctionConfigSubscriber(configService: ConfigService): FunctionConfigSubscriber =
        NacosFunctionConfigSubscriber(configService)

    @Bean
    fun nacosSchemaConfigSubscriber(f: NacosConfigFactory): SchemaConfigSubscriber =
        f.keyedSubscriber(
            dataIdPrefix = "workflow.schema.",
            indexDataId  = "workflow.schema.__index__",
            mapper       = { _, content -> JsonUtil.deserialize(content, SchemaConfigSnapshot::class.java) }
        )

    @Bean
    fun nacosIdempotencyConfigSubscriber(f: NacosConfigFactory): IdempotencyConfigSubscriber =
        f.subscriber(
            dataId  = "workflow.idempotency",
            mapper  = { content -> JsonUtil.deserialize(content, IdempotencyConfig::class.java) },
            default = IdempotencyConfig()
        )

    @Bean
    fun nacosCacheConfigSubscriber(f: NacosConfigFactory): CacheConfigSubscriber =
        f.subscriber(
            dataId  = "workflow.cache",
            mapper  = { content -> JsonUtil.deserialize(content, CacheConfig::class.java) },
            default = CacheConfig()
        )

    private fun buildProperties(env: Environment): Properties = Properties().apply {
        setProperty("serverAddr", env.getRequiredProperty("workflow.config.nacos.server-addr"))
        env.getProperty("workflow.config.nacos.namespace")?.let { setProperty("namespace", it) }
        env.getProperty("workflow.config.nacos.username")?.let { setProperty("username", it) }
        env.getProperty("workflow.config.nacos.password")?.let { setProperty("password", it) }
    }
}