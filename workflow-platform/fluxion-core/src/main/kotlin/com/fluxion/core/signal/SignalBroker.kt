package com.fluxion.core.signal

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * 外部信号 — 类比 Temporal Signal，用于向运行中的工作流注入数据
 *
 * 典型场景：
 *   - 人工审批：向等待审批的工作流发送 approve/reject 信号
 *   - 外部事件驱动：webhook 回调向工作流注入数据
 *   - 人工干预：运维人员向卡住的工作流注入跳过数据
 */
data class Signal(
    /** 信号名称（对应 waitForSignal 节点的 signalName 参数） */
    val name: String,
    /** 信号携带的数据（作为 waitForSignal 节点的输出） */
    val payload: Any? = null
)

/**
 * 信号投递代理 — 管理运行中工作流的信号收发
 *
 * 支持信号缓冲：信号可以先于 await 到达（deliver-then-await），
 * 也可以后于 await 到达（await-then-deliver），两种顺序均安全。
 *
 * 线程安全：基于 ConcurrentHashMap，适用于高并发场景。
 */
class SignalBroker {

    /** 等待中的信号槽：executionId → signalName → pending future */
    private val waiters = ConcurrentHashMap<String, ConcurrentHashMap<String, CompletableFuture<Signal>>>()

    /** 已缓冲的信号（信号先于 await 到达）：executionId → signalName → completed future */
    private val buffered = ConcurrentHashMap<String, ConcurrentHashMap<String, CompletableFuture<Signal>>>()

    /**
     * 发送信号到指定执行。
     *
     * 如果有节点正在等待该信号 → 立即唤醒；
     * 如果尚无节点等待 → 缓冲信号，后续 await 时立即返回。
     *
     * @return true 信号已被接受（投递或缓冲）
     */
    fun send(executionId: String, signal: Signal): Boolean {
        // 检查是否有等待者
        val execWaiters = waiters[executionId]
        if (execWaiters != null) {
            val future = execWaiters.remove(signal.name)
            if (future != null) {
                future.complete(signal)
                return true
            }
        }
        // 无等待者 → 缓冲
        buffered.computeIfAbsent(executionId) { ConcurrentHashMap() }[signal.name] = CompletableFuture.completedFuture(signal)
        return true
    }

    /**
     * 阻塞等待信号到达。
     *
     * 如果信号已缓冲 → 立即返回；
     * 如果信号未到达 → 注册等待槽并阻塞直到 [send] 唤醒。
     *
     * @param timeoutMs 超时毫秒数（<=0 表示无限等待）
     * @return 信号对象，超时返回 null
     */
    fun awaitSignal(executionId: String, signalName: String, timeoutMs: Long = 0): Signal? {
        // 检查缓冲
        val execBuffered = buffered[executionId]
        if (execBuffered != null) {
            val future = execBuffered.remove(signalName)
            if (future != null) {
                return future.get()
            }
        }

        // 注册等待槽
        val future = CompletableFuture<Signal>()
        val execWaiters = waiters.computeIfAbsent(executionId) { ConcurrentHashMap() }
        execWaiters[signalName] = future

        // 二次检查缓冲（send 可能在 put 和 get 之间到达）
        val execBuffered2 = buffered[executionId]
        if (execBuffered2 != null) {
            val bufferedFuture = execBuffered2.remove(signalName)
            if (bufferedFuture != null) {
                execWaiters.remove(signalName)
                return bufferedFuture.get()
            }
        }

        return if (timeoutMs > 0) {
            try {
                future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                execWaiters.remove(signalName)
                null
            }
        } else {
            try {
                future.get()
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * 查询指定执行的已缓冲信号列表（用于 Query API）
     */
    fun pendingSignals(executionId: String): List<String> {
        val execBuffered = buffered[executionId] ?: return emptyList()
        return execBuffered.keys().toList()
    }

    /**
     * 查询指定执行正在等待的信号列表（用于 Query API）
     */
    fun waitingSignals(executionId: String): List<String> {
        val execWaiters = waiters[executionId] ?: return emptyList()
        return execWaiters.keys().toList()
    }

    /**
     * 清理指定执行的所有信号槽（执行结束后调用）
     */
    fun cleanup(executionId: String) {
        waiters.remove(executionId)?.values?.forEach { it.cancel(true) }
        buffered.remove(executionId)
    }
}
