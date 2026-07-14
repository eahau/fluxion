package com.fluxion.core.mock

import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionResult

/**
 * Decorator that wraps a real [WorkflowFunction] and short-circuits its
 * execution when an active [MockRule] matches the current invocation.
 *
 * Wrapping is done at the `FunctionResolver` level (see
 * [MockFunctionRegistry]) rather than inside individual function nodes so
 * the mock layer is **fully transparent**: DAG code, traces, metrics, and
 * ACL rules all observe the same function identity they would in production
 * — the behaviour is just switched to a mock response.
 *
 * Fallback (error recovery) is intentionally *not* mocked — we forward
 * `fallback` directly to the inner function so operators can still observe
 * real error-handler behaviour while mocking the happy path.
 */
class MockWorkflowFunction(
    private val inner: WorkflowFunction<Any>,
    private val mockConfigProvider: () -> MockConfig,
    private val functionRef: String
) : WorkflowFunction<Any> {

    /** Registers as `mock:<ref>` so the tracer/debug UI can tell a mock-wrapped function apart. */
    override val functionName: String = "mock:$functionRef"

    /**
     * Evaluate the mock engine first; on a match the result is wrapped in
     * `FunctionResult.success` and returned without touching the inner
     * function. Otherwise the real function is invoked normally.
     */
    override fun apply(input: NodeInput): FunctionResult<Any> {
        val mockResult = evaluateMock(mockConfigProvider(), functionRef, input)
        return if (mockResult != null) {
            FunctionResult.success(mockResult)
        } else {
            inner.apply(input)
        }
    }

    /** Fallback is never mocked — error recovery must exercise the real path. */
    override fun fallback(input: NodeInput, ex: Throwable): FunctionResult<Any> = inner.fallback(input, ex)
}
