package com.fluxion.decorator.engine

import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.Executor

/**
 * Interceptor SPI for propagating thread-bound context across asynchronous
 * execution boundaries used by the engine.
 *
 * Conceptually mirrors OTel's `Context.wrap(Executor)`. The interceptor
 * captures the calling context at wrap-time and restores it when the wrapped
 * dispatcher / executor actually runs the task.
 *
 * Hook points that call `wrap()`:
 *  - `DagExecutor` uses [wrap]`(CoroutineDispatcher)` for `async(Dispatchers.IO)`.
 *  - `RetryScheduler`, `AsyncDecorator` and the per-node timeout executor use
 *    [wrap]`(Executor)`.
 *
 * The default [NOOP] instance is a pass-through; deployments with OpenTelemetry
 * enable `OtelContextTaskInterceptor` via Spring auto-configuration.
 *
 * ### Example (OTel)
 * ```
 * // At interceptor construction:
 * val otelContext = Context.current()
 * // On dispatch() / execute():
 * otelContext.makeCurrent().use { block.run() }
 * ```
 */
interface TaskInterceptor {

    /**
     * Return a dispatcher that propagates captured context before delegating to
     * the original `dispatcher`.
     */
    fun wrap(dispatcher: CoroutineDispatcher): CoroutineDispatcher

    /**
     * Return an executor that propagates captured context before running tasks
     * submitted to the original `executor`. Default is a pass-through.
     */
    fun wrap(executor: Executor): Executor = executor

    companion object {
        /** Identity interceptor -- does not propagate any context. */
        val NOOP: TaskInterceptor = object : TaskInterceptor {
            override fun wrap(dispatcher: CoroutineDispatcher): CoroutineDispatcher = dispatcher
        }
    }
}
