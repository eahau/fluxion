package com.fluxion.redis.meta

import com.fluxion.core.util.JsonUtil
import com.fluxion.core.value.FunctionMeta

/**
 * Declarative catalog of Redis built-in function metadata.
 *
 * Parameter signatures are derived from the Redis CLI grammar (see
 * https://redis.io/commands/) and expressed as a small alphabet of short tags
 * (`K`, `KV`, `K_FV`, ...). Each tag is then expanded into a full JSON Schema
 * property description. The frontend consumes the emitted `enum`, `x-commands`,
 * `category`, `x-widget`, and `x-i18n` metadata to render a zero-code visual
 * parameter editor with searchable dropdowns, per-category command groups, and
 * bilingual tooltips.
 */
object RedisFunctionMetas {

    private val COMMANDS = listOf(
        "APPEND" to "KV", "DECR" to "K", "DECRBY" to "K_N", "GET" to "K",
        "GETDEL" to "K", "GETEX" to "KV", "GETRANGE" to "K_R", "GETSET" to "KV",
        "INCR" to "K", "INCRBY" to "K_N", "INCRBYFLOAT" to "K_N", "LCS" to "KV",
        "MGET" to "KV", "MSET" to "KV", "MSETNX" to "KV",
        "PSETEX" to "K_N", "SET" to "KV", "SETEX" to "K_N", "SETNX" to "KV",
        "SETRANGE" to "K_N", "STRLEN" to "K", "SUBSTR" to "K_R",
        "BLMOVE" to "KV", "BLMPOP" to "KV", "BLPOP" to "K_N", "BRPOP" to "K_N",
        "BRPOPLPUSH" to "KV", "LINDEX" to "K_N", "LINSERT" to "KV",
        "LLEN" to "K", "LMOVE" to "KV", "LMPOP" to "KV",
        "LPOP" to "K", "LPOS" to "KV", "LPUSH" to "KV", "LPUSHX" to "KV",
        "LRANGE" to "K_R", "LREM" to "K_N", "LSET" to "K_N", "LTRIM" to "K_R",
        "RPOP" to "K", "RPOPLPUSH" to "KV", "RPUSH" to "KV", "RPUSHX" to "KV",
        "SADD" to "KV", "SCARD" to "K", "SDIFF" to "D", "SDIFFSTORE" to "D",
        "SINTER" to "D", "SINTERCARD" to "D", "SINTERSTORE" to "D",
        "SISMEMBER" to "KV", "SMEMBERS" to "K", "SMISMEMBER" to "KV",
        "SMOVE" to "KV", "SORT" to "K", "SPOP" to "K", "SRANDMEMBER" to "K",
        "SREM" to "KV", "SUNION" to "D", "SUNIONSTORE" to "D",
        "ZADD" to "K_SM", "ZCARD" to "K", "ZCOUNT" to "K_R", "ZDIFF" to "D",
        "ZDIFFSTORE" to "D", "ZINCRBY" to "K_N", "ZINTER" to "D", "ZINTERCARD" to "D",
        "ZINTERSTORE" to "D", "ZLEXCOUNT" to "K_R", "ZMPOP" to "KV", "ZMSCORE" to "KV",
        "ZPOPMAX" to "K", "ZPOPMIN" to "K", "ZRANDMEMBER" to "K", "ZRANGE" to "K_R",
        "ZRANGEBYLEX" to "K_R", "ZRANGEBYSCORE" to "K_R", "ZRANGESTORE" to "D",
        "ZRANK" to "KV", "ZREM" to "KV", "ZREMRANGEBYLEX" to "K_R",
        "ZREMRANGEBYRANK" to "K_R", "ZREMRANGEBYSCORE" to "K_R",
        "ZREVRANGE" to "K_R", "ZREVRANGEBYLEX" to "K_R", "ZREVRANGEBYSCORE" to "K_R",
        "ZREVRANK" to "KV", "ZSCAN" to "K", "ZSCORE" to "KV", "ZUNION" to "D", "ZUNIONSTORE" to "D",
        "HDEL" to "K_F", "HEXISTS" to "K_F", "HGET" to "K_F", "HGETALL" to "K",
        "HINCRBY" to "K_N", "HINCRBYFLOAT" to "K_N", "HKEYS" to "K", "HLEN" to "K",
        "HMGET" to "K_F", "HMSET" to "K_FV", "HRANDFIELD" to "K",
        "HSCAN" to "K", "HSET" to "K_FV", "HSETNX" to "K_FV", "HVALS" to "K",
        "COPY" to "KV", "DEL" to "K", "DUMP" to "K", "EXISTS" to "K",
        "EXPIRE" to "K_N", "EXPIREAT" to "K_T", "EXPIRETIME" to "K",
        "KEYS" to "P", "OBJECT" to "KV", "PEXPIRE" to "K_N", "PEXPIREAT" to "K_T",
        "PEXPIRETIME" to "K", "PTTL" to "K", "RANDOMKEY" to "N", "RENAME" to "KV",
        "RENAMENX" to "KV", "RESTORE" to "KV", "SCAN" to "K", "SORT_RO" to "K",
        "TOUCH" to "KV", "TTL" to "K", "TYPE" to "K", "UNLINK" to "K", "WAIT" to "N",
        "BITCOUNT" to "K", "BITFIELD" to "KV", "BITFIELD_RO" to "K", "BITOP" to "D",
        "BITPOS" to "K", "GETBIT" to "K_N", "SETBIT" to "K_N",
        "PFADD" to "KV", "PFCOUNT" to "KV", "PFMERGE" to "D",
        "GEOADD" to "KV", "GEODIST" to "KV", "GEOHASH" to "KV", "GEOPOS" to "KV",
        "GEORADIUS" to "KV", "GEORADIUSBYMEMBER" to "KV", "GEOSEARCH" to "KV", "GEOSEARCHSTORE" to "KV",
        "XACK" to "KV", "XADD" to "KV", "XAUTOCLAIM" to "KV", "XCLAIM" to "KV",
        "XDEL" to "KV", "XGROUP" to "KV", "XINFO" to "KV", "XLEN" to "K",
        "XPENDING" to "KV", "XRANGE" to "KV", "XREAD" to "KV", "XREADGROUP" to "KV",
        "XREVRANGE" to "KV", "XTRIM" to "KV",
        "EVAL" to "S", "EVALSHA" to "S", "EVALSHA_RO" to "S", "EVAL_RO" to "S",
        "PUBLISH" to "KV",
        "DBSIZE" to "N", "ECHO" to "KV", "FLUSHALL" to "N", "FLUSHDB" to "N",
        "INFO" to "N", "PING" to "N", "SELECT" to "N", "TIME" to "N",
        "PIPELINE" to "X",
    )

