package com.fluxion.builtin.db.transaction

import com.fluxion.decorator.decorator.NodeDecorator
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.WorkflowNode

/**
 * Per-node [NodeDecorator] that wraps a single function call in a
 * transactional boundary.
 *
 * The decorator reads `dataSource`, `propagation`, `isolation`, `timeout`,
 * and `readOnly` from the node's decorator params (under key `"transaction"`).
 * Default propagation is `REQUIRED` which means the node will automatically
 * join an existing workflow-level transaction opened by
 * [WorkflowTransactionDecorator] if one exists.
 *
 * The actual transactional work is delegated to the pluggable
 * [TransactionManager] SPI (Spring adapter by default).
 */
class TransactionDecorator(private val transactionManager: TransactionManager) : NodeDecorator {

    override fun name(): String = "transaction"

    override fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any> {
        return WorkflowFunction { input ->
            val (dsName, definition) = TransactionSupport.resolveDefinition(
                params = node.decoratorParams?.get(name()),
                name = node.name.ifBlank { null }
            )
            TransactionSupport.execute(transactionManager, dsName, definition) { function.apply(input) }
        }
    }
}
