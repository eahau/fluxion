package com.fluxion.schema.spring.boot

import com.fluxion.adapter.spi.config.SchemaConfigSubscriber
import com.fluxion.schema.DefaultSchemaManager
import com.fluxion.schema.api.SchemaDataProviderRegistry
import com.fluxion.schema.api.SchemaFormatBundle
import com.fluxion.schema.api.SchemaManager
import com.fluxion.schema.api.SchemaRegistry
import com.fluxion.schema.api.SchemaResolver
import com.fluxion.schema.json.FluxionSchemaResolver
import com.fluxion.schema.json.JsonSchemaCodec
import com.fluxion.schema.json.JsonSchemaDataProvider
import com.fluxion.schema.json.JsonSchemaFieldExtractor
import com.fluxion.schema.json.JsonSchemaParser
import com.fluxion.schema.json.JsonSchemaValidator
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.registry.InMemorySchemaRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

/**
 * fluxion-schema 核心 Spring Boot 自动装配。
 *
 * 注册 JSON Schema 格式 Bundle、[SchemaManager] 门面和 [InMemorySchemaRegistry]。
 * Avro / Protobuf 扩展由各自独立的 spring-boot 子模块装配：
 * - `fluxion-schema:avro:spring-boot`
 * - `fluxion-schema:protobuf:spring-boot`
 *
 * [SchemaManager] 收集所有 [SchemaFormatBundle]，按格式拆分构建 O(1) 路由表。
 *
 * 当 classpath 存在 [SchemaConfigSubscriber]（由 fluxion-config 模块提供）时，
 * 自动注册 [SchemaConfigApplier]，实现 Schema 热更新。
 */
@Configuration
@EnableConfigurationProperties(SchemaProperties::class)
@ConditionalOnProperty(prefix = "fluxion.schema", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class FluxionSchemaAutoConfiguration {

    // ─── JSON Schema Bundle（始终注册）──────────────────────

    @Bean
    @ConditionalOnMissingBean(name = ["jsonSchemaBundle"])
    fun jsonSchemaBundle(): SchemaFormatBundle = SchemaFormatBundle(
        format = SchemaFormat.JSON_SCHEMA,
        parser = JsonSchemaParser(),
        validator = JsonSchemaValidator(),
        extractor = JsonSchemaFieldExtractor(),
        codec = JsonSchemaCodec(),
        dataProvider = JsonSchemaDataProvider()
    )

    // ─── Registry / Resolver ────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    fun schemaDataProviderRegistry(
        bundles: List<SchemaFormatBundle>
    ): SchemaDataProviderRegistry = SchemaDataProviderRegistry(
        bundles.mapNotNull { b -> b.dataProvider?.let { b.format to it } }.toMap()
    )

    @Bean
    @ConditionalOnMissingBean
    fun inMemorySchemaRegistry(): InMemorySchemaRegistry = InMemorySchemaRegistry()

    @Bean
    @ConditionalOnMissingBean
    fun schemaResolver(schemaRegistry: SchemaRegistry?): SchemaResolver? {
        return schemaRegistry?.let { FluxionSchemaResolver(it) }
    }

    // ─── SchemaManager ─────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    fun schemaManager(
        bundles: List<SchemaFormatBundle>,
        resolver: SchemaResolver?,
        registry: SchemaRegistry?
    ): SchemaManager = DefaultSchemaManager(
        bundles = bundles,
        resolver = resolver,
        registry = registry
    )

    // ─── Worker 侧 Schema 热更新（仅当 SchemaConfigSubscriber 存在时激活）──

    @Configuration
    @ConditionalOnClass(name = ["com.fluxion.adapter.spi.config.SchemaConfigSubscriber"])
    @ConditionalOnBean(SchemaConfigSubscriber::class)
    class SchemaHotReloadConfiguration {

        private val log = LoggerFactory.getLogger(javaClass)

        @Bean
        fun schemaConfigApplier(
            subscriber: SchemaConfigSubscriber,
            registry: InMemorySchemaRegistry,
            schemaManager: SchemaManager,
            env: Environment
        ): SchemaConfigApplier {
            val scope = env.getProperty("workflow.instance.app-group")
            val applier = SchemaConfigApplier(
                subscriber = subscriber,
                registry = registry,
                schemaManager = schemaManager,
                scope = scope
            )
            applier.init()
            log.info("SchemaConfigApplier initialized for scope=$scope")
            return applier
        }
    }
}
