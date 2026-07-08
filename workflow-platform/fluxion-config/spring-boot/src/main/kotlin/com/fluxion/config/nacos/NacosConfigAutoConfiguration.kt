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
 * Nacos 閰嶇疆涓績 + 娉ㄥ唽涓績鑷姩瑁呴厤
 *
 * 鐢熸晥鏉′欢锛?
 *   1. classpath 瀛樺湪 ConfigService锛坣acos-client jar锛?
 *   2. 閰嶇疆 workflow.config.type=nacos
 *
 * ## 澹版槑寮忛厤缃敞鍐岋紙瀵规爣 NacosConfigs.java锛?
 *
 * 閫氳繃 [NacosConfigFactory] 灏佽 ConfigService + group锛?
 * 鏂板閰嶇疆鍙渶浼犱笟鍔″弬鏁帮紝鏃犻渶閲嶅鍩虹璁炬柦渚濊禆銆?
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

    // 鈹€鈹€鈹€ 閰嶇疆宸ュ巶锛堜竴娆℃€ф敞鍐岋紝娑堥櫎鍚庣画 Bean 鐨勯噸澶嶅弬鏁帮級鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Bean
    fun nacosConfigFactory(configService: ConfigService): NacosConfigFactory =
        NacosConfigFactory(configService)

    // 鈹€鈹€鈹€ Publisher 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Bean
    fun nacosDefinitionConfigPublisher(configService: ConfigService): DefinitionConfigPublisher =
        NacosDefinitionConfigPublisher(configService)

    @Bean
    fun nacosFunctionConfigPublisher(configService: ConfigService): FunctionConfigPublisher =
        NacosFunctionConfigPublisher(configService)

    @Bean
    fun nacosSchemaConfigPublisher(configService: ConfigService): SchemaConfigPublisher =
        NacosSchemaConfigPublisher(configService)

    // 鈹€鈹€鈹€ Subscriber锛堥€氳繃 factory 澹版槑寮忔敞鍐岋級鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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

    // 鈹€鈹€鈹€ 娉ㄥ唽涓績 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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
