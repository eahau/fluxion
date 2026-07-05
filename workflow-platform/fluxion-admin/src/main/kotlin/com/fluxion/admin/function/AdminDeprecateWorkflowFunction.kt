package com.fluxion.admin.function

import com.fluxion.admin.mapper.WorkflowMapper
import com.fluxion.admin.service.WfDefinitionService
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult
import org.slf4j.LoggerFactory
import org.slf4j.info
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Admin 自举工作流节点函数：下线工作流定义。
 *
 * 由内置工作流 [ADMIN_WORKFLOW_DEPRECATE_WORKFLOW_ID] 调用，
 * 将工作流下线逻辑从 Controller 下沉到工作流引擎执行。
 *
 * 作为 Spring Bean 注册，由 [com.fluxion.di.spring.SpringFunctionInstanceProvider]
 * 从应用上下文中直接获取实例。
 */
@Component
class AdminDeprecateWorkflowFunction @Autowired constructor(
    private val workflowService: WfDefinitionService,
    private val workflowMapper: WorkflowMapper
) : WorkflowFunction<Any?> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val workflowId = input.requireWfInput<String>("workflowId")

        log.info { "Admin workflow deprecating workflow [$workflowId]" }

        val deprecated = workflowService.deprecate(workflowId)
        val dto = workflowMapper.toDto(deprecated)

        return FunctionResult.success(dto)
    }

    override fun meta(): FunctionMeta = FunctionMeta.builder(FUNCTION_REF)
        .description("Admin self-hosted workflow: deprecate a workflow definition")
        .build()

    companion object {
        const val FUNCTION_REF = "admin:deprecateWorkflow"
    }
}
