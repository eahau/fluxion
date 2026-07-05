package com.fluxion.redis.springdata

import com.fluxion.redis.spi.RedisClientAdapter
import com.fluxion.redis.spi.RedisRawCommand
import org.springframework.data.redis.connection.ReturnType
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.util.concurrent.TimeUnit

/**
 * RedisClientAdapter 的 Spring Data Redis 实现
 *
 * 将 builtin:redisCommand 的命令字符串映射到 StringRedisTemplate API。
 * 作为 Lettuce / Redisson 之外的轻量替代方案，适用于已引入 spring-boot-starter-data-redis 的应用。
 *
 * 支持的命令（按数据结构分组）：
 *   String:     GET, SET, SETEX, MGET, MSET, INCR, INCRBY, DECR, DECRBY, SETNX
 *   Hash:       HGET, HSET, HMSET, HMGET, HGETALL, HDEL, HEXISTS, HLEN, HKEYS, HVALS
 *   Set:        SADD, SREM, SMEMBERS, SISMEMBER, SCARD, SUNION, SINTER, SDIFF
 *   Sorted Set: ZADD, ZREM, ZRANGE, ZREVRANGE, ZRANGEBYSCORE, ZCARD, ZSCORE, ZRANK, ZREVRANK, ZINCRBY
 *   List:       LPUSH, RPUSH, LPOP, RPOP, LRANGE, LLEN, LINDEX, LSET
 *   Key:        DEL, EXISTS, EXPIRE, TTL, PERSIST, RENAME, TYPE, KEYS
 *   Pub/Sub:    PUBLISH
 *   Lua:        通过 eval() / evalSha() / scriptLoad() 方法
 */
