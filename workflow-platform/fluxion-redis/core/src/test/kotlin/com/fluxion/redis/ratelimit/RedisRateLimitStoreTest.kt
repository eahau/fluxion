package com.fluxion.redis.ratelimit

import com.fluxion.core.ratelimit.RateLimitConfig
import com.fluxion.redis.spi.RedisClientAdapter
import com.fluxion.redis.spi.RedisRawCommand
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.security.MessageDigest

/**
 * Unit tests for [RedisRateLimitStore].
 *
 * Verifies the EVALSHA fast-path, NOSCRIPT transparent re-upload fallback,
 * multi-type result normalization (Boolean/Long/List), and fail-open
 * behaviour when the Redis server is completely down.
 */
class RedisRateLimitStoreTest {

    private val config = RateLimitConfig(upLimited = 6, cdSeconds = 10)

    @Test
    fun `should use EVALSHA with locally computed SHA`() {
        val adapter = CapturingRedisAdapter(1L)
        val store = RedisRateLimitStore(adapter)

        assertTrue(store.tryAcquire("ratelimit:test", config))

        val expectedSha = computeSha1(RedisRateLimitStore.LUA_SCRIPT)
        assertEquals(expectedSha, adapter.lastSha)
        assertNull(adapter.lastScript)
        assertEquals(listOf("ratelimit:test"), adapter.lastKeys)
        assertEquals(config.upLimited.toString(), adapter.lastArgs[0])
        assertEquals(config.cdSeconds.toString(), adapter.lastArgs[1])
        assertEquals(config.recoveryPerCd.toString(), adapter.lastArgs[2])
        assertEquals("60", adapter.lastArgs[3]) // upLimited * cdSeconds
        assertNotNull(adapter.lastArgs[4].toLongOrNull())
    }

    @Test
    fun `should fallback to EVAL on NOSCRIPT`() {
        val adapter = CapturingRedisAdapter(1L, noscriptCount = 1)
        val store = RedisRateLimitStore(adapter)

        assertTrue(store.tryAcquire("ratelimit:test", config))

        assertEquals(1, adapter.evalShaCount)
        assertEquals(1, adapter.evalCount)
        assertNotNull(adapter.lastScript)
        assertTrue(adapter.lastScript!!.contains("redis.call('GET'"))
    }

    @Test
    fun `should parse various result types`() {
        assertTrue(RedisRateLimitStore(CapturingRedisAdapter(1L)).tryAcquire("k", config))
        assertTrue(RedisRateLimitStore(CapturingRedisAdapter(true)).tryAcquire("k", config))
        assertTrue(RedisRateLimitStore(CapturingRedisAdapter(listOf(1L))).tryAcquire("k", config))
        assertFalse(RedisRateLimitStore(CapturingRedisAdapter(0L)).tryAcquire("k", config))
        assertFalse(RedisRateLimitStore(CapturingRedisAdapter(listOf(0L))).tryAcquire("k", config))
    }

    @Test
    fun `should fallback to allow on eval failure`() {
        val failingAdapter = object : RedisClientAdapter {
            override fun execute(command: String, key: String, args: List<String>): Any? = null
            override fun pipeline(commands: List<RedisRawCommand>): List<Any?> = emptyList()
            override fun eval(script: String, keys: List<String>, args: List<String>): Any? =
                throw RuntimeException("Redis down")
            override fun scriptLoad(script: String): String = throw RuntimeException("Redis down")
            override fun evalSha(sha: String, keys: List<String>, args: List<String>): Any? =
                throw RuntimeException("Redis down")
        }
        assertTrue(RedisRateLimitStore(failingAdapter).tryAcquire("k", config))
    }

    private class CapturingRedisAdapter(
        private val result: Any?,
        private val noscriptCount: Int = 0
    ) : RedisClientAdapter {
        var lastScript: String? = null
        var lastSha: String? = null
        var lastKeys: List<String> = emptyList()
        var lastArgs: List<String> = emptyList()
        var evalCount = 0
        var evalShaCount = 0

        override fun execute(command: String, key: String, args: List<String>): Any? = null
        override fun pipeline(commands: List<RedisRawCommand>): List<Any?> = emptyList()

        override fun eval(script: String, keys: List<String>, args: List<String>): Any? {
            evalCount++
            lastScript = script
            lastKeys = keys
            lastArgs = args
            return result
        }

        override fun scriptLoad(script: String): String = computeSha1(script)

        override fun evalSha(sha: String, keys: List<String>, args: List<String>): Any? {
            evalShaCount++
            if (evalShaCount <= noscriptCount) {
                throw RuntimeException("ERR NOSCRIPT No matching script")
            }
            lastSha = sha
            lastKeys = keys
            lastArgs = args
            return result
        }
    }

}

private fun computeSha1(script: String): String {
    val digest = MessageDigest.getInstance("SHA-1")
    return digest.digest(script.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
