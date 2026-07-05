package com.fluxion.test.lock

import com.fluxion.core.lock.DistributedLock
import com.fluxion.core.lock.DistributedLockProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 内存版分布式锁实现 — 仅用于测试。
 *
 * 功能与 [com.fluxion.core.lock.LocalDistributedLockProvider] 相同，但额外暴露
 * transientFailures、acquireAttempts、lastRetry 等钩子，方便单元测试验证
 * 重试、同步阻塞等参数是否正确传递。
 */
class InMemoryDistributedLockProvider : DistributedLockProvider {

    private val locks = ConcurrentHashMap<String, InMemoryDistributedLock>()

    /**
     * 控制指定锁键前 N 次获取失败，用于验证重试逻辑。
     */
    val transientFailures = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * 记录每个锁键的获取尝试次数。
     */
    val acquireAttempts = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * 记录最后一次 [acquire] 调用传入的参数。
     */
    @Volatile var lastRetry: Int = -1
    @Volatile var lastRetryIntervalMillis: Long = -1
    @Volatile var lastSync: Boolean = false

    override fun acquire(
        lockKey: String,
        leaseMillis: Long,
        waitMillis: Long,
        retry: Int,
        retryIntervalMillis: Long,
        sync: Boolean
    ): DistributedLock? {
        lastRetry = retry
        lastRetryIntervalMillis = retryIntervalMillis
        lastSync = sync

        val attempts = maxOf(retry, 0) + 1
        repeat(attempts) { attempt ->
            acquireAttempts.computeIfAbsent(lockKey) { AtomicInteger(0) }.incrementAndGet()

            val failureCounter = transientFailures[lockKey]
            if (failureCounter == null || failureCounter.getAndDecrement() <= 0) {
                val newLock = InMemoryDistributedLock(lockKey)
                if (locks.putIfAbsent(lockKey, newLock) == null) {
                    return newLock
                }
            }

            if (sync) {
                while (locks.containsKey(lockKey)) {
                    Thread.sleep(retryIntervalMillis.coerceAtLeast(10))
                }
                val newLock = InMemoryDistributedLock(lockKey)
                if (locks.putIfAbsent(lockKey, newLock) == null) {
                    return newLock
                }
            } else if (attempt < attempts - 1 && retryIntervalMillis > 0) {
                Thread.sleep(retryIntervalMillis)
            }
        }
        return null
    }

    override fun release(lock: DistributedLock) {
        locks.remove(lock.key, lock)
    }

    /**
     * 检查指定锁键是否已被释放（用于测试断言）。
     */
    fun isReleased(lockKey: String): Boolean = !locks.containsKey(lockKey)

    private data class InMemoryDistributedLock(
        override val key: String,
        override val token: String = "in-memory"
    ) : DistributedLock
}
