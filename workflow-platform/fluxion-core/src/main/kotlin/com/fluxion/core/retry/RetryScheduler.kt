package com.fluxion.core.retry

import com.fluxion.core.engine.TaskInterceptor
import org.slf4j.*
import java.util.concurrent.*
import java.util.function.Supplier

/**
 * 重试调度器 — 基于 JDK ScheduledThreadPoolExecutor + 虚拟线程
 *
 * 架构：
 *   - scheduler: ScheduledThreadPoolExecutor 负责定时调度（不能改虚拟线程）
 *   - retryWorker: 虚拟线程执行器，负责实际任务执行（Java 21+）
 *   - 调用方通过 CompletableFuture 获取结果
 */
class RetryScheduler @JvmOverloads constructor(
    private val retryWorker: Executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("fluxion-retry-worker", 0).factory()
    ),
    private val taskInterceptor: TaskInterceptor = TaskInterceptor.NOOP
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val scheduler =
        ScheduledThreadPoolExecutor(1) { r -> Thread(r, "fluxion-retry-timer").apply { isDaemon = true } }.apply {
            removeOnCancelPolicy = true
        }

    /**
     * 延迟 delayMs 后在 retryWorker 上异步执行 supplier，返回 CompletableFuture
     */
    fun <T> schedule(supplier: Supplier<T>, delayMs: Long): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        val wrappedWorker = taskInterceptor.wrap(retryWorker)
        if (delayMs <= 0) {
            wrappedWorker.execute { executeSupplier(supplier, future) }
        } else {
            scheduler.schedule(
                { wrappedWorker.execute { executeSupplier(supplier, future) } },
                delayMs, TimeUnit.MILLISECONDS
            )
        }
        return future
    }

    private fun <T> executeSupplier(supplier: Supplier<T>, future: CompletableFuture<T>) {
        try {
            future.complete(supplier.get())
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        }
    }

    /** 停止调度器（应在应用关闭时调用） */
    fun shutdown() {
        log.info { "Shutting down RetryScheduler" }
        scheduler.shutdownNow()
    }
}
