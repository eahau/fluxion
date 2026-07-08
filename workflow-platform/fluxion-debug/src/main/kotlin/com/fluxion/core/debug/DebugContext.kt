package com.fluxion.core.debug

import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.mock.MockConfig
import com.fluxion.core.mock.MockRule
import com.fluxion.core.value.NodeExecutionRecord

/**
 * Mutable stepping state used during an interactive debug session.
 *
 * A [DebugContext] is the in-memory, mutable equivalent of the serializable
 * [DebugSnapshot]. The two are freely convertible:
 *
 *  * `DebugSnapshot.restore(snap, def) -> DebugContext` at session start / resume.
 *  * `context.toSnapshot(...) -> DebugSnapshot` to persist between user requests.
 *
 * Mock rules and the next-node pointer are intentionally mutable: the
 * debugger UI edits them in-place between [DebugService.step] calls.
 */
class DebugContext(
    /** Accumulated execution state (inputs + every completed node's output). */
    val executionState: ImmutableExecutionState,
    /** Active mock configuration; edited in-place by the debugger UI. */
    var mockConfig: MockConfig,
    /** Node ids that cause stepping to pause before execution. */
    val breakpoints: Set<String>,
    /** Ordered trace of every node that has already executed in this session. */
    val traces: List<NodeExecutionRecord>,
    /** Index into [WorkflowDefinition.nodes] pointing at the *next* node to execute. */
    var nextNodeIndex: Int
) {
    /** Convenience: move `nextNodeIndex` forward by one (used from UI-driven overrides). */
    fun advanceNextNode() { nextNodeIndex++ }

    /**
     * Upsert a [MockRule] into `mockConfig.rules`: replaces a rule with the
     * same `id`, or appends it if new.
     */
    fun addMockRule(rule: MockRule) {
        val rules = mockConfig.rules.toMutableList()
        val idx = rules.indexOfFirst { it.id == rule.id }
        if (idx >= 0) {
            rules[idx] = rule
        } else {
            rules.add(rule)
        }
        this.mockConfig = mockConfig.copy(rules = rules)
    }

    /** Remove a previously added mock rule by id (no-op if absent). */
    fun removeMockRule(ruleId: String) {
        val rules = mockConfig.rules.filterNot { it.id == ruleId }
        this.mockConfig = mockConfig.copy(rules = rules)
    }

    /** Globally enable / disable mock application without discarding the rules list. */
    fun setMockEnabled(enabled: Boolean) {
        this.mockConfig = mockConfig.copy(enabled = enabled)
    }

    /**
     * Freeze this context into an immutable [DebugSnapshot] that can be
     * serialised and passed back to the service on the next step request.
     */
    fun toSnapshot(workflowId: String, workflowVersion: Int, pausedAtNodeId: String?): DebugSnapshot =
        DebugSnapshot(
            workflowId = workflowId,
            workflowVersion = workflowVersion,
            executionState = executionState,
            traces = traces,
            breakpoints = breakpoints,
            mockConfig = mockConfig,
            pausedAtNodeId = pausedAtNodeId,
            nextNodeIndex = nextNodeIndex
        )
}

/**
 * Serializable snapshot of an active debug session; wire format passed
 * between the admin console UI and [DebugService].
 *
 * Snapshot-version pairs are *immutable*: each step / continue call produces
 * a fresh [DebugSnapshot], leaving the caller's copy untouched. This makes it
 * safe to cache or compare snapshots in the UI.
 */
data class DebugSnapshot(
    /** Owning workflow id. */
    val workflowId: String,
    /** Workflow version at the time the snapshot was captured. */
    val workflowVersion: Int,
    /** Accumulated state at the last completed node. */
    val executionState: ImmutableExecutionState,
    /** Node execution records appended so far. */
    val traces: List<NodeExecutionRecord>,
    /** Breakpoint node ids that trigger a pause *before* running the node. */
    val breakpoints: Set<String>,
    /** Configured mock rules (may be empty / disabled). */
    val mockConfig: MockConfig,
    /** Non-null when stepping paused because the *next* node is a breakpoint. */
    val pausedAtNodeId: String?,
    /** Index of the next node to execute inside the owning [WorkflowDefinition]. */
    val nextNodeIndex: Int
) {
    companion object {
        /**
         * Restore a live mutable [DebugContext] from a stored snapshot.
         *
         * @throws IllegalStateException if the snapshot's workflow version no
         *                               longer matches the freshly loaded definition.
         */
        @JvmStatic
        fun restore(snap: DebugSnapshot, def: WorkflowDefinition): DebugContext {
            check(snap.workflowVersion == def.version) {
                "Workflow version mismatch: snapshot=${snap.workflowVersion}, definition=${def.version}"
            }
            return DebugContext(
                snap.executionState, snap.mockConfig,
                snap.breakpoints, snap.traces, snap.nextNodeIndex
            )
        }
    }
}
