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
 * Auto-configuration for the Lettuce-based Redis adapter.
 *
 * Creates three beans in order: the low-level [RedisClient], a long-lived
 * [StatefulRedisConnection], and finally the [RedisClientAdapter] facade.
 *
 * Configuration properties:
 *  - `workflow.redis.uri` — standard Redis URI string (default `redis://localhost:6379/0`).
 *    The URI encodes host, port, database index, password, and SSL mode in a single string; see
 *    [io.lettuce.core.RedisURI] for the accepted grammar.
 *
 * Only activates when `io.lettuce.core.RedisClient` is on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(RedisClient::class)
class LettuceWorkflowAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Create the Lettuce [RedisClient] using the configured Redis URI.
     *
     * Skips registration if the application already provides a custom client bean
     * (for example to fine-tune client resources or event-loop pools).
     */
    @Bean
    @ConditionalOnMissingBean(RedisClient::class)
    fun workflowRedisClient(
        @Value("\${workflow.redis.uri:redis://localhost:6379/0}") redisUri: String
    ): RedisClient {
        log.info { "Creating Lettuce RedisClient from URI: $redisUri" }
        return RedisClient.create(RedisURI.create(redisUri))
    }

    /**
     * Open a long-lived string/string [StatefulRedisConnection] from the client.
     *
     * The connection is stateful and threadsafe; multiple adapter invocations share
     * the same underlying TCP channel. Named `workflowRedisConnection` so callers can
     * override it with a cluster / sentinel variant if required.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["workflowRedisConnection"])
    fun workflowRedisConnection(redisClient: RedisClient): StatefulRedisConnection<String, String> {
        log.info { "Connecting to Redis via Lettuce..." }
        val conn = redisClient.connect()
        log.info { "Lettuce Redis connection established." }
        return conn
    }

    /**
     * Wire the [LettuceRedisAdapter] SPI implementation.
     *
     * Only registered when no other [RedisClientAdapter] bean exists. Applications
     * that want to plug in a custom adapter (e.g. for tracing) can simply declare
     * their own bean and this one will back off.
     */
    @Bean
    @ConditionalOnMissingBean(RedisClientAdapter::class)
    fun redisClientAdapter(
        workflowRedisConnection: StatefulRedisConnection<String, String>
    ): RedisClientAdapter {
        log.info { "Registering LettuceRedisAdapter as RedisClientAdapter" }
        return LettuceRedisAdapter(workflowRedisConnection)
    }
}
