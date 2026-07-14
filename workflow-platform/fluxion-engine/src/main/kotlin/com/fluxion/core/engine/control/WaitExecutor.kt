package com.fluxion.core.engine.control

import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.signal.Signal
import com.fluxion.core.signal.SignalBroker
import com.fluxion.core.value.NodeExecutionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.*

/**
 * Executor for WAIT-type workflow nodes.
 *
 * Suspends the current coroutine until an external signal is delivered via
 * [SignalBroker] or the configured timeout elapses. Typically used for
 * human approval steps, asynchronous callbacks, or external system
 * notifications that must complete before the workflow continues.
 *
 * Node params read from [WorkflowNode.params]:
 *  - `signalName`: logical signal name to wait on (default `"default"`)
 *  - `waitTimeoutMs`: timeout in milliseconds; `0` means wait forever
 *  - `timeoutOutput`: value returned as node output on timeout; if `null`
 *    a default `{signalName, status="TIMEOUT"}` map is produced
 *
 * Signals are delivered out-of-band through the admin signal API:
 * ```
 * POST /api/admin/executions/{executionId}/signal
 * { "signalName": "approval", "data": { "approved": true } }
 * ```
 */
class WaitExecutor(
    private val signalBroker: SignalBroker
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Execute a WAIT node.
     *
     * Blocks the calling coroutine (on the IO dispatcher) until either the
     * signal is received or the timeout fires. On signal the payload is
     * returned as the node output; on timeout the configured
     * `timeoutOutput` (or a default TIMEOUT marker) is used.
     *
     * @param node WAIT node definition carrying signal/timeout params
     * @param state current execution state (provides [ExecutionMeta.executionId])
     * @return success record carrying the signal payload or timeout marker
     */
    suspend fun execute(
        node: WorkflowNode,
        state: ImmutableExecutionState
    ): NodeExecutionRecord {
        val startNano = System.nanoTime()
        val params = node.params ?: emptyMap()

        val signalName = params["signalName"]?.toString() ?: "default"
        val waitTimeoutMs = (params["waitTimeoutMs"] as? Number)?.toLong() ?: 0L
        val timeoutOutput = params["timeoutOutput"]

        val executionId = state.meta.executionId

        log.info { "Wait node [${node.name}]: waiting for signal '$signalName' (executionId=$executionId, timeout=${waitTimeoutMs}ms)" }

        // Bridge CompletableFuture (SignalBroker API) to coroutines via IO dispatcher.
        val signal: Signal? = withContext(Dispatchers.IO) {
            signalBroker.awaitSignal(executionId, signalName, waitTimeoutMs)
        }

        val elapsedMs = elapsedMs(startNano)

        return if (signal != null) {
            log.info { "Wait node [${node.name}]: received signal '$signalName', duration=${elapsedMs}ms" }
            NodeExecutionRecord.success(
                node = node,
                output = signal.payload ?: mapOf("signalName" to signal.name, "status" to "RECEIVED"),
                durationMs = elapsedMs
            )
        } else {
            log.info { "Wait node [${node.name}]: signal '$signalName' timed out after ${waitTimeoutMs}ms" }
            if (timeoutOutput != null) {
                NodeExecutionRecord.success(node, timeoutOutput, elapsedMs)
            } else {
                NodeExecutionRecord.success(
                    node = node,
                    output = mapOf("signalName" to signalName, "status" to "TIMEOUT"),
                    durationMs = elapsedMs
                )
            }
        }
    }

    private fun elapsedMs(startNano: Long): Long = (System.nanoTime() - startNano) / 1_000_000L
}
