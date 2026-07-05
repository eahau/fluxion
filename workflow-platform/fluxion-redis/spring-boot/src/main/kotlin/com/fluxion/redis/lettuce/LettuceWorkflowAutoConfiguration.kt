package com.fluxion.redis.lettuce

import com.fluxion.redis.spi.RedisClientAdapter
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import org.slf4j.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * Lettuce Redis 适配器自动配置
 */
@AutoConfiguration
@ConditionalOnClass(RedisClient::class)
class LettuceWorkflowAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean(RedisClient::class)
    fun workflowRedisClient(
        @Value("\${workflow.redis.uri:redis://localhost:6379/0}") redisUri: String
    ): RedisClient {
        log.info { "Creating Lettuce RedisClient from URI: $redisUri" }
        return RedisClient.create(RedisURI.create(redisUri))
    }

    @Bean
    @ConditionalOnMissingBean(name = ["workflowRedisConnection"])
    fun workflowRedisConnection(redisClient: RedisClient): StatefulRedisConnection<String, String> {
        log.info { "Connecting to Redis via Lettuce..." }
        val conn = redisClient.connect()
        log.info { "Lettuce Redis connection established." }
        return conn
    }

    @Bean
    @ConditionalOnMissingBean(RedisClientAdapter::class)
    fun redisClientAdapter(
        workflowRedisConnection: StatefulRedisConnection<String, String>
    ): RedisClientAdapter {
        log.info { "Registering LettuceRedisAdapter as RedisClientAdapter" }
        return LettuceRedisAdapter(workflowRedisConnection)
    }
}
