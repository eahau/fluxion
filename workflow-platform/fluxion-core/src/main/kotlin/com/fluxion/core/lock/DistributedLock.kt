package com.fluxion.core.lock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Distributed lock handle -- returned on successful acquire, used to release the lock.
 *
 * Implementations should hold the underlying client's lock reference (e.g. Redisson RLock,
 * Redis SET NX token, etc.), but only expose key and token externally.
 */
interface DistributedLock {
    /** Lock key */
    val key: String

    /**
     * Unique identifier (for safe release to avoid releasing a lock held by others).
     * For simple implementations that don't distinguish holders, may return empty string.
     */
    val token: String
}

/**
 * Distributed lock parameters.
 *
 * Used by [DistributedLockProvider.lock] / [lockSuspending] to avoid overly long method signatures.
 */
data class LockParams(
    val waitMillis: Long = 0L,
    val leaseMillis: Long = 30_000L,
    val retry: Int = 0,
    val retryIntervalMillis: Long = 100L,
    val sync: Boolean = false,
    val failOnLocked: Boolean = true
)

/**
 * Distributed lock provider SPI -- zero framework dependencies.
 *
 * Hides underlying Redis / ZooKeeper / Database implementation differences,
 * for use by [com.fluxion.decorator.decorator.NodeDecorator] and WorkflowRouter.
 */
interface DistributedLockProvider {

    /**
     * Attempt to acquire a distributed lock.
     *
     * @param lockKey     Lock key (caller has already concatenated business dimensions,
     *                    e.g. workflowId:nodeId:bizKey)
     * @param leaseMillis Lock lease (max hold time, milliseconds). If not released
     *                    before this time, the lock should auto-expire.
     * @param waitMillis  Max wait time (milliseconds). 0 means don't wait, return immediately.
     * @param retry       Number of retries after failure (default 0).
     * @param retryIntervalMillis Interval between retries (milliseconds, default 0).
     * @param sync        Whether to block synchronously acquiring the lock (default false).
     *                    When true, blocks until lock is acquired (use with caution,
     *                    will block the current thread/coroutine).
     * @return [DistributedLock] on success, null on failure.
     */
    fun acquire(
        lockKey: String,
        leaseMillis: Long,
        waitMillis: Long,
        retry: Int = 0,
        retryIntervalMillis: Long = 0,
        sync: Boolean = false
    ): DistributedLock?

    /**
     * Release the lock acquired by [acquire].
     *
     * Implementations should verify [DistributedLock.token] and only release
     * locks held by the current holder.
     * Release failure (e.g. lock already expired) should not throw, only log.
     */
    fun release(lock: DistributedLock)

    /**
     * Execute [action] under distributed lock protection.
     *
     * Default implementation wraps [acquire] / [release]; callers don't need
     * to manage the lock lifecycle manually.
     * Implementations may override to leverage the underlying client's native
     * capabilities (e.g. Redisson's lock.lock() / tryLock()).
     *
     * @param params Lock parameters (includes failure strategy [LockParams.failOnLocked]).
     */
    fun <T> lock(
        lockKey: String,
        params: LockParams,
        action: () -> T
    ): T {
        val lock = acquire(
            lockKey,
            params.leaseMillis,
            params.waitMillis,
            params.retry,
            params.retryIntervalMillis,
            params.sync
        )
        if (lock == null) {
            if (params.failOnLocked) {
                throw com.fluxion.core.exception.LockAcquisitionException("Failed to acquire lock key=$lockKey")
            }
            return action()
        }
        try {
            return action()
        } finally {
            try {
                release(lock)
            } catch (_: Exception) {
                // release failure should not affect business result;
                // implementations log on their own
            }
        }
    }

    /**
     * No-op implementation -- for scenarios where distributed lock is not configured.
     */
    companion object {
        @JvmField
        val NOOP: DistributedLockProvider = object : DistributedLockProvider {
            override fun acquire(
                lockKey: String,
                leaseMillis: Long,
                waitMillis: Long,
                retry: Int,
                retryIntervalMillis: Long,
                sync: Boolean
            ): DistributedLock? = null

            override fun release(lock: DistributedLock) {}
        }
    }
}

/**
 * Suspend version of [DistributedLockProvider.lock].
 *
 * Lock acquire and release run on [Dispatchers.IO]; [action] runs on the
 * caller's coroutine dispatcher (not forced to IO thread).
 */
suspend fun <T> DistributedLockProvider.lockSuspending(
    lockKey: String,
    params: LockParams,
    action: suspend () -> T
): T {
    val lock = withContext(Dispatchers.IO) {
        acquire(
            lockKey,
            params.leaseMillis,
            params.waitMillis,
            params.retry,
            params.retryIntervalMillis,
            params.sync
        )
    }
    if (lock == null) {
        if (params.failOnLocked) {
            throw com.fluxion.core.exception.LockAcquisitionException("Failed to acquire lock key=$lockKey")
        }
        return action()
    }
    try {
        return action()
    } finally {
        withContext(Dispatchers.IO) {
            try {
                release(lock)
            } catch (_: Exception) {
                // release failure should not affect business result;
                // implementations log on their own
            }
        }
    }
}
