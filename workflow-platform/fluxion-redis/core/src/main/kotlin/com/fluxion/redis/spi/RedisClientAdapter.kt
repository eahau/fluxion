package com.fluxion.redis.spi

/**
 * Value object that describes a single raw Redis command inside a pipeline batch.
 *
 * @property command uppercase Redis verb (GET / HSET / ...).
 * @property key   primary Redis key the command operates on.
 * @property args  positional command arguments following the key.
 */
data class RedisRawCommand(
    val command: String,
    val key: String,
    val args: List<String> = emptyList()
) {
    companion object {
        @JvmStatic
        fun of(command: String, key: String, args: List<String>?): RedisRawCommand =
            RedisRawCommand(command, key, args ?: emptyList())

        @JvmStatic
        fun of(command: String, key: String): RedisRawCommand =
            RedisRawCommand(command, key, emptyList())
    }
}

/**
 * Service Provider Interface (SPI) that abstracts over concrete Redis client libraries
 * (Lettuce, Redisson, Spring Data Redis, Jedis, ...).
 *
 * Every adapter is expected to behave as a light stateless facade; the higher-level
 * `RedisCommandFunction` and `RedisRateLimitStore` do not need to know which client
 * is wired at runtime. Implementations only need to support the small command surface
 * listed below; anything outside it should be routed through `eval(..)` with a Lua
 * script.
 */
interface RedisClientAdapter {

    /** Execute a single synchronous Redis command and return the raw response. */
    fun execute(command: String, key: String, args: List<String>): Any?

    /**
     * Execute a batch of commands in a single pipeline round-trip to minimize RTT.
     *
     * The returned list has the same size as `commands`; indices match 1:1. Per-command
     * failures must return `null` at their slot instead of propagating the exception up.
     */
    fun pipeline(commands: List<RedisRawCommand>): List<Any?>

    /** Run a Lua script server-side via `EVAL`. */
    fun eval(script: String, keys: List<String>, args: List<String>): Any?

    /**
     * Upload a Lua script to the Redis server (or servers in a cluster) and return
     * its SHA1 digest for later use with `evalSha`.
     *
     * In clustered deployments the caller must still tolerate `NOSCRIPT` errors from
     * replica / shard nodes that did not receive the script upload.
     */
    fun scriptLoad(script: String): String

    /**
     * Invoke a previously uploaded script via `EVALSHA`.
     *
     * If the server does not recognize `sha` the adapter MUST surface an exception
     * whose message (or nested message) contains the literal token `NOSCRIPT` so
     * callers can transparently fall back to a full `eval(..)` re-upload.
     */
    fun evalSha(sha: String, keys: List<String>, args: List<String>): Any?
}
