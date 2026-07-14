package com.fluxion.decorator.tracing

import com.fluxion.decorator.engine.TaskInterceptor
import io.opentelemetry.context.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

/**
 * [TaskInterceptor] that propagates the current OpenTelemetry `Context`
 * across both coroutine dispatchers and `Executor`-based thread pools.
 *
 * Implementation mirrors the canonical OTel pattern:
 *  - Capture `Context.current()` at the moment [wrap] is called.
 *  - Re-attach it (via `makeCurrent().use { ... }`) when the wrapped
 *    dispatcher / executor actually runs the user's block.
 */
class OtelContextTaskInterceptor : TaskInterceptor {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun wrap(dispatcher: CoroutineDispatcher): CoroutineDispatcher {
        val otelContext = Context.current()
        return object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                dispatcher.dispatch(context) {
                    // Auto-closeable Scope restores the previous context on exit.
                    otelContext.makeCurrent().use { block.run() }
                }
            }
        }
    }

    override fun wrap(executor: Executor): Executor = Context.current().wrap(executor)
}
