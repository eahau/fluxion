package com.fluxion.core.function

import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.LongAdder

/**
 * 函数版本状态
 */
enum class FunctionVersionState {
    /** 当前新执行默认使用的版本 */
    ACTIVE,
    /** 已有新版本替代，不再接收新执行，但仍在服务中的执行可继续使用 */
    RETIRING
}

/**
 * 函数的一个具体版本。
 *
 * 与 Erlang 热代码升级中的 "old code" 概念对应：
 * 新版本上线后，旧版本进入 RETIRING 状态，只服务已在运行的调用，
 * 无在途调用后即可被 GC 回收。
 */
class FunctionVersion(
    /** 版本号，单调递增 */
    val version: Long,
    /** 函数实现 */
    val function: WorkflowFunction<Any>,
    /** 函数元信息 */
    val meta: FunctionMeta,
    /** 版本创建时间 */
    val createdAt: Long = System.currentTimeMillis()
) {
    private val stateRef = AtomicReference(FunctionVersionState.ACTIVE)
    private val inFlightRef = LongAdder()

    /** 当前状态 */
    val state: FunctionVersionState get() = stateRef.get()

    /** 当前在途调用数 */
    val inFlight: Long get() = inFlightRef.sum()

    /** 标记为退役中 */
    fun retire() = stateRef.set(FunctionVersionState.RETIRING)

    /** 包装为带在途计数追踪的函数 */
    fun toTrackingFunction(): WorkflowFunction<Any> = InFlightTrackingFunction(this)

    internal fun incrementInFlight() = inFlightRef.increment()
    internal fun decrementInFlight() = inFlightRef.decrement()
}

/**
 * 在途调用追踪包装函数。
 *
 * 每次 apply() 前后维护 FunctionVersion 的 inFlight 计数，
 * 使退役版本能感知是否还有未完成的调用。
 */
private class InFlightTrackingFunction(
    private val version: FunctionVersion
) : WorkflowFunction<Any> {

    override fun apply(input: NodeInput): FunctionResult<Any> {
        version.incrementInFlight()
        try {
            return version.function.apply(input)
        } finally {
            version.decrementInFlight()
        }
    }

    override fun meta(): FunctionMeta = version.meta

    override fun fallback(input: NodeInput, ex: Throwable): FunctionResult<Any> =
        version.function.fallback(input, ex)
}
