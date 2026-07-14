package com.fluxion.redis.redisson

import com.fluxion.core.lock.DistributedLockProvider
import com.fluxion.redis.redisson.lock.RedissonDistributedLockProvider
import com.fluxion.redis.spi.RedisClientAdapter
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.slf4j.*
import org.slf4j.info
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * Auto-configuration for the Redisson-based Redis adapter.
 *
 * Registers the [RedissonClient] instance (single-server mode, configurable via
 * `workflow.redis.uri`), the [RedisClientAdapter] facade, and — as a bonus — the
 * [RedissonDistributedLockProvider] which gives callers can use for strong distributed
 * locking semantics (watchdog renewal, reentrancy, fair queuing).
 *
 * Redisson is the recommended adapter for production deployments because it handles
 * cluster topology changes, connection pooling, and retry logic out of the box.
 *
 * Configuration properties (tuned for typical workflow workloads):
 *  - `workflow.redis.uri`       — Redis connection URI
 *  - connectionMinimumIdleSize   = 2  (keep a couple of warm connections ready)
 *  - connectionPoolSize        = 10 (cap concurrent commands per node)
 *  - connectTimeout / timeout  = 3s (avoid hanging the engine)
 *  - retryAttempts             = 3  (retry transient failures)
 *  - retryInterval             = 1s (back-off between attempts)
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient::class)
class RedissonWorkflowAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Create the [RedissonClient] using single-server configuration derived from the URI.
     *
     * Applications that need cluster / sentinel / replicated topology should define their
     * own `RedissonClient` @Bean and this definition will back off via
     * `@ConditionalOnMissingBean`.
     */
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

    /**
     * Register the Redisson-backed [RedisClientAdapter] SPI unless the caller has
     * already wired a different adapter.
     */
    @Bean
    @ConditionalOnMissingBean(RedisClientAdapter::class)
    fun redisClientAdapter(workflowRedissonClient: RedissonClient): RedisClientAdapter {
        log.info { "Registering RedissonRedisAdapter as RedisClientAdapter" }
        return RedissonRedisAdapter(workflowRedissonClient)
    }

    /**
     * Register the Redisson-backed [DistributedLockProvider] when no other lock
     * provider bean has been supplied. This is the recommended provider because Redisson's RLock
     * implementation includes watchdog auto-renewal and reentrant semantics out of the box.
     */
    @Bean
    @ConditionalOnBean(RedissonClient::class)
    @ConditionalOnMissingBean(DistributedLockProvider::class)
    fun redissonDistributedLockProvider(client: RedissonClient): DistributedLockProvider {
        log.info { "Registering RedissonDistributedLockProvider" }
        return RedissonDistributedLockProvider(client)
    }
}
