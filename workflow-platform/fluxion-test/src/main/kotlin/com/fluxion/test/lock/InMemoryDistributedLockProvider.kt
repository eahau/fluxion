package com.fluxion.test.lock

import com.fluxion.core.lock.DistributedLock
import com.fluxion.core.lock.DistributedLockProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory [DistributedLockProvider] designed exclusively for unit tests.
 *
 * Mirrors the behaviour of `LocalDistributedLockProvider` in production but
 * deliberately exposes extra inspection hooks so tests can assert on
 * retry parameters, simulate transient failures, and verify lock release:
 *
 *  - [transientFailures]  -- force the next N acquires for a given key to fail.
 *  - [acquireAttempts]   -- counts every `acquire` call per key (includes retries).
 *  - [lastRetry], [lastRetryIntervalMillis], [lastSync] -- remember the last
 *    call's parameters so tests can assert correct propagation.
 *  - [isReleased]        -- convenience assertion for common "lock was freed" checks.
 *
 * **Do not use in production** -- this implementation is in-process only and
 * has no lease-expiry mechanism.
 */
class InMemoryDistributedLockProvider : DistributedLockProvider {

    private val locks = ConcurrentHashMap<String, InMemoryDistributedLock>()

    /** Number of transient failures still to inject for a given lock key. */
    val transientFailures = ConcurrentHashMap<String, AtomicInteger>()

    /** Total acquire attempts made per key (each retry increments the count). */
    val acquireAttempts = ConcurrentHashMap<String, AtomicInteger>()

    /** Mirrored parameters from the most recent [acquire] call; useful for assertion. */
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
                // Synchronous path: spin-wait until the lock is released, then
                // race with any other waiter for the slot.
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

    /** Assertion helper: returns `true` if `lockKey` is currently un-held. */
    fun isReleased(lockKey: String): Boolean = !locks.containsKey(lockKey)

    private data class InMemoryDistributedLock(
        override val key: String,
        override val token: String = "in-memory"
    ) : DistributedLock
}
