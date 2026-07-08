package com.fluxion.redis.config

import com.fluxion.core.function.FunctionRegistry
import com.fluxion.decorator.ratelimit.RateLimitStore
import com.fluxion.redis.function.RedisCommandFunction
import com.fluxion.redis.lettuce.LettuceWorkflowAutoConfiguration
import com.fluxion.redis.ratelimit.RedisRateLimitStore
import com.fluxion.redis.redisson.RedissonWorkflowAutoConfiguration
import com.fluxion.redis.spi.RedisClientAdapter
import org.slf4j.*
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * Top-level auto-configuration that wires the Redis-backed integrations into the
 * workflow engine after a concrete [RedisClientAdapter] is available.
 *
 * Runs after the client-specific auto-configurations ([LettuceWorkflowAutoConfiguration],
 * [RedissonWorkflowAutoConfiguration]) so either one (or an externally supplied bean)
 * has had a chance to register the adapter. Two higher-level beans are exposed:
 *
 *  1. `RedisCommandFunction` — registers `builtin:redisCommand` into the [FunctionRegistry]
 *     so workflow authors can invoke arbitrary Redis commands from the designer.
 *  2. `RedisRateLimitStore` — default [RateLimitStore] when the decorator module did not
 *     register a more specific store (in-memory, JDBC, …).
 */
@AutoConfiguration(after = [LettuceWorkflowAutoConfiguration::class, RedissonWorkflowAutoConfiguration::class])
class RedisWorkflowAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Register `builtin:redisCommand` into the shared function registry.
     *
     * Only activates when a [RedisClientAdapter] bean is present in the context;
     * the concrete adapter implementation is logged so operators can confirm which
     * client (Lettuce vs Redisson vs Spring Data) is active at runtime.
     */
    @Bean
    @ConditionalOnBean(RedisClientAdapter::class)
    fun redisCommandFunction(
        registry: FunctionRegistry,
        redisAdapter: RedisClientAdapter
    ): RedisCommandFunction {
        val function = RedisCommandFunction(redisAdapter)
        registry.register("builtin:redisCommand", function)
        log.info { "Registered builtin:redisCommand (adapter: ${redisAdapter.javaClass.simpleName})" }
        return function
    }

    /**
     * Fallback [RateLimitStore] backed by a Redis Lua script.
     *
     * Only registered when:
     *  - A [RedisClientAdapter] bean exists, AND
     *  - No other [RateLimitStore] has been registered by the application or another
     *    auto-configuration (the decorator module ships an in-memory default).
     */
    @Bean
    @ConditionalOnBean(RedisClientAdapter::class)
    @ConditionalOnMissingBean(RateLimitStore::class)
    fun redisRateLimitStore(redisAdapter: RedisClientAdapter): RateLimitStore {
        log.info { "Registering RedisRateLimitStore" }
        return RedisRateLimitStore(redisAdapter)
    }
}
