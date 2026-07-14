package com.fluxion.core.exception

import com.fluxion.core.value.SideEffect

/**
 * Exception hierarchy for the Fluxion workflow engine.
 *
 * Every public exception carries a stable `errorCode` of the form
 * `WF-CATEGORY-NNN` which is:
 *  - Logged and attached to error responses for easier operator lookup.
 *  - Cross-referenced in the admin console error code reference.
 *  - Used by the HTTP/RPC adapter layers to map to the correct HTTP status /
 *    RPC error code.
 *
 * Categories currently defined:
 *  - `WF-VALIDATION-NNN`  — request / definition validation failures
 *  - `WF-NODE-NNN`        — per-node execution errors
 *  - `WF-DAG-NNN`         — graph topology / dependency errors
 *  - `WF-REGISTRY-NNN`    — function / workflow / decorator lookup failures
 *  - `WF-SAGA-NNN`        — Saga compensation and transaction errors
 *  - `WF-TX-NNN`          — local transaction errors
 *  - `WF-EXTERNAL-NNN`    — external function gateway errors
 *  - `WF-DECORATOR-NNN`   — decorator (rate-limit, lock, tracing) errors
 *  - `WF-500-NNN`         — meta-workflow / bootstrap-level internal errors
 */
open class WorkflowException(
    /** Stable error identifier (e.g. `WF-VALIDATION-001`). */
    val errorCode: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * WF-VALIDATION-001 — invalid request parameters.
 *
 * Thrown when the caller-supplied arguments fail basic structural checks
 * (missing required field, wrong type, out of range, etc.).
 */
class InvalidParamException : WorkflowException {
    constructor(message: String) : super("WF-VALIDATION-001", message)
    constructor(errors: List<String>) : super("WF-VALIDATION-001", "Invalid parameters: `$errors")
}

/**
 * WF-VALIDATION-002 — input/output does not match the declared JSON / Protobuf / Avro schema.
 *
 * @property validationErrors machine-readable list of validation error details.
 */
class SchemaValidationException(
    message: String,
    val validationErrors: List<String> = listOf(message)
) : WorkflowException("WF-VALIDATION-002", message)

/**
 * WF-VALIDATION-003 — [WorkflowDefinition] is structurally invalid (missing nodes,
 * bad references, etc.).
 */
class InvalidWorkflowDefinitionException(message: String) :
    WorkflowException("WF-VALIDATION-003", message)

/**
 * WF-NODE-001 — node execution failed.
 *
 * The primary constructor is private; callers must use the secondary constructor
 * which ensures the node name is embedded in the message, or [timeout] for
 * timeout-originated errors.
 *
 * @property nodeName id/name of the failing node; null for timeouts that fire
 *                    before node resolution completes.
 */
class WorkflowNodeException private constructor(
    val nodeName: String?,
    message: String,
    cause: Throwable?
) : WorkflowException("WF-NODE-001", message, cause) {

    /** Wrap a cause thrown during node execution; used by `handleNodeError`. */
    constructor(nodeName: String, cause: Throwable?) : this(
        nodeName,
        "Node [$nodeName] execution failed: ${cause?.message ?: "unknown"}",
        cause
    )

    companion object {
        /** Build a timeout variant (node may not yet be resolved). */
        @JvmStatic
        fun timeout(message: String, cause: Throwable?): WorkflowNodeException =
            WorkflowNodeException(null, message, cause)
    }
}

/**
 * WF-NODE-002 — maximum retries exhausted for a failing node.
 *
 * The engine sets [lastCause] to the most recent throwable so that operators
 * can see the underlying reason in logs/traces.
 */
class RetryExhaustedException(nodeName: String, maxRetries: Int, lastCause: Throwable?) :
    WorkflowException(
        "WF-NODE-002",
        "Node [$nodeName] exhausted $maxRetries retries: ${lastCause?.message ?: "unknown"}",
        lastCause
    )

/**
 * WF-NODE-003 — the user-configured fallback function itself threw.
 */
class FallbackFailedException(nodeName: String, cause: Throwable?) :
    WorkflowException(
        "WF-NODE-003",
        "Fallback of node [$nodeName] failed: ${cause?.message ?: "unknown"}",
        cause
    )

/**
 * WF-NODE-004 — node timed out after [timeoutMs] milliseconds.
 */
class NodeTimeoutException(nodeName: String, timeoutMs: Int) :
    WorkflowException("WF-NODE-004", "Node [$nodeName] timed out after ${timeoutMs}ms")

/**
 * WF-DAG-001 — DAG contains a cycle; topological sort is impossible.
 */
class CyclicDependencyException(workflowId: String) :
    WorkflowException("WF-DAG-001", "Cyclic dependency detected in workflow: `$workflowId")

/**
 * WF-DAG-002 — a node could not run because a declared dependency's output
 * was not yet produced.
 *
 * This normally indicates a race or a graph construction bug.
 */
class DependencyNotReadyException(nodeId: String, depNodeId: String) :
    WorkflowException("WF-DAG-002", "Node [$nodeId] dependency [$depNodeId] output not ready")

/**
 * WF-DAG-003 — the same node id appears more than once in the definition.
 */
class DuplicateNodeIdException(nodeId: String) :
    WorkflowException("WF-DAG-003", "Duplicate node id: `$nodeId")

/**
 * WF-REGISTRY-001 — requested function ref is not registered.
 */
class FunctionNotFoundException(functionRef: String) :
    WorkflowException("WF-REGISTRY-001", "Function not found: `$functionRef")

/**
 * WF-REGISTRY-002 — requested workflow id does not exist in the store.
 */
class WorkflowNotFoundException(workflowId: String) :
    WorkflowException("WF-REGISTRY-002", "Workflow not found: `$workflowId")

/**
 * WF-REGISTRY-003 — a decorator referenced from the node definition was not
 * registered in the decorator registry.
 */
class DecoratorNotFoundException(decoratorRef: String) :
    WorkflowException("WF-REGISTRY-003", "Decorator not found: `$decoratorRef")

/**
 * WF-SAGA-001 — Saga pattern execution (forward or compensation) failed.
 *
 * When pending side-effects are available (i.e. forward phase produced
 * effects before the failure) they are attached via [pendingSideEffects]
 * so the caller can decide whether to perform out-of-band compensation.
 *
 * @property pendingSideEffects side effects generated before the failure;
 *                              empty if the cause is from compensation itself.
 */
class SagaExecutionException : WorkflowException {
    val pendingSideEffects: List<SideEffect>

    constructor(message: String, pendingSideEffects: List<SideEffect>) : super("WF-SAGA-001", message) {
        this.pendingSideEffects = pendingSideEffects
    }

    constructor(message: String, cause: Throwable?) : super("WF-SAGA-001", message, cause) {
        this.pendingSideEffects = emptyList()
    }
}

/**
 * WF-TX-001 — local JDBC/Spring-managed transaction rolled back or could not start.
 */
class WorkflowTransactionException : WorkflowException {
    constructor(message: String, cause: Throwable?) : super("WF-TX-001", message, cause)
    constructor(message: String) : super("WF-TX-001", message)
}

/**
 * WF-EXTERNAL-001 — call through the external function gateway failed
 * (network error, non-2xx status, protocol error, etc.).
 */
class ExternalFunctionException(functionRef: String, cause: Throwable?) :
    WorkflowException(
        "WF-EXTERNAL-001",
        "External function [$functionRef] call failed: ${cause?.message ?: "unknown"}",
        cause
    )

/**
 * WF-DECORATOR-001 — rate limiter rejected the invocation.
 *
 * Thrown from the rate-limit decorator when the quota is exhausted and
 * `failOnBlocked` is enabled.
 */
class RateLimitExceededException(message: String) : WorkflowException("WF-DECORATOR-001", message)

/**
 * WF-DECORATOR-002 — distributed lock acquisition failed.
 *
 * Thrown from the distributed-lock decorator (or programmatic lock helpers)
 * when the lock cannot be acquired within `waitMillis` and
 * `LockParams.failOnLocked` is true.
 */
class LockAcquisitionException(message: String) : WorkflowException("WF-DECORATOR-002", message)

/**
 * WF-500-012 — admin meta-workflow bootstrap failure.
 *
 * Reserved for scenarios where the admin console's internal workflows (used
 * for schema publishing, function approval, etc.) fail to start — generally
 * a deployment / configuration problem rather than a user-visible issue.
 */
class MetaWorkflowBootstrapException : WorkflowException {
    constructor(message: String) : super("WF-500-012", message)
    constructor(message: String, cause: Throwable?) : super("WF-500-012", message, cause)
}
