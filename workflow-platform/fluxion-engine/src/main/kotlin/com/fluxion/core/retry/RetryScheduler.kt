package com.fluxion.core.retry

import com.fluxion.decorator.engine.TaskInterceptor
import org.slf4j.*
import java.util.concurrent.*
import java.util.function.Supplier

/**
 * Schedules retry attempts with a fixed delay.
 *
 * Internally splits concerns across two executors:
 *  - `scheduler`: single-threaded [ScheduledThreadPoolExecutor] used purely
 *    as a delay timer (daemon thread, `removeOnCancelPolicy` enabled so
 *    cancelled retries do not leak).
 *  - `retryWorker`: virtual-thread-per-task executor (Java 21+) that
 *    actually runs the user-supplied retry payload. Using virtual threads
 *    means retries never block the scarce scheduler timer thread.
 *
 * Returns [CompletableFuture] so callers (e.g. [WorkflowEngine]) can chain
 * further retries through `exceptionallyCompose`.
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
     * Schedule `supplier` to run after `delayMs` (or immediately if <= 0).
     *
     * @return a [CompletableFuture] that completes with the supplier result
     *   or completes exceptionally with any throwable raised by the supplier.
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

    /** Gracefully shut down the scheduler timer thread (called on Spring context close). */
    fun shutdown() {
        log.info { "Shutting down RetryScheduler" }
        scheduler.shutdownNow()
    }
}
