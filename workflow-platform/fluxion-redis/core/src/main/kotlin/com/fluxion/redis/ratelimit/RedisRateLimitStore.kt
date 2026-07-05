package com.fluxion.redis.ratelimit

import com.fluxion.core.ratelimit.RateLimitConfig
import com.fluxion.core.ratelimit.RateLimitStore
import com.fluxion.redis.spi.RedisClientAdapter
import org.slf4j.LoggerFactory
import org.slf4j.debug
import org.slf4j.warn
import java.security.MessageDigest

/**
 * 基于 Redis + Lua 的滑动窗口限流存储。
 *
 * 整条“读状态-计算-写状态”逻辑通过 Lua 脚本在 Redis 服务端原子执行，
 * 兼容 Lettuce / Redisson / Spring Data Redis 等 [RedisClientAdapter] 实现。
 *
 * Lua 脚本位于 classpath 资源文件 `com/fluxion/redis/ratelimit/rate-limit.lua`，
 * 默认由运维方预先通过 `SCRIPT LOAD` 加载到 Redis（集群需加载到每个主节点）。
 * 应用启动时从 classpath 读取脚本并计算 SHA1，运行时优先使用 `EVALSHA` 调用；
 * 若服务端不存在该脚本，自动回退到 `EVAL` 传输完整脚本。
 */
class RedisRateLimitStore(private val adapter: RedisClientAdapter) : RateLimitStore {

    private val log = LoggerFactory.getLogger(javaClass)

    private val scriptSha: String = computeSha1(LUA_SCRIPT)

    override fun tryAcquire(key: String, config: RateLimitConfig): Boolean {
        val now = System.currentTimeMillis()
        val ttlSeconds = config.upLimited * config.cdSeconds
        val keys = listOf(key)
        val args = listOf(
            config.upLimited.toString(),
            config.cdSeconds.toString(),
            config.recoveryPerCd.toString(),
            ttlSeconds.toString(),
            now.toString()
        )
        return try {
            val result = evalWithFallback(keys, args)
            toBoolean(result).also { allowed ->
                log.debug { "Redis rate limit key=$key allowed=$allowed config=$config" }
            }
        } catch (e: Exception) {
            log.warn(e) { "Redis rate limit eval failed for key=$key, fallback to allow" }
            true
        }
    }

    private fun evalWithFallback(keys: List<String>, args: List<String>): Any? {
        return try {
            adapter.evalSha(scriptSha, keys, args)
        } catch (e: Exception) {
            if (isNoScriptError(e)) {
                log.debug { "NOSCRIPT detected, fallback to EVAL with full script" }
                adapter.eval(LUA_SCRIPT, keys, args)
            } else {
                throw e
            }
        }
    }

    private fun isNoScriptError(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t.message?.contains("NOSCRIPT", ignoreCase = true) == true) return true
            t = t.cause
        }
        return false
    }

    private fun toBoolean(result: Any?): Boolean = when (result) {
        is Boolean -> result
        is Number -> result.toInt() == 1
        is List<*> -> toBoolean(result.firstOrNull())
        else -> result?.toString() == "1"
    }

    companion object {
        internal val LUA_SCRIPT: String = loadScript()

        private const val RESOURCE_PATH = "/rate-limit.lua"

        private fun loadScript(): String {
            val stream = RedisRateLimitStore::class.java.getResourceAsStream(RESOURCE_PATH)
                ?: throw IllegalStateException("Rate limit Lua script not found in classpath: $RESOURCE_PATH")
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

        private fun computeSha1(script: String): String {
            val digest = MessageDigest.getInstance("SHA-1")
            return digest.digest(script.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
