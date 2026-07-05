package com.fluxion.core.lock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 分布式锁句柄 — 获取成功后返回，用于释放锁。
 *
 * 实现类应持有底层客户端的锁引用（如 Redisson RLock、
 * Redis SET NX 的 token 等），但对外仅暴露 key 与 token。
 */
interface DistributedLock {
    /** 锁键 */
    val key: String

    /**
     * 唯一标识符（用于安全释放，避免误释放他人持有的锁）。
     * 对于不区分持有者的简单实现，可返回空字符串。
     */
    val token: String
}

/**
 * 分布式锁参数。
 *
 * 供 [DistributedLockProvider.lock] / [lockSuspending] 使用，避免方法签名过长。
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
 * 分布式锁提供者 SPI — 零框架依赖。
 *
 * 屏蔽底层 Redis / ZooKeeper / Database 等实现差异，
 * 供 [com.fluxion.core.decorator.NodeDecorator] 与 WorkflowRouter 使用。
 */
interface DistributedLockProvider {

    /**
     * 尝试获取分布式锁。
     *
     * @param lockKey     锁键（调用方已拼接好业务维度，如 workflowId:nodeId:bizKey）
     * @param leaseMillis 锁租约（最大持有时间，毫秒）。超过此时间未释放，锁应自动失效。
     * @param waitMillis  最大等待时间（毫秒）。0 表示不等待，立即返回。
     * @param retry       获取失败后的重试次数（默认 0）。
     * @param retryIntervalMillis 每次重试间隔（毫秒，默认 0）。
     * @param sync        是否同步阻塞获取锁（默认 false）。为 true 时拿不到锁一直阻塞，
     *                    直到获取成功（慎用，会阻塞当前线程/协程）。
     * @return 获取成功返回 [DistributedLock]，失败返回 null
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
     * 释放由 [acquire] 获取的锁。
     *
     * 实现类应校验 [DistributedLock.token]，仅释放当前持有者持有的锁。
     * 释放失败（如锁已超时）不应抛出异常，仅记录日志。
     */
    fun release(lock: DistributedLock)

    /**
     * 在分布式锁保护下执行 [action]。
     *
     * 默认实现基于 [acquire] / [release] 封装，调用方无需手动管理锁生命周期。
     * 实现类可覆盖此方法以利用底层客户端的原生能力（如 Redisson 的 lock.lock() / tryLock()）。
     *
     * @param params 锁参数（含失败策略 [LockParams.failOnLocked]）
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
                // 释放失败不应当影响业务结果，由具体实现自行记录日志
            }
        }
    }

    /**
     * 无操作实现 — 用于未配置分布式锁的场景。
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
 * [DistributedLockProvider.lock] 的 suspend 版本。
 *
 * 锁的获取与释放在 [Dispatchers.IO] 中执行；[action] 在调用方协程调度器中执行，
 * 不强制绑定 IO 线程。
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
                // 释放失败不应当影响业务结果，由具体实现自行记录日志
            }
        }
    }
}
