/**
 * Spring Boot auto-configuration for `fluxion-schema:core`.
 *
 * Always registers the JSON-Schema format bundle plus the core
 * [SchemaManager] facade backed by an [InMemorySchemaRegistry]. Format
 * extensions for Avro and Protobuf are shipped as separate spring-boot
 * sub-modules that contribute their own [SchemaFormatBundle] beans:
 * * `fluxion-schema:avro:spring-boot`
 * * `fluxion-schema:protobuf:spring-boot`
 *
 * [SchemaManager] collects every [SchemaFormatBundle] on the context and
 * assembles O(1) lookup maps per SPI (parser/validator/extractor/codec/
 * dataProvider) so adding new formats is purely additive.
 *
 * When a [SchemaConfigSubscriber] bean is present on the classpath (provided
 * by the `fluxion-config` adapter), the nested [SchemaHotReloadConfiguration]
 * activates and wires a [SchemaConfigApplier] that performs the initial bulk
 * load plus continuous incremental push updates.
 */
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
import org.slf4j.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration
@EnableConfigurationProperties(SchemaProperties::class)
@ConditionalOnProperty(prefix = "fluxion.schema", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class FluxionSchemaAutoConfiguration {

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

    /**
     * Worker-side hot-reload wiring.
     *
     * Activates only when the config-adapter SPI classes are on the
     * classpath AND a concrete [SchemaConfigSubscriber] bean has been
     * registered (Nacos, Apollo, or internal HTTP registry adapter).
     * The resulting [SchemaConfigApplier] performs the initial bulk
     * schema load and then subscribes to incremental push updates.
     */
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
            log.info { "SchemaConfigApplier initialized for scope=$scope" }
            return applier
        }
    }
}
