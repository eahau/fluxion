package com.fluxion.runtime.core.provider

import com.fluxion.core.model.WorkflowDefinition

/**
 * 工作流定义提供者 SPI — 适配器层路由查找的最小契约
 *
 * 作为 [WorkflowDefinition] 的只读领域视图：
 * - [get]：按 workflowId 获取已解析的工作流定义
 * - [findByBinding]：反向索引（protocol + bindKey → workflowId）
 *
 * 此接口仅依赖 [fluxion-core] 类型，与配置中心无关。
 * 配置中心只是其中一种实现来源（见 ConfigBackedDefinitionProvider），
 * 控制面（WfDefinitionService）则直接从 DB 提供。
 */
interface DefinitionProvider {
    fun get(key: String): WorkflowDefinition?
    fun findByBinding(protocol: String, bindKey: String): String?
}
