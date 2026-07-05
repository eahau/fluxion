package com.fluxion.decorator.impl.tracing

import com.fluxion.core.engine.TaskInterceptor
import io.opentelemetry.context.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

/**
 * OTel Context 跨线程传播拦截器
 *
 * 参考 OTel Context.wrap(Executor) 模式：
 *   在 wrap() 调用时捕获 Context.current()，
 *   在 dispatch 时 makeCurrent()，确保 IO 线程上 OTel 上下文可用。
 */
class OtelContextTaskInterceptor : TaskInterceptor {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun wrap(dispatcher: CoroutineDispatcher): CoroutineDispatcher {
        val otelContext = Context.current()
        return object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                dispatcher.dispatch(context) {
                    otelContext.makeCurrent().use { block.run() }
                }
            }
        }
    }

    override fun wrap(executor: Executor): Executor = Context.current().wrap(executor)
}
