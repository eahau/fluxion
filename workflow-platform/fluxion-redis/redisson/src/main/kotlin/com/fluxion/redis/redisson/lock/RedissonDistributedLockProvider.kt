package com.fluxion.redis.redisson.lock

import com.fluxion.core.lock.DistributedLock
import com.fluxion.core.lock.DistributedLockProvider
import org.redisson.api.RedissonClient
import org.slf4j.*
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 基于 Redisson 的分布式锁实现。
 *
 * 使用 Redisson 的 [org.redisson.api.RLock] 实现可重入、带看门狗（watch dog）的分布式锁。
 * 当指定 [leaseMillis] 时，锁会在租约到期后自动释放，避免死锁。
 */
class RedissonDistributedLockProvider(
    private val client: RedissonClient
) : DistributedLockProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun acquire(
        lockKey: String,
        leaseMillis: Long,
        waitMillis: Long,
        retry: Int,
        retryIntervalMillis: Long,
        sync: Boolean
    ): DistributedLock? {
        val rLock = client.getLock(lockKey)
        val token = UUID.randomUUID().toString()

        return try {
            if (sync) {
                // 同步阻塞模式：拿不到锁一直阻塞（慎用）
                if (leaseMillis > 0) {
                    rLock.lock(leaseMillis, TimeUnit.MILLISECONDS)
                } else {
                    rLock.lock()
                }
                return RedissonDistributedLock(lockKey, token, rLock)
            }

            val attempts = maxOf(retry, 0) + 1
            repeat(attempts) { attempt ->
                val acquired = if (waitMillis <= 0) {
                    rLock.tryLock(0, leaseMillis, TimeUnit.MILLISECONDS)
                } else {
                    rLock.tryLock(waitMillis, leaseMillis, TimeUnit.MILLISECONDS)
                }
                if (acquired) {
                    return RedissonDistributedLock(lockKey, token, rLock)
                }
                if (attempt < attempts - 1 && retryIntervalMillis > 0) {
                    Thread.sleep(retryIntervalMillis)
                }
            }
            log.debug { "Failed to acquire Redisson lock [$lockKey] after $attempts attempts (wait=$waitMillis ms, lease=$leaseMillis ms, retry=$retry)" }
            null
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn { "Interrupted while acquiring Redisson lock [$lockKey]" }
            null
        } catch (ex: Exception) {
            log.warn(ex) { "Failed to acquire Redisson lock [$lockKey]: ${ex.message}" }
            null
        }
    }

    override fun release(lock: DistributedLock) {
        val rLock = (lock as? RedissonDistributedLock)?.rLock ?: return
        try {
            if (rLock.isHeldByCurrentThread) {
                rLock.unlock()
            } else {
                log.debug { "Skipping unlock for [$lock.key]: not held by current thread" }
            }
        } catch (ex: IllegalMonitorStateException) {
            log.warn(ex) { "Lock [$lock.key] already released or expired" }
        } catch (ex: Exception) {
            log.warn(ex) { "Failed to release Redisson lock [$lock.key]: ${ex.message}" }
        }
    }

    private data class RedissonDistributedLock(
        override val key: String,
        override val token: String,
        val rLock: org.redisson.api.RLock
    ) : DistributedLock
}
