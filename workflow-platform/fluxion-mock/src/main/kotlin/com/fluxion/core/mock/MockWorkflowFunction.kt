package com.fluxion.core.mock

import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionResult

/**
 * 鐏?Mock 閼宠棄濮忕亸浣筋棅娑?[WorkflowFunction]閵?
 *
 * 鐠囥儱鍤遍弫鏉垮瘶鐟佸懍绔存稉顏嗘埂鐎圭偛鍤遍弫甯礉閸?Mock 鐟欏嫬鍨崨鎴掕厬閺冨墎娲块幒銉ㄧ箲閸?Mock 閸濆秴绨查敍?
 * 閺堫亜鎳℃稉顓熸婵梹澧紒娆掝潶閸栧懓顥婇崙鑺ユ殶閹笛嗩攽閵?
 *
 * mockConfig 闁俺绻?provider 閸︺劍鐦″▎?apply() 閺冭泛濮╅幀浣藉箯閸欐牭绱?
 * 闁灝鍘ら弸鍕偓鐘虫閹规洝骞忛棃娆愨偓渚€鍘ょ純顕嗙礉娓氬じ绨弨顖涘瘮閹笛嗩攽閺堢喎濮╅幀浣界殶閺佺鐨熺拠?Mock閵?
 */
class MockWorkflowFunction(
    private val inner: WorkflowFunction<Any>,
    private val mockConfigProvider: () -> MockConfig,
    private val functionRef: String
) : WorkflowFunction<Any> {

    override val functionName: String = "mock:$functionRef"

    override fun apply(input: NodeInput): FunctionResult<Any> {
        val mockResult = evaluateMock(mockConfigProvider(), functionRef, input)
        return if (mockResult != null) {
            FunctionResult.success(mockResult)
        } else {
            inner.apply(input)
        }
    }

    override fun fallback(input: NodeInput, ex: Throwable): FunctionResult<Any> = inner.fallback(input, ex)
}
