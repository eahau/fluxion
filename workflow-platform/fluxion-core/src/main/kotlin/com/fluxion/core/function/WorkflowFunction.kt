package com.fluxion.core.function

import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult

/**
 * 工作流函数核心抽象
 *
 * 签名：O apply(NodeInput input)
 * 节点函数只能读取 input 中的数据，不能访问 ImmutableExecutionState
 *
 * @param O 输出类型
 */
@FunctionalInterface
fun interface WorkflowFunction<O> {

    /**
     * 执行函数逻辑
     * 约束：只读 input，返回值是唯一输出通道，相同 input 应返回相同 output
     */
    fun apply(input: NodeInput): FunctionResult<O>

    /**
     * 函数元信息（注册、文档生成、Schema 校验）。
     *
     * 推荐实现类把 FunctionMeta 作为 companion object 常量静态初始化，
     * 并在 meta() 中直接返回该常量，保证调用时只是纯 get，无运行时构造开销。
     *
     * 默认 fallback 仅在没有覆盖时使用，按类名生成一个最小 meta。
     */
    fun meta(): FunctionMeta = FunctionMeta.of(this::class.java.simpleName ?: "anonymous")

    /**
     * 降级函数（默认抛出异常）
     * 若需要真正的降级恢复逻辑，必须覆盖此方法并返回合法的降级值
     */
    fun fallback(input: NodeInput, ex: Throwable): FunctionResult<O> {
        throw WorkflowNodeException(meta().name, ex)
    }

    // ─── 函数组合能力 ────────────────────────────────────────────

    /**
     * 链式组合：this → after（类比函数调用链）
     * 组合后的函数：NodeInput → this.apply() → NodeInput(output) → after.apply()
     */
    fun <V> andThen(after: WorkflowFunction<V>): WorkflowFunction<V> {
        val self = this
        return object : WorkflowFunction<V> {
            override fun apply(input: NodeInput): FunctionResult<V> {
                val intermediate = self.apply(input)
                val nextInput = input.withDirectInput(intermediate.output)
                return after.apply(nextInput)
            }

            override fun meta(): FunctionMeta = FunctionMeta.composed(self.meta(), after.meta())
        }
    }
}
