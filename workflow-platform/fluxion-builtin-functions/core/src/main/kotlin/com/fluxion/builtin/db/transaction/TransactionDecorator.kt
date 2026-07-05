package com.fluxion.builtin.db.transaction

import com.fluxion.core.decorator.NodeDecorator
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.*

/**
 * 事务装饰器。
 *
 * 通过 [TransactionManager] SPI 开启事务边界，屏蔽底层 JDBC / Spring / JTA 实现差异。
 * 传播行为通过装饰器参数 `propagation` 配置，默认 `REQUIRED`。
 */
class TransactionDecorator(private val transactionManager: TransactionManager) : NodeDecorator {

    override fun name(): String = "transaction"

    override fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any> {
        return WorkflowFunction { input ->
            val (dsName, definition) = TransactionSupport.resolveDefinition(
                params = node.decoratorParams(name()),
                name = node.name.ifBlank { null }
            )
            TransactionSupport.execute(transactionManager, dsName, definition) { function.apply(input) }
        }
    }
}
