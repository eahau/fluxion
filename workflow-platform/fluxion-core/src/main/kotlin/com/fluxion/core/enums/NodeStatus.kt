package com.fluxion.core.enums

/**
 * Node execution status.
 */
enum class NodeStatus {
    /** Normal completion */
    SUCCESS,
    /** Failure (error strategy FAIL or retries exhausted) */
    FAILED,
    /** Skipped (error strategy SKIP) */
    SKIPPED,
    /** Fallback succeeded (error strategy FALLBACK) */
    FALLBACK,
    /** Retrying (transient state, eventually SUCCESS or FAILED) */
    RETRY
}
