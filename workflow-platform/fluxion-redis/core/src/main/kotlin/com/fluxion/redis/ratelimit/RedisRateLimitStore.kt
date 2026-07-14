package com.fluxion.redis.ratelimit

import com.fluxion.core.ratelimit.RateLimitConfig
import com.fluxion.core.ratelimit.RateLimitStore
import com.fluxion.redis.spi.RedisClientAdapter
import org.slf4j.*
import java.security.MessageDigest

/**
 * Redis-backed sliding-window / token-bucket rate-limit store implemented on top of
 * an atomic Lua script.
 *
 * The entire read-state → compute → write-state operation is executed server-side by
 * `rate-limit.lua` for correctness under concurrent access from multiple workers. The
 * implementation is client-agnostic and relies on any [RedisClientAdapter] (Lettuce,
 * Redisson, Spring Data Redis, …).
 *
 * ## Script handling strategy
 *
 * On class init the Lua text is loaded from classpath (`/rate-limit.lua`) and its
 * SHA1 digest is computed locally. At runtime the fast path calls `EVALSHA`; if the
 * server answers with a `NOSCRIPT` error the store falls back to a full `EVAL` and
 * the next call re-enters the fast path. In clustered deployments this behavior
 * tolerates replicas / new shards that never received the original `SCRIPT LOAD`.
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

    // Prefer EVALSHA → on explicit NOSCRIPT, transparently retry with the full script.
    // Any other exception propagates to the caller (see the caller's allow-fallback).
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

    // Walk the exception cause chain and look for the literal NOSCRIPT token that every
    // Redis dialect (Lettuce, Jedis, Redisson, Spring Data) eventually surfaces inside
    // the error message when a script SHA is unknown to the server.
    private fun isNoScriptError(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t.message?.contains("NOSCRIPT", ignoreCase = true) == true) return true
            t = t.cause
        }
        return false
    }

    // Different adapters return script results as Boolean / Long / Int / List<*> / "1"
    // string; normalize to a plain true/false at the store boundary.
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
                ?: throw IllegalStateException("Rate limit Lua script not found in classpath: `$RESOURCE_PATH")
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

        private fun computeSha1(script: String): String {
            val digest = MessageDigest.getInstance("SHA-1")
            return digest.digest(script.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
