package com.fluxion.cache.spring.boot

import com.fluxion.cache.FluxionCacheFactory
import com.fluxion.config.core.CacheConfigSubscriber
import org.slf4j.LoggerFactory
import org.slf4j.*
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(name = ["com.fluxion.cache.FluxionCacheFactory"])
@EnableConfigurationProperties(CacheProperties::class)
class FluxionCacheAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun fluxionCacheFactoryInitializer(
        subscriber: CacheConfigSubscriber?,
        properties: CacheProperties
    ): FluxionCacheFactoryInitializer = FluxionCacheFactoryInitializer(subscriber, properties)
}

class FluxionCacheFactoryInitializer(
    subscriber: CacheConfigSubscriber?,
    properties: CacheProperties
) {
    init {
        FluxionCacheFactory.init(
            configProvider = subscriber?.let { { it.load() } },
            configWatcher = subscriber?.let { watcher -> { listener -> watcher.watch { listener(it) } } } ?: {},
            defaultSpec = properties.defaultSpec
        )
    }
}

@ConfigurationProperties(prefix = "fluxion.cache")
data class CacheProperties(
    val defaultSpec: String = "maximumSize=1000,expireAfterWrite=30m"
)