package com.fluxion.core.mock

import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult

/**
 * 将 Mock 能力封装为 [WorkflowFunction]。
 *
 * 该函数包装一个真实函数，在 Mock 规则命中时直接返回 Mock 响应，
 * 未命中时委托给被包装函数执行。
 *
 * mockConfig 通过 provider 在每次 apply() 时动态获取，
 * 避免构造时捕获静态配置，便于支持执行期动态调整调试 Mock。
 */
class MockWorkflowFunction(
    private val inner: WorkflowFunction<Any>,
    private val mockConfigProvider: () -> MockConfig,
    private val functionRef: String
) : WorkflowFunction<Any> {

    override fun apply(input: NodeInput): FunctionResult<Any> {
        val mockResult = evaluateMock(mockConfigProvider(), functionRef, input)
        return if (mockResult != null) {
            FunctionResult.success(mockResult)
        } else {
            inner.apply(input)
        }
    }

    /**
     * Mock 包装函数不应对入参/出参做 Schema 校验，
     * 因为命中 Mock 时不会执行真实函数逻辑。
     */
    override fun meta(): FunctionMeta = inner.meta().copy(
        name = "mock:${inner.meta().name}",
        inputSchema = null,
        outputSchema = null
    )

    override fun fallback(input: NodeInput, ex: Throwable): FunctionResult<Any> {
        return inner.fallback(input, ex)
    }
}
