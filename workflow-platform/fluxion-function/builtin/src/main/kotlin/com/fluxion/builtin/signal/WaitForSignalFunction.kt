package com.fluxion.builtin.signal

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.signal.SignalBroker
import com.fluxion.core.value.FunctionResult
import org.slf4j.*
/**
 * Built-in external-signal wait function (`builtin:waitForSignal`).
 *
 * Suspends (pauses) the current workflow node until a matching signal is
 * delivered through the [SignalBroker] SPI. Once the signal arrives, the
 * configured `signal.payload` is emitted as the node output so downstream
 * nodes can consume the injected data.
 *
 * Typical use cases:
 * - **Human approval** — workflow waits for `"approve"` / `"reject"` signals
 *   fired by an operator clicking a button in the admin UI.
 * - **Third-party callback** — an external webhook POSTs back through the
 *   Signal REST API; the workflow resumes with the webhook body as payload.
 * - **Ops manual override** — SRE injects data via CLI to unstick a blocked
 *   workflow during incident response.
 *
 * The function relies on the virtual-thread-friendly [SignalBroker] which
 * (in Fluxion's default implementation) parks the coroutine without holding
 * an OS thread, so thousands of waiting workflows impose negligible overhead.
 *
 * Node params:
 * - `signalName` — name of the signal to wait for (required).
 * - `timeoutMs`  — max wait in milliseconds; `0` (default) = unlimited.
 *                  Expiry throws [SignalTimeoutException] which the workflow
 *                  author can catch via error-handling edges.
 */
class WaitForSignalFunction(
    private val signalBroker: SignalBroker
) : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:waitForSignal"

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
}

/**
 * Thrown by [WaitForSignalFunction] when the configured timeout elapses
 * without a matching signal arriving on the broker.
 *
 * Workflow authors typically wire a conditional next-edge on the node's
 * exception class to route time-specific follow-up behavior (e.g. send
 * a reminder, escalate to a different approver, or fail the workflow).
 */
class SignalTimeoutException(message: String) : RuntimeException(message)
