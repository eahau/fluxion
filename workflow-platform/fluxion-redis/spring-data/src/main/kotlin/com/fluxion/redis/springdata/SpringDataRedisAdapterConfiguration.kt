package com.fluxion.redis.springdata

import com.fluxion.redis.spi.RedisClientAdapter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Auto-configuration for the Spring Data Redis adapter.
 *
 * Activates only when:
 *  - [StringRedisTemplate] is present on the classpath (i.e. the application pulled in
 *    `spring-boot-starter-data-redis`), AND
 *  - No other [RedisClientAdapter] bean has been registered yet.
 *
 * This adapter has the lowest precedence among the three Redis client adapters; the
 * Lettuce and Redisson auto-configurations both define explicit `@ConditionalOnMissingBean`
 * guards on `RedisClientAdapter` and therefore win when their client libraries are
 * also on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate::class)
@ConditionalOnMissingBean(RedisClientAdapter::class)
class SpringDataRedisAdapterConfiguration {

    /**
     * Create the adapter and hand it the shared `StringRedisTemplate` that Spring Boot
     * already auto-configured from application properties.
     */
    @Bean
    fun redisClientAdapter(redisTemplate: StringRedisTemplate): RedisClientAdapter =
        SpringDataRedisAdapter(redisTemplate)
}
