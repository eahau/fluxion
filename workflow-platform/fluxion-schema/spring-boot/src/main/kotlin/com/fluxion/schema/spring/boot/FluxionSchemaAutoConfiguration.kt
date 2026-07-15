package com.fluxion.schema.spring.boot

import com.fluxion.cache.FluxionCacheFactory
import com.fluxion.schema.DefaultSchemaManager
import com.fluxion.schema.api.ExternalSchemaRegistry
import com.fluxion.schema.api.ExternalSchemaRegistryPoller
import com.fluxion.schema.api.SchemaCacheManager
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
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Primary
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
    fun schemaCacheManager(properties: SchemaProperties): SchemaCacheManager =
        SchemaCacheManager(preloadOnStartup = properties.cache.preloadOnStartup)

    @Bean
    @ConditionalOnMissingBean
    fun schemaResolver(schemaRegistry: SchemaRegistry?): SchemaResolver? =
        schemaRegistry?.let { FluxionSchemaResolver(it) }

    @Bean
    @ConditionalOnMissingBean
    fun schemaManager(
        bundles: List<SchemaFormatBundle>,
        resolver: SchemaResolver?,
        registry: SchemaRegistry?,
        cacheManager: SchemaCacheManager
    ): SchemaManager = DefaultSchemaManager(
        bundles = bundles,
        resolver = resolver,
        registry = registry,
        cacheManager = cacheManager
    )

    @Configuration
    @ConditionalOnBean(ExternalSchemaRegistry::class)
    class ExternalSchemaRegistryPollingConfiguration {

        private val log = LoggerFactory.getLogger(javaClass)

        @Bean
        fun schemaRegistryPoller(
            registry: ExternalSchemaRegistry,
            schemaManager: DefaultSchemaManager,
            properties: SchemaProperties
        ): ExternalSchemaRegistryPoller {
            val poller = ExternalSchemaRegistryPoller(
                registry = registry,
                intervalMs = properties.cache.pollIntervalMs,
                listener = object : com.fluxion.schema.api.SchemaRegistryListener {
                    override fun onSchemaRegistered(name: String, schema: com.fluxion.schema.model.Schema) {
                        schemaManager.getCacheManager().putSchema(name, schema)
                        log.info { "Registered schema [$name] from external registry" }
                    }

                    override fun onSchemaUpdated(name: String, schema: com.fluxion.schema.model.Schema) {
                        schemaManager.getCacheManager().putSchema(name, schema)
                        log.info { "Updated schema [$name] from external registry" }
                    }

                    override fun onSchemaDeleted(name: String) {
                        schemaManager.getCacheManager().evictSchema(name)
                        log.info { "Deleted schema [$name] from cache" }
                    }
                }
            )
            poller.start()
            log.info { "ExternalSchemaRegistryPoller started with interval=${properties.cache.pollIntervalMs}ms" }
            return poller
        }
    }
}
