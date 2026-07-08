package com.fluxion.core.engine

import com.fluxion.core.signal.SignalBroker
import java.util.concurrent.ConcurrentHashMap

/**
 * Snapshot of a currently running workflow execution.
 *
 * Served by the admin console Query API and used by [SignalBroker] lookups
 * to route external signals back to the correct waiters.
 */
data class RunningExecution(
    val executionId: String,
    val workflowId: String,
    val workflowName: String,
    val version: Int,
    val startTime: Long,
    val inputs: Map<String, Any>,
    val nodeIds: List<String>
)

/**
 * In-memory visibility store for currently running executions.
 *
 * Mirrors the Temporal "visibility store" role at a smaller scale:
 *  - Start/end lifecycle events from [DagExecutor]
 *  - Admin Query API (get by ID / list all / count)
 *  - Coordinated cleanup with [SignalBroker] on execution end
 *
 * Thread safety is achieved through [ConcurrentHashMap]; the data set is
 * bounded by the number of concurrently executing DAGs (which also matches
 * the lifecycle of in-memory WAIT promises).
 */
class ExecutionTracker(
    private val signalBroker: SignalBroker? = null
) {

    private val running = ConcurrentHashMap<String, RunningExecution>()

    /** Invoked by [DagExecutor.execute] just before the first node runs. */
    fun trackStart(
        executionId: String,
        workflowId: String,
        workflowName: String,
        version: Int,
        inputs: Map<String, Any>,
        nodeIds: List<String>
    ) {
        running[executionId] = RunningExecution(
            executionId = executionId,
            workflowId = workflowId,
            workflowName = workflowName,
            version = version,
            startTime = System.currentTimeMillis(),
            inputs = inputs,
            nodeIds = nodeIds
        )
    }

    /** Invoked from the `finally` block of [DagExecutor.executeDecorated]. */
    fun trackEnd(executionId: String) {
        running.remove(executionId)
        signalBroker?.cleanup(executionId)
    }

    /** Fetch a single running execution by ID, or `null`. */
    fun getRunning(executionId: String): RunningExecution? = running[executionId]

    /** Immutable snapshot of every currently tracked execution. */
    fun listRunning(): List<RunningExecution> = running.values.toList()

    /** Presence check (used by signal delivery fast-path). */
    fun isRunning(executionId: String): Boolean = running.containsKey(executionId)

    /** Number of currently tracked executions (metrics / health probes). */
    fun size(): Int = running.size
}
