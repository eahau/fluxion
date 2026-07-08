package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Immutable post-execution snapshot of a completed workflow invocation.
 *
 * Stored asynchronously by the engine after execution finishes (success or failure).
 * Contains the original inputs, per-node outputs, and the full execution trace
 * (NodeExecutionRecord list). UI uses these rows for execution history / debug replay.
 * Separate from runtime execution log store to allow long-term retention and SQL queries.
 *
 * Source table: `wf_execution_snapshot`.
 *
 * Collaborates with: WfExecutionSnapshotRepository, WfExecutionSnapshotService,
 * WorkflowEngine (writer), ExecutionsController (reader).
 */
@Entity
@Table(name = "wf_execution_snapshot")
class WfExecutionSnapshot : BaseEntity() {

    /** Engine-level execution UUID, maps to column `execution_id`. */
    @Column(name = "execution_id", nullable = false, unique = true, length = 64)
    var executionId: String = ""

    /** Workflow that was executed, maps to column `workflow_id`. */
    @Column(name = "workflow_id", nullable = false, length = 64)
    var workflowId: String = ""

    /** Snapshot of workflow.version at execution time, maps to column `workflow_version`. */
    @Column(name = "workflow_version", nullable = false)
    var workflowVersion: Int = 1

    /** True if the workflow completed without error, maps to column `success`. */
    @Column(name = "success", nullable = false)
    var success: Boolean = true

    /** Original workflow inputs Map<String,Object> JSON, maps to column `inputs_json`. */
    @Column(name = "inputs_json", columnDefinition = "MEDIUMTEXT")
    var inputsJson: String? = null

    /** Map<nodeId, outputValue> JSON for every completed node, maps to column `node_outputs_json`. */
    @Column(name = "node_outputs_json", columnDefinition = "MEDIUMTEXT")
    var nodeOutputsJson: String? = null

    /** List<NodeExecutionRecord> trace JSON for timeline/debug UI, maps to column `trace_json`. */
    @Column(name = "trace_json", columnDefinition = "MEDIUMTEXT")
    var traceJson: String? = null

    /** Root error message if success=false, maps to column `error_msg`. */
    @Column(name = "error_msg", length = 512)
    var errorMsg: String? = null
}