    private val categoryOf = mapOf(
        "APPEND" to "string", "DECR" to "string", "DECRBY" to "string", "GET" to "string",
        "GETDEL" to "string", "GETEX" to "string", "GETRANGE" to "string", "GETSET" to "string",
        "INCR" to "string", "INCRBY" to "string", "INCRBYFLOAT" to "string", "LCS" to "string",
        "MGET" to "string", "MSET" to "string", "MSETNX" to "string",
        "PSETEX" to "string", "SET" to "string", "SETEX" to "string", "SETNX" to "string",
        "SETRANGE" to "string", "STRLEN" to "string", "SUBSTR" to "string",
        "BLMOVE" to "list", "BLMPOP" to "list", "BLPOP" to "list", "BRPOP" to "list",
        "BRPOPLPUSH" to "list", "LINDEX" to "list", "LINSERT" to "list",
        "LLEN" to "list", "LMOVE" to "list", "LMPOP" to "list",
        "LPOP" to "list", "LPOS" to "list", "LPUSH" to "list", "LPUSHX" to "list",
        "LRANGE" to "list", "LREM" to "list", "LSET" to "list", "LTRIM" to "list",
        "RPOP" to "list", "RPOPLPUSH" to "list", "RPUSH" to "list", "RPUSHX" to "list",
        "SADD" to "set", "SCARD" to "set", "SDIFF" to "set", "SDIFFSTORE" to "set",
        "SINTER" to "set", "SINTERCARD" to "set", "SINTERSTORE" to "set",
        "SISMEMBER" to "set", "SMEMBERS" to "set", "SMISMEMBER" to "set",
        "SMOVE" to "set", "SORT" to "set", "SPOP" to "set", "SRANDMEMBER" to "set",
        "SREM" to "set", "SUNION" to "set", "SUNIONSTORE" to "set",
        "ZADD" to "zset", "ZCARD" to "zset", "ZCOUNT" to "zset", "ZDIFF" to "zset",
        "ZDIFFSTORE" to "zset", "ZINCRBY" to "zset", "ZINTER" to "zset", "ZINTERCARD" to "zset",
        "ZINTERSTORE" to "zset", "ZLEXCOUNT" to "zset", "ZMPOP" to "zset", "ZMSCORE" to "zset",
        "ZPOPMAX" to "zset", "ZPOPMIN" to "zset", "ZRANDMEMBER" to "zset", "ZRANGE" to "zset",
        "ZRANGEBYLEX" to "zset", "ZRANGEBYSCORE" to "zset", "ZRANGESTORE" to "zset",
        "ZRANK" to "zset", "ZREM" to "zset", "ZREMRANGEBYLEX" to "zset",
        "ZREMRANGEBYRANK" to "zset", "ZREMRANGEBYSCORE" to "zset",
        "ZREVRANGE" to "zset", "ZREVRANGEBYLEX" to "zset", "ZREVRANGEBYSCORE" to "zset",
        "ZREVRANK" to "zset", "ZSCAN" to "zset", "ZSCORE" to "zset", "ZUNION" to "zset", "ZUNIONSTORE" to "zset",
        "HDEL" to "hash", "HEXISTS" to "hash", "HGET" to "hash", "HGETALL" to "hash",
        "HINCRBY" to "hash", "HINCRBYFLOAT" to "hash", "HKEYS" to "hash", "HLEN" to "hash",
        "HMGET" to "hash", "HMSET" to "hash", "HRANDFIELD" to "hash",
        "HSCAN" to "hash", "HSET" to "hash", "HSETNX" to "hash", "HVALS" to "hash",
        "COPY" to "key", "DEL" to "key", "DUMP" to "key", "EXISTS" to "key",
        "EXPIRE" to "key", "EXPIREAT" to "key", "EXPIRETIME" to "key",
        "KEYS" to "key", "OBJECT" to "key", "PEXPIRE" to "key", "PEXPIREAT" to "key",
        "PEXPIRETIME" to "key", "PTTL" to "key", "RANDOMKEY" to "key", "RENAME" to "key",
        "RENAMENX" to "key", "RESTORE" to "key", "SCAN" to "key", "SORT_RO" to "key",
        "TOUCH" to "key", "TTL" to "key", "TYPE" to "key", "UNLINK" to "key", "WAIT" to "key",
        "BITCOUNT" to "bitmap", "BITFIELD" to "bitmap", "BITFIELD_RO" to "bitmap",
        "BITOP" to "bitmap", "BITPOS" to "bitmap", "GETBIT" to "bitmap", "SETBIT" to "bitmap",
        "PFADD" to "hyperloglog", "PFCOUNT" to "hyperloglog", "PFMERGE" to "hyperloglog",
        "GEOADD" to "geo", "GEODIST" to "geo", "GEOHASH" to "geo", "GEOPOS" to "geo",
        "GEORADIUS" to "geo", "GEORADIUSBYMEMBER" to "geo", "GEOSEARCH" to "geo", "GEOSEARCHSTORE" to "geo",
        "XACK" to "stream", "XADD" to "stream", "XAUTOCLAIM" to "stream", "XCLAIM" to "stream",
        "XDEL" to "stream", "XGROUP" to "stream", "XINFO" to "stream", "XLEN" to "stream",
        "XPENDING" to "stream", "XRANGE" to "stream", "XREAD" to "stream", "XREADGROUP" to "stream",
        "XREVRANGE" to "stream", "XTRIM" to "stream",
        "EVAL" to "scripting", "EVALSHA" to "scripting", "EVALSHA_RO" to "scripting", "EVAL_RO" to "scripting",
        "PUBLISH" to "pubsub",
        "DBSIZE" to "server", "ECHO" to "server", "FLUSHALL" to "server", "FLUSHDB" to "server",
        "INFO" to "server", "PING" to "server", "SELECT" to "server", "TIME" to "server",
        "PIPELINE" to "special",
    )

