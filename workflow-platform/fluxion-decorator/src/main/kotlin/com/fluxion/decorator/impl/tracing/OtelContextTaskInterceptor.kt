package com.fluxion.decorator.impl.tracing

import com.fluxion.decorator.engine.TaskInterceptor
import io.opentelemetry.context.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

/**
 * OTel Context 璺ㄧ嚎绋嬩紶鎾嫤鎴櫒
 *
 * 鍙傝€?OTel Context.wrap(Executor) 妯″紡锛?
 *   鍦?wrap() 璋冪敤鏃舵崟鑾?Context.current()锛?
 *   鍦?dispatch 鏃?makeCurrent()锛岀‘淇?IO 绾跨▼涓?OTel 涓婁笅鏂囧彲鐢ㄣ€?
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
