package com.fluxion.core.lock

import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 本地（单 JVM）分布式锁实现。
 *
 * 基于 [ConcurrentHashMap] 实现同 JVM 内的互斥，适用于：
 * - 本地开发
 * - 单节点部署且无外部 Redis/ZooKeeper 等分布式协调服务的场景
 *
 * 注意：该实现无法跨 JVM 互斥，多实例部署时请注入 [RedissonDistributedLockProvider]
 * 等真正的分布式实现。
 */
class LocalDistributedLockProvider : DistributedLockProvider {

    private val log = LoggerFactory.getLogger(javaClass)
    private val locks = ConcurrentHashMap<String, LocalDistributedLock>()

    override fun acquire(
        lockKey: String,
        leaseMillis: Long,
        waitMillis: Long,
        retry: Int,
        retryIntervalMillis: Long,
        sync: Boolean
    ): DistributedLock? {
        val attempts = maxOf(retry, 0) + 1
        repeat(attempts) { attempt ->
            val newLock = LocalDistributedLock(lockKey)
            if (locks.putIfAbsent(lockKey, newLock) == null) {
                log.debug { "Acquired local lock [$lockKey]" }
                return newLock
            }

            if (sync) {
                // 同步阻塞：忙等（仅适用于极端单节点场景）
                while (locks.containsKey(lockKey)) {
                    Thread.sleep(retryIntervalMillis.coerceAtLeast(10))
                }
                if (locks.putIfAbsent(lockKey, newLock) == null) {
                    return newLock
                }
            } else if (attempt < attempts - 1 && retryIntervalMillis > 0) {
                Thread.sleep(retryIntervalMillis)
            }
        }
        log.debug { "Failed to acquire local lock [$lockKey] after $attempts attempts" }
        return null
    }

    override fun release(lock: DistributedLock) {
        val removed = locks.remove(lock.key, lock)
        if (removed) {
            log.debug { "Released local lock [${lock.key}]" }
        }
    }

    private data class LocalDistributedLock(
        override val key: String,
        override val token: String = "local"
    ) : DistributedLock
}
