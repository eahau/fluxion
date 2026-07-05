package com.fluxion.redis.lettuce

import com.fluxion.redis.spi.RedisClientAdapter
import com.fluxion.redis.spi.RedisRawCommand
import io.lettuce.core.AbstractRedisAsyncCommands
import io.lettuce.core.RedisFuture
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.BooleanOutput
import io.lettuce.core.output.CommandOutput
import io.lettuce.core.output.DoubleOutput
import io.lettuce.core.output.IntegerOutput
import io.lettuce.core.output.KeyListOutput
import io.lettuce.core.output.MapOutput
import io.lettuce.core.output.ScoredValueOutput
import io.lettuce.core.output.StatusOutput
import io.lettuce.core.output.ValueListOutput
import io.lettuce.core.output.ValueOutput
import io.lettuce.core.protocol.CommandArgs
import io.lettuce.core.protocol.CommandType
import org.slf4j.LoggerFactory
import org.slf4j.debug
import org.slf4j.warn
import java.util.concurrent.TimeUnit

/**
 * Lettuce 实现的 RedisClientAdapter
 *
 * execute / pipeline 均通过 dispatch() API 发送原始协议命令，
 * 避免按命令名逐分支调用 Lettuce typed API 导致的映射逻辑重复。
 */
class LettuceRedisAdapter(
    private val connection: StatefulRedisConnection<String, String>
) : RedisClientAdapter {

    private val log = LoggerFactory.getLogger(javaClass)

    // ─── 同步命令 ──────────────────────────────────────────────────────────

    override fun execute(command: String, key: String, args: List<String>): Any? {
        val cmdName = command.uppercase()
        log.debug { "lettuce execute: $cmdName key=[$key] args=$args" }

        val cmdType = try {
            CommandType.valueOf(cmdName)
        } catch (_: IllegalArgumentException) {
            throw UnsupportedOperationException(
                "Unsupported Redis command: [$command]. For complex commands, use EVAL with Lua script."
            )
        }

        val cmdArgs = CommandArgs(StringCodec.UTF8).addKey(key)
        args.forEach { cmdArgs.add(it) }
        val output = resolveOutput(cmdType)

        return try {
            connection.sync().dispatch(cmdType, output, cmdArgs)
        } catch (e: Exception) {
            log.warn(e) { "execute $cmdName failed: ${e.message}" }
            throw e
        }
    }

    // ─── Pipeline ─────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    override fun pipeline(commands: List<RedisRawCommand>): List<Any?> {
        val async = connection.async() as AbstractRedisAsyncCommands<String, String>
        async.setAutoFlushCommands(false)
        val futures = mutableListOf<RedisFuture<*>>()

        try {
            for (rawCmd in commands) {
                val cmdType = CommandType.valueOf(rawCmd.command.uppercase())
                val cmdArgs = CommandArgs(StringCodec.UTF8).addKey(rawCmd.key)
                rawCmd.args.forEach { cmdArgs.add(it) }
                val future = async.dispatch(cmdType, resolveOutput(cmdType), cmdArgs)
                futures.add(future)
            }
        } finally {
            async.flushCommands()
            async.setAutoFlushCommands(true)
        }

        return futures.mapIndexed { i, future ->
            try {
                future.get(10, TimeUnit.SECONDS)
            } catch (e: Exception) {
                log.warn(e) { "Pipeline command[$i] failed: ${e.message}" }
                null
            }
        }
    }

    // ─── Lua EVAL ─────────────────────────────────────────────────────────

    override fun eval(script: String, keys: List<String>, args: List<String>): Any? {
        val keysArr = keys.toTypedArray()
        val argsArr = args.toTypedArray()
        log.debug { "lettuce EVAL keys=$keys args=$args" }
        return connection.sync().eval(script, ScriptOutputType.OBJECT, keysArr, *argsArr)
    }

    override fun scriptLoad(script: String): String {
        log.debug { "lettuce SCRIPT LOAD" }
        return connection.sync().scriptLoad(script)
    }

    override fun evalSha(sha: String, keys: List<String>, args: List<String>): Any? {
        val keysArr = keys.toTypedArray()
        val argsArr = args.toTypedArray()
        log.debug { "lettuce EVALSHA sha=$sha keys=$keys args=$args" }
        return connection.sync().evalsha(sha, ScriptOutputType.OBJECT, keysArr, *argsArr)
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────

    /**
     * 按命令类型解析对应的 CommandOutput，
     * 确保 Redis 协议层响应被正确解析为 Java 类型。
     *
     * | Output 类型         | Java 返回类型                | 命令                                                                  |
     * |---------------------|-----------------------------|----------------------------------------------------------------------|
     * | StatusOutput        | "OK"                        | SET, SETEX, PSETEX, LSET, LTRIM, HMSET, LINSERT, RENAME, COPY, PERSIST |
     * | ValueOutput         | String?                     | GET, GETSET, HGET, LPOP, RPOP, LINDEX, SRANDMEMBER, SPOP, TYPE, OBJECT, PUBLISH, GETRANGE |
     * | IntegerOutput       | Long                        | DEL, UNLINK, EXPIRE, PEXPIRE, TTL, PTTL, INCR, INCRBY, DECR, DECRBY, APPEND, STRLEN, SETRANGE, LPUSH, RPUSH, LPUSHX, RPUSHX, LLEN, LREM, SADD, SREM, SCARD, ZADD, ZREM, ZRANK, ZREVRANK, ZCARD, ZCOUNT, ZINCRBY, HDEL, HLEN, HSET, XLEN |
     * | BooleanOutput       | Boolean                     | EXISTS, SETNX, HEXISTS, HSETNX, SISMEMBER                            |
     * | DoubleOutput        | Double                      | INCRBYFLOAT, HINCRBYFLOAT                                            |
     * | MapOutput           | Map<String, String>         | HGETALL                                                              |
     * | KeyListOutput       | List<String>                | KEYS                                                                 |
     * | ValueListOutput     | List<String>                | HMGET, HKEYS, HVALS, LRANGE, ZRANGE, ZREVRANGE, ZRANGEBYSCORE, ZREVRANGEBYSCORE, SMEMBERS |
     * | ScoredValueOutput   | ScoredValue<String>         | ZSCORE, ZPOPMIN, ZPOPMAX                                             |
     */
    private fun resolveOutput(cmdType: CommandType): CommandOutput<String, String, *> {
        val codec = StringCodec.UTF8
        return when (cmdType) {
            // ── Status ("OK") ──────────────────────────────────────────────
            CommandType.SET, CommandType.SETEX, CommandType.PSETEX,
            CommandType.LSET, CommandType.LTRIM, CommandType.HMSET,
            CommandType.LINSERT,
            CommandType.RENAME, CommandType.COPY, CommandType.PERSIST
                -> StatusOutput(codec)

            // ── Value (String?) ────────────────────────────────────────────
            CommandType.GET, CommandType.GETSET, CommandType.GETRANGE,
            CommandType.HGET,
            CommandType.LPOP, CommandType.RPOP, CommandType.LINDEX,
            CommandType.SRANDMEMBER, CommandType.SPOP,
            CommandType.TYPE, CommandType.OBJECT,
            CommandType.PUBLISH
                -> ValueOutput(codec)

            // ── Integer (Long) ─────────────────────────────────────────────
            CommandType.DEL, CommandType.UNLINK,
            CommandType.EXPIRE, CommandType.PEXPIRE,
            CommandType.TTL, CommandType.PTTL,
            CommandType.INCR, CommandType.INCRBY,
            CommandType.DECR, CommandType.DECRBY,
            CommandType.APPEND, CommandType.STRLEN, CommandType.SETRANGE,
            CommandType.LPUSH, CommandType.RPUSH,
            CommandType.LPUSHX, CommandType.RPUSHX,
            CommandType.LLEN, CommandType.LREM,
            CommandType.SADD, CommandType.SREM, CommandType.SCARD,
            CommandType.ZADD, CommandType.ZREM,
            CommandType.ZRANK, CommandType.ZREVRANK,
            CommandType.ZCARD, CommandType.ZCOUNT, CommandType.ZINCRBY,
            CommandType.HDEL, CommandType.HLEN, CommandType.HSET,
            CommandType.XLEN
                -> IntegerOutput(codec)

            // ── Boolean ────────────────────────────────────────────────────
            CommandType.EXISTS, CommandType.SETNX,
            CommandType.HEXISTS, CommandType.HSETNX,
            CommandType.SISMEMBER
                -> BooleanOutput(codec)

            // ── Double ─────────────────────────────────────────────────────
            CommandType.INCRBYFLOAT, CommandType.HINCRBYFLOAT
                -> DoubleOutput(codec)

            // ── Map ────────────────────────────────────────────────────────
            CommandType.HGETALL -> MapOutput(codec)

            // ── Key List ───────────────────────────────────────────────────
            CommandType.KEYS -> KeyListOutput(codec)

            // ── Value List ─────────────────────────────────────────────────
            CommandType.HMGET, CommandType.HKEYS, CommandType.HVALS,
            CommandType.LRANGE,
            CommandType.ZRANGE, CommandType.ZREVRANGE,
            CommandType.ZRANGEBYSCORE, CommandType.ZREVRANGEBYSCORE,
            CommandType.SMEMBERS
                -> ValueListOutput(codec)

            // ── Scored Value ───────────────────────────────────────────────
            CommandType.ZSCORE, CommandType.ZPOPMIN, CommandType.ZPOPMAX
                -> ScoredValueOutput(codec)

            else -> throw UnsupportedOperationException(
                "Unsupported Redis command: $cmdType. For complex commands, use EVAL with Lua script."
            )
        }
    }
}
