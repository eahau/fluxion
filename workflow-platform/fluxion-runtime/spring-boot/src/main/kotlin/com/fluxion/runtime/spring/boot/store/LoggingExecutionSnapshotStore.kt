package com.fluxion.runtime.spring.boot.store

import com.fluxion.core.value.EngineResult
import com.fluxion.runtime.core.spi.ExecutionSnapshotStore
import org.slf4j.*
/**
 * Default stateless [ExecutionSnapshotStore] implementation.
 *
 * Emits structured one-liners to SLF4J logger `WorkflowExecutionSnapshot`:
 * - INFO line for successful executions
 * - WARN line for failed executions (with `error=` field appended)
 *
 * Zero external dependencies — ideal for the stateless-sidecar deployment
 * pattern where the Worker lives next to a legacy Java 8 service and a full
 * persistence tier is undesired. Override via `@ConditionalOnMissingBean`
 * when durable snapshots (Audit UI, replay) are needed.
 */
class LoggingExecutionSnapshotStore : ExecutionSnapshotStore {

    private val log = LoggerFactory.getLogger("WorkflowExecutionSnapshot")

    /**
     * Log a single snapshot line; outcome level reflects success flag.
     */
    override fun save(result: EngineResult, workflowId: String, version: Int) {
        if (result.success) {
            log.info {
                "Snapshot saved workflowId=$workflowId version=$version " +
                        "executionId=${result.executionId}"
            }
        } else {
            log.warn {
                "Snapshot saved workflowId=$workflowId version=$version " +
                        "executionId=${result.executionId} error=${result.errorMsg}"
            }
        }
    }
}
