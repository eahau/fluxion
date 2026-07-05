package com.fluxion.builtin.signal

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.builtin.meta.BuiltinFunctionMetas
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.signal.SignalBroker
import com.fluxion.core.value.FunctionResult
import org.slf4j.LoggerFactory
import org.slf4j.debug

/**
 * 内置信号等待函数（builtin:waitForSignal）
 *
 * 阻塞当前节点执行，直到外部通过 Signal API 发送匹配的信号。
 * 信号到达后返回 signal payload 作为节点输出，供下游节点消费。
 *
 * 典型场景：
 *   - 人工审批：等待 "approve" / "reject" 信号
 *   - 外部回调：等待第三方系统回调
 *   - 人工干预：运维手动注入数据跳过阻塞
 *
 * 依赖 [SignalBroker] 进行信号收发。
 * 在虚拟线程环境下（Fluxion 默认），阻塞开销极低。
 */
class WaitForSignalFunction(
    private val signalBroker: SignalBroker
) : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val signalName = input.requireParam<String>("signalName")
        val timeoutMs = input.paramAsLong("timeoutMs", 0L)
        val executionId = input.meta?.executionId
            ?: throw IllegalStateException("waitForSignal requires execution context (meta.executionId)")

        log.debug { "waitForSignal: executionId=$executionId signalName=$signalName timeoutMs=$timeoutMs" }

        val signal = signalBroker.awaitSignal(executionId, signalName, timeoutMs)
            ?: throw SignalTimeoutException(
                "Signal [$signalName] not received within ${timeoutMs}ms for execution [$executionId]"
            )

        log.debug { "waitForSignal: received signal=$signalName for executionId=$executionId" }
        return FunctionResult.success(signal.payload)
    }

    override fun meta() = BuiltinFunctionMetas.WAIT_FOR_SIGNAL
}

/**
 * 信号等待超时异常
 */
class SignalTimeoutException(message: String) : RuntimeException(message)
