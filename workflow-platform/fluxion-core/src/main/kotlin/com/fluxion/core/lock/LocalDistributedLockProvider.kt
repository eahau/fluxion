package com.fluxion.core.lock

import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Local (single JVM) distributed lock implementation.
 *
 * Based on [ConcurrentHashMap] for intra-JVM mutual exclusion, suitable for:
 * - Local development
 * - Single-node deployments without external Redis/ZooKeeper coordination services
 *
 * Note: This implementation cannot provide cross-JVM mutual exclusion.
 * For multi-instance deployments, inject [RedissonDistributedLockProvider]
 * or other true distributed implementations.
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
                // synchronous blocking: busy-wait (only for extreme single-node scenarios)
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
