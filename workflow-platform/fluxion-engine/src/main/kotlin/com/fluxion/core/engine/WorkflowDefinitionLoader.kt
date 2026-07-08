package com.fluxion.core.engine

import com.fluxion.core.model.WorkflowDefinition

/**
 * SPI used to resolve a [WorkflowDefinition] by its stable ID.
 *
 * Implemented in adapters by e.g. `WfDefinitionService` (persistence-backed)
 * or a `DefinitionProvider` (memory/config-backed). The primary consumer is
 * [SubWorkflowExecutor], which is instantiated through the Spring
 * auto-config so the correct wired implementation is injected.
 *
 * Returns `null` when the requested workflow cannot be resolved — callers
 * are responsible for surfacing that condition as an appropriate error.
 */
fun interface WorkflowDefinitionLoader {

    /**
     * Look up a workflow definition by ID.
     *
     * @param workflowId workflow unique identifier
     * @return the definition, or `null` if not found
     */
    fun load(workflowId: String): WorkflowDefinition?

    companion object {
        /** Always-returns-null stub (useful for tests / single-workflow engines). */
        val NOOP = WorkflowDefinitionLoader { null }
    }
}
