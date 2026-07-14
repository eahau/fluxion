package com.fluxion.redis.redisson

import com.fluxion.redis.spi.RedisClientAdapter
import com.fluxion.redis.spi.RedisRawCommand
import org.redisson.api.RBatch
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.slf4j.*
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Redisson-based implementation of [RedisClientAdapter].
 *
 * Implements each supported Redis command by delegating to Redisson's typed reactive
 * objects (RBucket, RMap, RDeque, RSet, RScoredSortedSet, …). Commands that are not
 * explicitly handled throw [UnsupportedOperationException] and callers are expected to
 * fall back to EVAL with a Lua script for edge cases.
 *
 * Pipeline execution uses Redisson's [RBatch] API. Commands that lack a direct async
 * batch counterpart are executed sequentially as a fallback after the batch flush.
 */
class RedissonRedisAdapter(
    private val client: RedissonClient
) : RedisClientAdapter {

    private val log = LoggerFactory.getLogger(javaClass)

    // ─────────────────────────────────────────────────────────────────────
    // Synchronous single-command execution
    // ─────────────────────────────────────────────────────────────────────

    override fun execute(command: String, key: String, args: List<String>): Any? {
        log.debug { "redisson execute: $command key=[$key] args=$args" }

        return when (command.uppercase()) {
            // ─── String (RBucket) ──────────────────────────────────────
            "GET" -> client.getBucket<String>(key).get()

            "SET" -> {
                val bucket = client.getBucket<String>(key)
                if (args.size >= 3) {
                    val opt = args[1].uppercase()
                    val ttl = args[2].toLong()
                    if (opt == "EX") { bucket.set(args[0], Duration.ofSeconds(ttl)); return "OK" }
                    if (opt == "PX") { bucket.set(args[0], Duration.ofMillis(ttl)); return "OK" }
                }
                bucket.set(if (args.isEmpty()) "" else args[0])
                "OK"
            }

            "SETEX" -> {
                client.getBucket<String>(key).set(arg(args, 1), Duration.ofSeconds(arg(args, 0).toLong()))
                "OK"
            }

            "PSETEX" -> {
                client.getBucket<String>(key).set(arg(args, 1), Duration.ofMillis(arg(args, 0).toLong()))
                "OK"
            }

            "SETNX" -> client.getBucket<String>(key).setIfAbsent(arg(args, 0))

            "GETSET" -> {
                val bucket = client.getBucket<String>(key)
                val old = bucket.get()
                bucket.set(arg(args, 0))
                old
            }

            "DEL" -> if (client.getBucket<Any>(key).delete()) 1L else 0L
            "EXISTS" -> if (client.getBucket<Any>(key).isExists) 1L else 0L

            "EXPIRE" -> {
                val ttl = arg(args, 0).toLong()
                client.getBucket<Any>(key).expire(Duration.ofSeconds(ttl))
            }

            "PEXPIRE" -> {
                val ttl = arg(args, 0).toLong()
                client.getBucket<Any>(key).expire(Duration.ofMillis(ttl))
            }

            "PERSIST" -> client.getBucket<Any>(key).clearExpire()

            "TTL" -> {
                val ms = client.getBucket<Any>(key).remainTimeToLive()
                if (ms < 0) ms else ms / 1000
            }

            "PTTL" -> client.getBucket<Any>(key).remainTimeToLive()

            "INCR" -> client.getAtomicLong(key).incrementAndGet()
            "INCRBY" -> client.getAtomicLong(key).addAndGet(arg(args, 0).toLong())
            "DECR" -> client.getAtomicLong(key).decrementAndGet()
            "DECRBY" -> client.getAtomicLong(key).addAndGet(-arg(args, 0).toLong())
            "INCRBYFLOAT" -> client.getAtomicDouble(key).addAndGet(arg(args, 0).toDouble())

            "APPEND" -> {
                val bucket = client.getBucket<String>(key)
                val current = bucket.get() ?: ""
                val appended = current + arg(args, 0)
                bucket.set(appended)
                appended.length.toLong()
            }

            "STRLEN" -> {
                val v = client.getBucket<String>(key).get()
                v?.length?.toLong() ?: 0L
            }

            // ─── Hash (RMap) ────────────────────────────────────────────
            "HGET" -> client.getMap<String, String>(key)[arg(args, 0)]

            "HSET" -> {
                client.getMap<String, String>(key).fastPut(arg(args, 0), arg(args, 1))
                1L
            }

            "HSETNX" -> client.getMap<String, String>(key).fastPutIfAbsent(arg(args, 0), arg(args, 1))

            "HMSET" -> {
                val map = LinkedHashMap<String, String>()
                var i = 0
                while (i + 1 < args.size) { map[args[i]] = args[i + 1]; i += 2 }
                client.getMap<String, String>(key).putAll(map)
                "OK"
            }

            "HMGET" -> {
                val rmap = client.getMap<String, String>(key)
                args.map { field -> rmap[field] }
            }

            "HGETALL" -> client.getMap<String, String>(key).readAllMap()

            "HDEL" -> {
                val rmap = client.getMap<String, String>(key)
                args.count { rmap.remove(it) != null }.toLong()
            }

            "HEXISTS" -> client.getMap<String, String>(key).containsKey(arg(args, 0))
            "HKEYS" -> ArrayList(client.getMap<String, String>(key).readAllKeySet())
            "HVALS" -> ArrayList(client.getMap<String, String>(key).readAllValues())
            "HLEN" -> client.getMap<String, String>(key).size.toLong()

            "HINCRBY" -> {
                val rmap = client.getMap<String, String>(key)
                val field = arg(args, 0)
                val delta = arg(args, 1).toLong()
                val current = rmap[field]?.toLong() ?: 0L
                val newVal = current + delta
                rmap.fastPut(field, newVal.toString())
                newVal
            }

            // ─── List (RDeque) ──────────────────────────────────────────
            "LPUSH" -> {
                val deque = client.getDeque<String>(key)
                for (i in args.indices.reversed()) deque.addFirst(args[i])
                deque.size.toLong()
            }

            "RPUSH" -> {
                val deque = client.getDeque<String>(key)
                args.forEach { deque.addLast(it) }
                deque.size.toLong()
            }

            "LPOP" -> client.getDeque<String>(key).pollFirst()
            "RPOP" -> client.getDeque<String>(key).pollLast()
            "LLEN" -> client.getDeque<String>(key).size.toLong()

            "LRANGE" -> {
                val all = ArrayList(client.getDeque<String>(key))
                val start = normalizeIndex(arg(args, 0).toInt(), all.size)
                val stop = normalizeIndex(arg(args, 1).toInt(), all.size)
                if (start > stop || start >= all.size) emptyList()
                else all.subList(start, minOf(stop + 1, all.size))
            }

            "LINDEX" -> {
                val all = ArrayList(client.getDeque<String>(key))
                val idx = normalizeIndex(arg(args, 0).toInt(), all.size)
                if (idx in all.indices) all[idx] else null
            }

            "LSET" -> {
                val deque = client.getDeque<String>(key)
                val all = ArrayList(deque)
                val idx = arg(args, 0).toInt()
                require(idx in all.indices) { "LSET index out of range" }
                all[idx] = arg(args, 1)
                deque.clear()
                all.forEach { deque.addLast(it) }
                "OK"
            }

            "LREM" -> {
                val count = arg(args, 0).toLong()
                val target = arg(args, 1)
                val deque = client.getDeque<String>(key)
                val all = ArrayList(deque)
                val removed = removeElements(all, target, count)
                deque.clear()
                all.forEach { deque.addLast(it) }
                removed
            }

            // ─── Set (RSet) ─────────────────────────────────────────────
            "SADD" -> {
                val rset = client.getSet<String>(key)
                args.count { rset.add(it) }.toLong()
            }

            "SMEMBERS" -> ArrayList(client.getSet<String>(key))

            "SREM" -> {
                val rset = client.getSet<String>(key)
                args.count { rset.remove(it) }.toLong()
            }

            "SISMEMBER" -> client.getSet<String>(key).contains(arg(args, 0))
            "SCARD" -> client.getSet<String>(key).size.toLong()
            "SPOP" -> client.getSet<String>(key).removeRandom()
            "SRANDMEMBER" -> client.getSet<String>(key).random()

            // ─── Sorted Set (RScoredSortedSet) ──────────────────────────
            "ZADD" -> {
                val score = arg(args, 0).toDouble()
                val added = client.getScoredSortedSet<String>(key).add(score, arg(args, 1))
                if (added) 1L else 0L
            }

            "ZRANGE" -> ArrayList(
                client.getScoredSortedSet<String>(key)
                    .valueRange(arg(args, 0).toInt(), arg(args, 1).toInt())
            )

            "ZREVRANGE" -> ArrayList(
                client.getScoredSortedSet<String>(key)
                    .valueRangeReversed(arg(args, 0).toInt(), arg(args, 1).toInt())
            )

            "ZRANGEBYSCORE" -> {
                val min = parseScore(arg(args, 0))
                val max = parseScore(if (args.size > 1) arg(args, 1) else "+inf")
                ArrayList(client.getScoredSortedSet<String>(key).valueRange(min, true, max, true))
            }

            "ZREVRANGEBYSCORE" -> {
                val max = parseScore(arg(args, 0))
                val min = parseScore(if (args.size > 1) arg(args, 1) else "-inf")
                ArrayList(client.getScoredSortedSet<String>(key).valueRangeReversed(min, true, max, true))
            }

            "ZREM" -> {
                val zset = client.getScoredSortedSet<String>(key)
                args.count { zset.remove(it) }.toLong()
            }

            "ZRANK" -> client.getScoredSortedSet<String>(key).rank(arg(args, 0))?.toLong()
            "ZREVRANK" -> client.getScoredSortedSet<String>(key).revRank(arg(args, 0))?.toLong()
            "ZSCORE" -> client.getScoredSortedSet<String>(key).getScore(arg(args, 0))
            "ZINCRBY" -> client.getScoredSortedSet<String>(key).addScore(arg(args, 1), arg(args, 0).toDouble())
            "ZCARD" -> client.getScoredSortedSet<String>(key).size().toLong()

            "ZCOUNT" -> {
                val min = parseScore(arg(args, 0))
                val max = parseScore(if (args.size > 1) arg(args, 1) else "+inf")
                client.getScoredSortedSet<String>(key).count(min, true, max, true).toLong()
            }

            "ZPOPMIN" -> client.getScoredSortedSet<String>(key).pollFirstEntry()?.value
            "ZPOPMAX" -> client.getScoredSortedSet<String>(key).pollLastEntry()?.value

            // ─── Distributed locks (Redisson-native strength) ──────────
            "LOCK" -> {
                val lock = client.getLock(key)
                if (args.isNotEmpty()) lock.lock(arg(args, 0).toLong(), TimeUnit.MILLISECONDS)
                else lock.lock()
                "OK"
            }

            "TRYLOCK" -> {
                val lock = client.getLock(key)
                try {
                    if (args.size >= 2) lock.tryLock(arg(args, 0).toLong(), arg(args, 1).toLong(), TimeUnit.MILLISECONDS)
                    else lock.tryLock()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    false
                }
            }

            "UNLOCK" -> {
                val lock = client.getLock(key)
                if (lock.isHeldByCurrentThread) lock.unlock()
                "OK"
            }

            // ─── Pub/Sub ───────────────────────────────────────────────
            "PUBLISH" -> client.getTopic(key).publish(arg(args, 0))

            else -> throw UnsupportedOperationException(
                "Unsupported Redis command: [$command]. For complex commands, use EVAL with Lua script."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lua scripting (EVAL / SCRIPT LOAD / EVALSHA)
    // ─────────────────────────────────────────────────────────────────────

    override fun eval(script: String, keys: List<String>, args: List<String>): Any? {
        log.debug { "redisson EVAL keys=$keys args=$args" }
        val rScript = client.script
        return rScript.eval<Any>(
            RScript.Mode.READ_WRITE,
            script,
            RScript.ReturnType.MULTI,
            keys.toMutableList<Any>(),
            *args.toTypedArray()
        )
    }

    override fun scriptLoad(script: String): String {
        log.debug { "redisson SCRIPT LOAD" }
        return client.script.scriptLoad(script)
    }

    override fun evalSha(sha: String, keys: List<String>, args: List<String>): Any? {
        log.debug { "redisson EVALSHA sha=$sha keys=$keys args=$args" }
        return client.script.evalSha<Any>(
            RScript.Mode.READ_WRITE,
            sha,
            RScript.ReturnType.MULTI,
            keys.toMutableList<Any>(),
            *args.toTypedArray()
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pipeline (RBatch) execution
    // ─────────────────────────────────────────────────────────────────────

    override fun pipeline(commands: List<RedisRawCommand>): List<Any?> {
        if (commands.isEmpty()) return emptyList()

        val batch = client.createBatch()
        val usesBatch = BooleanArray(commands.size)

        for (i in commands.indices) {
            val cmd = commands[i]
            usesBatch[i] = enqueueBatch(batch, cmd.command, cmd.key, cmd.args)
        }

        val batchResult = batch.execute()
        val batchResponses = batchResult.responses

        val results = mutableListOf<Any?>()
        var batchIdx = 0
        for (i in commands.indices) {
            if (usesBatch[i]) {
                results.add(if (batchIdx < batchResponses.size) batchResponses[batchIdx++] else null)
            } else {
                val cmd = commands[i]
                try {
                    results.add(execute(cmd.command, cmd.key, cmd.args))
                } catch (e: Exception) {
                    log.warn(e) { "Fallback sequential command [${cmd.command}] failed: ${e.message}" }
                    results.add(null)
                }
            }
        }
        return results
    }

    private fun enqueueBatch(batch: RBatch, command: String, key: String, args: List<String>): Boolean {
        return try {
            when (command.uppercase()) {
                "GET" -> batch.getBucket<String>(key).getAsync()
                "SET" -> batch.getBucket<String>(key).setAsync(if (args.isEmpty()) "" else args[0])
                "SETEX" -> batch.getBucket<String>(key).setAsync(arg(args, 1), Duration.ofSeconds(arg(args, 0).toLong()))
                "DEL" -> batch.getBucket<Any>(key).deleteAsync()
                "EXISTS" -> batch.getBucket<Any>(key).isExistsAsync()
                "EXPIRE" -> batch.getBucket<Any>(key).expireAsync(Duration.ofSeconds(arg(args, 0).toLong()))
                "TTL" -> batch.getBucket<Any>(key).remainTimeToLiveAsync()
                "INCR" -> batch.getAtomicLong(key).incrementAndGetAsync()
                "INCRBY" -> batch.getAtomicLong(key).addAndGetAsync(arg(args, 0).toLong())
                "HGET" -> batch.getMap<String, String>(key).getAsync(arg(args, 0))
                "HSET" -> batch.getMap<String, String>(key).fastPutAsync(arg(args, 0), arg(args, 1))
                "HGETALL" -> batch.getMap<String, String>(key).readAllMapAsync()
                "HDEL" -> batch.getMap<String, String>(key).removeAsync(arg(args, 0))
                "LPUSH" -> batch.getDeque<String>(key).addFirstAsync(arg(args, 0))
                "RPUSH" -> batch.getDeque<String>(key).addLastAsync(arg(args, 0))
                "LLEN" -> batch.getDeque<String>(key).sizeAsync()
                "SADD" -> batch.getSet<String>(key).addAsync(arg(args, 0))
                "SCARD" -> batch.getSet<String>(key).sizeAsync()
                "ZADD" -> batch.getScoredSortedSet<String>(key).addAsync(arg(args, 0).toDouble(), arg(args, 1))
                "ZCARD" -> batch.getScoredSortedSet<String>(key).sizeAsync()
                "PUBLISH" -> batch.getTopic(key).publishAsync(arg(args, 0))
                else -> return false
            }
            true
        } catch (e: Exception) {
            log.warn(e) { "Failed to enqueue command [$command] to RBatch: ${e.message}" }
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun arg(args: List<String>, index: Int): String {
        if (index < args.size) return args[index]
        throw IllegalArgumentException("Argument at index [$index] is missing, args=$args")
    }

    private fun normalizeIndex(index: Int, size: Int): Int =
        if (index < 0) maxOf(0, size + index) else index

    private fun parseScore(score: String): Double = when (score.lowercase()) {
        "+inf", "inf" -> Double.POSITIVE_INFINITY
        "-inf" -> Double.NEGATIVE_INFINITY
        else -> score.toDouble()
    }

    private fun removeElements(list: MutableList<String>, target: String, count: Long): Long {
        var removed = 0L
        if (count == 0L) {
            removed = list.count { it == target }.toLong()
            list.removeAll { it == target }
        } else if (count > 0) {
            val it = list.iterator()
            while (it.hasNext() && removed < count) {
                if (it.next() == target) { it.remove(); removed++ }
            }
        } else {
            val it = list.listIterator(list.size)
            val limit = -count
            while (it.hasPrevious() && removed < limit) {
                if (it.previous() == target) { it.remove(); removed++ }
            }
        }
        return removed
    }
}
