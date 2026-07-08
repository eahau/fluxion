package com.fluxion.builtin.db.transaction

import com.fluxion.decorator.decorator.WorkflowDecorator
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.decoratorParams
import com.fluxion.core.value.EngineResult

/**
 * Workflow-level [WorkflowDecorator] that opens a single transactional
 * boundary spanning every node in a workflow execution.
 *
 * Combined with per-node [TransactionDecorator] using propagation `REQUIRED`
 * (the default), this lets multiple DB-writing nodes participate atomically:
 * any subsequent node failing rolls back the writes of all prior nodes.
 *
 * Config precedence (highest first):
 * 1. [WorkflowDefinition.transactionConfig] — explicit typed model object.
 * 2. `workflow:transaction` entry inside [WorkflowDefinition.workflowDecoratorParams].
 *
 * Supported decorator params (only when `transactionConfig` is absent):
 * - `dataSource` — logical DS name, default `"default"`
 * - `propagation` — `"REQUIRED"` (default), `REQUIRES_NEW`, etc.
 * - `isolation` — `"DEFAULT"`, `"READ_COMMITTED"`, ...
 * - `timeout` — seconds, `-1` = unlimited (default)
 * - `readOnly` — boolean hint (default `false`)
 */
class WorkflowTransactionDecorator(
    private val transactionManager: TransactionManager
) : WorkflowDecorator {

    override fun name(): String = "workflow:transaction"

    override suspend fun decorate(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        execute: suspend () -> EngineResult
    ): EngineResult {
        val (dsName, definition) = TransactionSupport.resolveDefinition(
            params = def.decoratorParams(name()),
            name = def.name,
            transactionConfig = def.transactionConfig
        )
        return TransactionSupport.executeSuspending(transactionManager, dsName, definition, execute)
    }
}
