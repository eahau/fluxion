package com.fluxion.builtin.db.transaction

import com.fluxion.core.decorator.WorkflowDecorator
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.decoratorParams
import com.fluxion.core.value.EngineResult

/**
 * 工作流级事务装饰器。
 *
 * 为整段工作流执行开启事务边界，使多个节点共享同一事务上下文。
 * 节点级 [TransactionDecorator] 的 REQUIRED 传播行为会自然加入该全局事务。
 *
 * 配置来源（按优先级）：
 * 1. [WorkflowDefinition.transactionConfig]（若存在，则作为完整事务定义）
 * 2. [WorkflowDefinition.workflowDecoratorParams] 中 "workflow:transaction" 键的参数
 *
 * 可配置参数（当未使用 transactionConfig 时生效）：
 * - `dataSource`: 数据源名称，默认 "default"
 * - `propagation`: 传播行为，默认 "REQUIRED"
 * - `isolation`: 隔离级别，默认 ""（由底层决定）
 * - `timeout`: 超时时间（秒），默认 -1（无限制）
 * - `readOnly`: 是否只读，默认 false
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
