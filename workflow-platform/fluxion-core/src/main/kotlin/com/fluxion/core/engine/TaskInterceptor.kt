package com.fluxion.core.engine

import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.Executor

/**
 * 跨线程上下文传播扩展点
 *
 * 设计参考 OTel Context.wrap()：
 *   wrap 一次返回包装后的执行器/调度器，调用方无需感知上下文传播。
 *
 * ```
 * // OTel: Context.wrap(Executor)
 * Executor wrapped = context.wrap(executor);
 * wrapped.execute(task);  // context auto-propagated
 *
 * // Fluxion: TaskInterceptor.wrap(CoroutineDispatcher / Executor)
 * async(interceptor.wrap(dispatcher)) { ... }
 * interceptor.wrap(executor).execute { ... }
 * ```
 *
 * 覆盖场景：
 *   - [wrap] `CoroutineDispatcher` — DagExecutor async(Dispatchers.IO)
 *   - [wrap] `Executor` — RetryScheduler / TIMEOUT_EXECUTOR / AsyncDecorator
 *
 * fluxion-core 本身不依赖任何追踪框架，
 * 具体实现由 OTel 模块（如 fluxion-decorator-impl）提供。
 */
interface TaskInterceptor {

    /** 包装 CoroutineDispatcher，在 dispatch 层面自动传播上下文 */
    fun wrap(dispatcher: CoroutineDispatcher): CoroutineDispatcher

    /** 包装 Executor，在 execute 层面自动传播上下文 */
    fun wrap(executor: Executor): Executor = executor

    companion object {
        /** 空实现 — 不传播任何上下文，返回原始执行器/调度器 */
        val NOOP: TaskInterceptor = object : TaskInterceptor {
            override fun wrap(dispatcher: CoroutineDispatcher): CoroutineDispatcher = dispatcher
        }
    }
}
