package com.fluxion.redis.springdata

import com.fluxion.redis.spi.RedisClientAdapter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Spring Data Redis 适配器自动配置
 *
 * 生效条件：
 *   - 类路径存在 StringRedisTemplate（即应用已引入 spring-boot-starter-data-redis）
 *   - 当前没有其它 RedisClientAdapter Bean（ Lettuce / Redisson 适配器优先级更高）
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate::class)
@ConditionalOnMissingBean(RedisClientAdapter::class)
class SpringDataRedisAdapterConfiguration {

    @Bean
    fun redisClientAdapter(redisTemplate: StringRedisTemplate): RedisClientAdapter =
        SpringDataRedisAdapter(redisTemplate)
}
