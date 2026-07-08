package com.fluxion.core.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fluxion.core.enums.Protocol
import com.fluxion.core.util.DagTopology

/**
 * Immutable description of a workflow that the engine can execute.
 *
 * Mirrors the `wf_definition` table schema published by the admin
 * console. Serialised payloads from the admin are JSON (stored in
 * `nodes_config`) and the runtime rehydrates them into this data class
 * before handing them to [WorkflowEngine].
 */
data class WorkflowDefinition(
    var id: String = "",
    var name: String = "",
    /** UI / grouping category (free-form, e.g. `BUSINESS`, `INTEGRATION`). */
    var category: String = "BUSINESS",
    /** Protected-flag: `1` means only operators with elevated rights can edit. */
    var isProtectedFlag: Int = 0,
    /** Transport protocol used by inbound adapters (HTTP / Dubbo / gRPC / Kafka / INTERNAL). */
    var protocol: Protocol = Protocol.HTTP,
    /** Protocol-specific address: HTTP=path, Dubbo=serviceMethod, gRPC=method, Kafka=topic. */
    var method: String? = null,
    var path: String? = null,
    /** Additional per-protocol knobs (gRPC package, Kafka consumer group, ...). */
    var protocolConfig: Map<String, Any>? = null,
    /** Inline JSON schema for the workflow input. */
    var inputSchema: Any? = null,
    /**
     * Schema format for `inputSchema` — `json-schema`, `protobuf`, `avro`, ...
     *
     * When `null` the engine treats the payload as legacy JSON Schema (default
     * behaviour for admin console versions that do not set the field).
     */
    var inputSchemaFormat: String? = null,
    /** Inline JSON schema for the workflow output. */
    var outputSchema: Any? = null,
    /** Schema format for `outputSchema`; same semantics as [inputSchemaFormat]. */
    var outputSchemaFormat: String? = null,
    /** The DAG of nodes that makes up the workflow body. */
    var nodes: List<WorkflowNode> = emptyList(),
    /** Workflow-level params merged with every node's [NodeInput.workflowInput]. */
    var globalParams: Map<String, Any>? = null,
    /** Optional DB-transaction wrapping (isolation, propagation, timeout). */
    var transactionConfig: TransactionConfig? = null,
    /**
     * Decorator refs applied to the *whole* workflow (not individual nodes).
     *
     * Typical entries: `["workflow:transaction", "workflow:lock", "workflow:ratelimit"]`.
     * Per-node decorators run inside the scope of workflow-level decorators.
     */
    var workflowDecorators: List<String>? = null,
    /** Per-workflow-decorator parameter map: `decoratorName → { key → value }`. */
    var workflowDecoratorParams: Map<String, Map<String, Any>>? = null,
    /** `1` enables Saga compensation on failure walk-back. */
    var sagaEnabled: Int = 0,
    /** Draft / Published / Deprecated lifecycle flag (admin console only). */
    var status: String = "DRAFT",
    var version: Int = 0,
    var createdBy: String? = null,
    /** Visibility scope: PLATFORM / PRIVATE / MARKETPLACE. */
    var scope: String = "PRIVATE",
    /** Owning app / tenant group (required when scope == PRIVATE). */
    var appGroup: String? = null,
    /**
     * Global error handler reference invoked when a node fails and its
     * own [ErrorStrategy] does not fully resolve the problem.
     *
     * Typical value: `"wf:errorWrapper"` — wraps the underlying error in
     * a user-friendly envelope and writes the full stack to the audit log.
     */
    var errorHandlerRef: String? = null,
    /** All triggers that can launch an instance of this definition. */
    var triggers: List<WorkflowTrigger> = emptyList()
) {
    /**
     * Topologically-sorted node ids derived from [nodes] and
     * [WorkflowNode.dependsOn].
     *
     * Memoised (lazy) because the same definition is reused across
     * thousands of executions; JSON deserialisation does not re-run the
     * sort and the sorted view survives cache round-trips because the
     * property is annotated `@get:JsonIgnore`. Returns `emptyList()`
     * if the graph has a cycle (callers that require strict correctness
     * should call [DagTopology.validate] explicitly).
     */
    @get:JsonIgnore
    val sortedNodeIds: List<String> by lazy { DagTopology.topologicalSort(nodes) ?: emptyList() }

    /** True when Saga compensation walk-back is enabled. */
    fun isSagaEnabled(): Boolean = sagaEnabled == 1
    /** True when the definition is marked read-only for non-operators. */
    fun isProtected(): Boolean = isProtectedFlag == 1
}
