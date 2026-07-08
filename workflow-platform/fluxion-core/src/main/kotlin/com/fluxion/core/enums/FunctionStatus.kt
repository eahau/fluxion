package com.fluxion.core.enums

/**
 * Terminal result status of a single function invocation.
 *
 * Recorded in [com.fluxion.core.value.NodeExecutionRecord] and persisted by
 * [com.fluxion.core.log.ExecutionLogStore] for audit and replay.
 */
enum class FunctionStatus {
    /** Function completed without throwing. */
    SUCCESS,
    /** Function threw — retry/fallback also exhausted or not configured. */
    FAILED,
    /** Execution was deliberately skipped (e.g. error strategy SKIP). */
    SKIPPED
}
