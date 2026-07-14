package com.fluxion.core.enums

/**
 * Core enumeration types used across the Fluxion workflow engine.
 *
 * This file defines the error handling strategy that a workflow node applies
 * when its execution fails.
 */

/**
 * Strategy for handling node execution failures.
 *
 * Determines how [com.fluxion.core.engine.DagExecutor] reacts when a
 * [com.fluxion.core.model.WorkflowNode] throws an exception.
 */
enum class ErrorStrategy {
    /** Fail immediately — propagate error and halt downstream nodes. */
    FAIL,
    /** Skip the node and continue with successors (output is null). */
    SKIP,
    /** Execute the configured fallback function and continue. */
    FALLBACK,
    /** Retry up to [WorkflowNode.retryCount] times, then FAIL. */
    RETRY
}
