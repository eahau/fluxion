package com.fluxion.redis.spi

/**
 * Pipeline 批量命令描述（值对象）
 */
data class RedisRawCommand(
    /** 命令名称（GET / HSET / ...） */
    val command: String,
    /** Redis Key */
    val key: String,
    /** 命令参数列表 */
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
 * Redis 客户端适配器 SPI — 屏蔽 Lettuce / Jedis / Redisson / Spring Data Redis 差异
 */
interface RedisClientAdapter {

    /**
     * 执行单条 Redis 命令
     */
    fun execute(command: String, key: String, args: List<String>): Any?

    /**
     * Pipeline 批量执行（减少 RTT）
     */
    fun pipeline(commands: List<RedisRawCommand>): List<Any?>

    /**
     * Lua 脚本执行（EVAL）
     */
    fun eval(script: String, keys: List<String>, args: List<String>): Any?

    /**
     * 加载 Lua 脚本到 Redis，返回 SHA1 摘要。
     *
     * 集群环境下应尽可能将脚本分发到所有节点，但调用方仍需处理 NOSCRIPT 回退。
     */
    fun scriptLoad(script: String): String

    /**
     * 通过 SHA1 执行已加载的 Lua 脚本（EVALSHA）。
     *
     * 若服务端不存在该脚本，应抛出包含 "NOSCRIPT" 的异常，由调用方重新加载。
     */
    fun evalSha(sha: String, keys: List<String>, args: List<String>): Any?
}
