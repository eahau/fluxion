package com.fluxion.core.engine

import com.fluxion.core.model.WorkflowDefinition

/**
 * 工作流定义加载器 SPI — 供子工作流节点按 ID 加载目标工作流定义。
 *
 * 由控制面（WfDefinitionService）和运行面（DefinitionProvider）分别实现，
 * 通过 Spring 注入到 [SubWorkflowExecutor]。
 *
 * 未注册时，子工作流节点将抛出 IllegalStateException。
 */
fun interface WorkflowDefinitionLoader {

    /**
     * 根据工作流 ID 加载定义。
     *
     * @param workflowId 目标工作流 ID
     * @return 工作流定义，未找到时返回 null
     */
    fun load(workflowId: String): WorkflowDefinition?

    companion object {
        /** 空实现：始终返回 null */
        val NOOP = WorkflowDefinitionLoader { null }
    }
}