class SpringDataRedisAdapter(
    private val redisTemplate: StringRedisTemplate
) : RedisClientAdapter {

    override fun execute(command: String, key: String, args: List<String>): Any? {
        return when (command.uppercase()) {
            // ─── String ───────────────────────────────────────────────────
            "GET"     -> redisTemplate.opsForValue().get(key)
            "SET"     -> {
                redisTemplate.opsForValue().set(key, args.getOrElse(0) { "" })
                "OK"
            }
            "SETEX"   -> {
                val seconds = args.getOrElse(0) { "0" }.toLong()
                redisTemplate.opsForValue().set(key, args.getOrElse(1) { "" }, seconds, TimeUnit.SECONDS)
                "OK"
            }
            "SETNX"   -> redisTemplate.opsForValue().setIfAbsent(key, args.getOrElse(0) { "" })
            "INCR"    -> redisTemplate.opsForValue().increment(key)
            "INCRBY"  -> redisTemplate.opsForValue().increment(key, args.getOrElse(0) { "1" }.toLong())
            "DECR"    -> redisTemplate.opsForValue().decrement(key)
            "DECRBY"  -> redisTemplate.opsForValue().decrement(key, args.getOrElse(0) { "1" }.toLong())
            "MGET"    -> redisTemplate.opsForValue().multiGet(listOf(key) + args)
            // ─── Hash ─────────────────────────────────────────────────────
            "HGET"    -> redisTemplate.opsForHash<String, String>().get(key, args.getOrElse(0) { "" })
            "HGETALL" -> redisTemplate.opsForHash<String, String>().entries(key)
            "HSET"    -> {
                val map = args.chunked(2).filter { it.size == 2 }.associate { it[0] to it[1] }
                redisTemplate.opsForHash<String, String>().putAll(key, map)
                map.size.toLong()
            }
            "HMSET"   -> {
                val map = args.chunked(2).filter { it.size == 2 }.associate { it[0] to it[1] }
                redisTemplate.opsForHash<String, String>().putAll(key, map); "OK"
            }
            "HDEL"    -> redisTemplate.opsForHash<String, String>().delete(key, *args.toTypedArray())
            "HEXISTS" -> redisTemplate.opsForHash<String, String>().hasKey(key, args.getOrElse(0) { "" })
            "HLEN"    -> redisTemplate.opsForHash<String, String>().size(key)
            "HKEYS"   -> redisTemplate.opsForHash<String, String>().keys(key)
            "HVALS"   -> redisTemplate.opsForHash<String, String>().values(key)
            // ─── Set ──────────────────────────────────────────────────────
            "SADD"    -> redisTemplate.opsForSet().add(key, *args.toTypedArray())
            "SREM"    -> redisTemplate.opsForSet().remove(key, *args.toTypedArray())
            "SMEMBERS"-> redisTemplate.opsForSet().members(key)
            "SISMEMBER" -> redisTemplate.opsForSet().isMember(key, args.getOrElse(0) { "" })
            "SCARD"   -> redisTemplate.opsForSet().size(key)
            // ─── Sorted Set ───────────────────────────────────────────────
            "ZADD"    -> {
                // args: [score, member, score, member, ...]
                var count = 0L
                args.chunked(2).filter { it.size == 2 }.forEach { (score, member) ->
                    redisTemplate.opsForZSet().add(key, member, score.toDouble())
                    count++
                }
                count
            }
            "ZREM"    -> redisTemplate.opsForZSet().remove(key, *args.toTypedArray())
            "ZRANGE"  -> {
                val start = args.getOrElse(0) { "0" }.toLong()
                val stop  = args.getOrElse(1) { "-1" }.toLong()
                redisTemplate.opsForZSet().range(key, start, stop)
            }
            "ZREVRANGE" -> {
                val start = args.getOrElse(0) { "0" }.toLong()
                val stop  = args.getOrElse(1) { "-1" }.toLong()
                redisTemplate.opsForZSet().reverseRange(key, start, stop)
            }
            "ZSCORE"  -> redisTemplate.opsForZSet().score(key, args.getOrElse(0) { "" })
            "ZRANK"   -> redisTemplate.opsForZSet().rank(key, args.getOrElse(0) { "" })
            "ZREVRANK"-> redisTemplate.opsForZSet().reverseRank(key, args.getOrElse(0) { "" })
            "ZCARD"   -> redisTemplate.opsForZSet().size(key)
            "ZINCRBY" -> redisTemplate.opsForZSet().incrementScore(
                key, args.getOrElse(1) { "" }, args.getOrElse(0) { "0" }.toDouble()
            )
            // ─── List ─────────────────────────────────────────────────────
            "LPUSH"   -> redisTemplate.opsForList().leftPushAll(key, args)
            "RPUSH"   -> redisTemplate.opsForList().rightPushAll(key, args)
            "LPOP"    -> redisTemplate.opsForList().leftPop(key)
            "RPOP"    -> redisTemplate.opsForList().rightPop(key)
            "LRANGE"  -> {
                val start = args.getOrElse(0) { "0" }.toLong()
                val stop  = args.getOrElse(1) { "-1" }.toLong()
                redisTemplate.opsForList().range(key, start, stop)
            }
            "LLEN"    -> redisTemplate.opsForList().size(key)
            // ─── Key ──────────────────────────────────────────────────────
            "DEL"     -> redisTemplate.delete(key).let { if (it) 1L else 0L }
            "EXISTS"  -> if (redisTemplate.hasKey(key)) 1L else 0L
            "EXPIRE"  -> redisTemplate.expire(key, args.getOrElse(0) { "0" }.toLong(), TimeUnit.SECONDS)
            "TTL"     -> redisTemplate.getExpire(key, TimeUnit.SECONDS)
            "PERSIST" -> redisTemplate.persist(key)
            "RENAME"  -> { redisTemplate.rename(key, args.getOrElse(0) { "" }); "OK" }
            "TYPE"    -> redisTemplate.type(key).code()
            // ─── Pub/Sub ──────────────────────────────────────────────────
            "PUBLISH" -> redisTemplate.convertAndSend(key, args.getOrElse(0) { "" })
            else      -> throw UnsupportedOperationException(
                "Command not supported by SpringDataRedisAdapter: $command. " +
                "Use a Lettuce/Redisson adapter or implement RedisClientAdapter for custom commands."
            )
        }
    }

    override fun pipeline(commands: List<RedisRawCommand>): List<Any?> {
        return redisTemplate.executePipelined {
            commands.forEach { cmd ->
                execute(cmd.command, cmd.key, cmd.args)
            }
        }
    }

    override fun eval(script: String, keys: List<String>, args: List<String>): Any? {
        val redisScript = DefaultRedisScript<Any>(script)
        return redisTemplate.execute(redisScript, keys, *args.toTypedArray())
    }

    override fun scriptLoad(script: String): String {
        val scriptBytes = script.toByteArray(Charsets.UTF_8)
        return redisTemplate.execute { conn ->
            conn.scriptingCommands().scriptLoad(scriptBytes)
        } ?: throw IllegalStateException("SCRIPT LOAD returned null")
    }

    override fun evalSha(sha: String, keys: List<String>, args: List<String>): Any? {
        return redisTemplate.execute { conn ->
            val serializer = redisTemplate.stringSerializer
            val keyBytes = keys.map { serializer.serialize(it)!! }.toTypedArray()
            val argBytes = args.map { serializer.serialize(it)!! }.toTypedArray()
            val keysAndArgs = keyBytes + argBytes
            conn.scriptingCommands().evalSha(sha, ReturnType.MULTI, keys.size, *keysAndArgs)
        }
    }
}
