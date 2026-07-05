package com.fluxion.config.nacos

import com.alibaba.nacos.api.NacosFactory
import com.alibaba.nacos.api.config.ConfigService
import com.alibaba.nacos.api.naming.NamingFactory
import com.alibaba.nacos.api.naming.NamingService
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
import java.util.Properties

/**
 * Nacos 配置中心 + 注册中心自动装配
 *
 * 生效条件：
 *   1. classpath 存在 ConfigService（nacos-client jar）
 *   2. 配置 workflow.config.type=nacos
 *
 * ## 声明式配置注册（对标 NacosConfigs.java）
 *
 * 通过 [NacosConfigFactory] 封装 ConfigService + group，
 * 新增配置只需传业务参数，无需重复基础设施依赖。
 */
@AutoConfiguration
@ConditionalOnClass(name = ["com.alibaba.nacos.api.config.ConfigService"])
@ConditionalOnProperty(name = ["workflow.config.type"], havingValue = "nacos")
class NacosConfigAutoConfiguration {

    @Bean
    fun nacosConfigService(env: Environment): ConfigService =
        NacosFactory.createConfigService(buildProperties(env))

    @Bean
    fun nacosNamingService(env: Environment): NamingService =
        NamingFactory.createNamingService(buildProperties(env))

    // ─── 配置工厂（一次性注册，消除后续 Bean 的重复参数）────────

    @Bean
    fun nacosConfigFactory(configService: ConfigService): NacosConfigFactory =
        NacosConfigFactory(configService)

    // ─── Publisher ──────────────────────────────────────────────────

    @Bean
    fun nacosDefinitionConfigPublisher(configService: ConfigService): DefinitionConfigPublisher =
        NacosDefinitionConfigPublisher(configService)

    @Bean
    fun nacosFunctionConfigPublisher(configService: ConfigService): FunctionConfigPublisher =
        NacosFunctionConfigPublisher(configService)

    @Bean
    fun nacosSchemaConfigPublisher(configService: ConfigService): SchemaConfigPublisher =
        NacosSchemaConfigPublisher(configService)

    // ─── Subscriber（通过 factory 声明式注册）─────────────────────

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

    // ─── 注册中心 ──────────────────────────────────────────────────

    @Bean
    fun nacosInstanceRegistry(namingService: NamingService): InstanceRegistry =
        NacosInstanceRegistry(namingService)

    @Bean
    fun nacosInstanceDiscovery(namingService: NamingService): InstanceDiscovery =
        NacosInstanceDiscovery(namingService)

    private fun buildProperties(env: Environment): Properties = Properties().apply {
        setProperty("serverAddr", env.getRequiredProperty("workflow.config.nacos.server-addr"))
        env.getProperty("workflow.config.nacos.namespace")?.let { setProperty("namespace", it) }
        env.getProperty("workflow.config.nacos.username")?.let { setProperty("username", it) }
        env.getProperty("workflow.config.nacos.password")?.let { setProperty("password", it) }
    }
}
