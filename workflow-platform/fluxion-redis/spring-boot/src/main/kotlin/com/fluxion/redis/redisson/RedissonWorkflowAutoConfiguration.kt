package com.fluxion.redis.redisson

import com.fluxion.core.lock.DistributedLockProvider
import com.fluxion.redis.redisson.lock.RedissonDistributedLockProvider
import com.fluxion.redis.spi.RedisClientAdapter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.slf4j.LoggerFactory
import org.slf4j.info
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * Redisson Redis 适配器自动配置
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient::class)
class RedissonWorkflowAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean(RedissonClient::class)
    fun workflowRedissonClient(
        @Value("\${workflow.redis.uri:redis://localhost:6379/0}") redisUri: String
    ): RedissonClient {
        log.info { "Creating RedissonClient (single server) from URI: $redisUri" }
        val config = Config()
        config.useSingleServer()
            .setAddress(redisUri)
            .setConnectionMinimumIdleSize(2)
            .setConnectionPoolSize(10)
            .setConnectTimeout(3000)
            .setTimeout(3000)
            .setRetryAttempts(3)
            .setRetryInterval(1000)
        return Redisson.create(config)
    }

    @Bean
    @ConditionalOnMissingBean(RedisClientAdapter::class)
    fun redisClientAdapter(workflowRedissonClient: RedissonClient): RedisClientAdapter {
        log.info { "Registering RedissonRedisAdapter as RedisClientAdapter" }
        return RedissonRedisAdapter(workflowRedissonClient)
    }

    @Bean
    @ConditionalOnBean(RedissonClient::class)
    @ConditionalOnMissingBean(DistributedLockProvider::class)
    fun redissonDistributedLockProvider(client: RedissonClient): DistributedLockProvider {
        log.info { "Registering RedissonDistributedLockProvider" }
        return RedissonDistributedLockProvider(client)
    }
}