    private fun i18n(zh: String, en: String) = mapOf(
        "x-i18n" to mapOf("zh" to mapOf("description" to zh), "en" to mapOf("description" to en))
    )

    private val BINDING = mapOf("type" to listOf("string", "object"), "x-widget" to "binding")

    private val xCommands = COMMANDS.associate { (name, args) ->
        name to mapOf("args" to args, "category" to (categoryOf[name] ?: "special"))
    }

    private val COMMAND_ITEM_PROPS = mapOf(
        "command" to mapOf(
            "type" to "string", "description" to "Redis command",
            "enum" to COMMANDS.map { it.first },
            "x-commands" to xCommands,
            "x-widget" to "searchableSelect",
        ) + i18n("Redis Command", "Redis Command"),
        "key" to BINDING + mapOf("description" to "Redis key (supports \${var} templates or structured binding)")
            + i18n("Redis Key (supports \${var} template interpolation or structured binding)",
                   "Redis Key (supports \${var} template interpolation or structured binding)"),
        "args" to mapOf(
            "type" to "array", "items" to BINDING,
            "description" to "Command argument list (supports structured binding)",
        ) + i18n("Command arguments (supports structured binding)",
                 "Command arguments (supports structured binding)"),
    )

    private val schemaJson = JsonUtil.serialize(
        mapOf(
            "type" to "object",
            "required" to emptyList<String>(),
            "properties" to (COMMAND_ITEM_PROPS + mapOf(
                "raw" to mapOf(
                    "type" to "string",
                    "description" to "Raw Redis CLI line (e.g. SET user:1 name John; \${var} template interpolation supported)",
                    "x-widget" to "textarea",
                ) + i18n(
                    "Raw Redis CLI command (e.g. SET user:1 name John; supports \${var} interpolation)",
                    "Raw Redis CLI command line (e.g. SET user:1 name John; supports \${var} template interpolation)"
                ),
                "script" to mapOf("type" to "string", "description" to "Lua script body (for EVAL command only)")
                    + i18n("Lua script (EVAL command only)", "Lua script (EVAL command only)"),
                "keys" to mapOf("type" to "array", "items" to BINDING, "description" to "EVAL KEYS array")
                    + i18n("EVAL KEYS array", "EVAL KEYS arguments"),
                "commands" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "object", "properties" to COMMAND_ITEM_PROPS),
                    "description" to "PIPELINE sub-command list",
                ) + i18n("PIPELINE command list", "PIPELINE command list"),
            )),
        )
    )

    @JvmField
    val REDIS_COMMAND = FunctionMeta.builder("builtin:redisCommand")
        .description("Generic Redis command executor covering all structures & commands via the pluggable RedisClientAdapter SPI")
        .descriptions(mapOf(
            "zh" to "Generic Redis command executor, supporting all data structures and commands (decoupled via RedisClientAdapter SPI)",
            "en" to "Generic Redis command executor, supporting all data structures and commands (decoupled via RedisClientAdapter SPI)",
        ))
        .domain("redis")
        .paramSchema(schemaJson)
        .build()
}