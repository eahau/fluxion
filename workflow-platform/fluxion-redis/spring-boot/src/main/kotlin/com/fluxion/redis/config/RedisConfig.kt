package com.fluxion.redis.config

import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.ratelimit.RateLimitStore
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
 * Redis 工作流函数 Spring Boot 自动配置
 *
 * 注意：@ConditionalOnBean 只能放在 @Bean 方法级别，
 * 放在类级别会因 AutoConfiguration 处理顺序不确定而可能找不到 RedisClientAdapter。
 *
 * 本配置不直接引用 RedissonClient/RedisClient 等具体客户端类型，
 * 避免在缺少对应客户端依赖的运行时出现 ClassNotFoundException。
 */
@AutoConfiguration(after = [LettuceWorkflowAutoConfiguration::class, RedissonWorkflowAutoConfiguration::class])
class RedisWorkflowAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnBean(RedisClientAdapter::class)
    fun redisCommandFunction(
        registry: FunctionRegistry,
        redisAdapter: RedisClientAdapter
    ): RedisCommandFunction {
        val function = RedisCommandFunction(redisAdapter)
        registry.register("builtin:redisCommand", function.meta(), function)
        log.info { "Registered builtin:redisCommand (adapter: ${redisAdapter.javaClass.simpleName})" }
        return function
    }

    @Bean
    @ConditionalOnBean(RedisClientAdapter::class)
    @ConditionalOnMissingBean(RateLimitStore::class)
    fun redisRateLimitStore(redisAdapter: RedisClientAdapter): RateLimitStore {
        log.info { "Registering RedisRateLimitStore" }
        return RedisRateLimitStore(redisAdapter)
    }
}
