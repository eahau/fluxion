package com.fluxion.admin.function

import com.fluxion.admin.generated.model.PublishTarget
import com.fluxion.admin.mapper.WorkflowMapper
import com.fluxion.admin.service.WfDefinitionService
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult
import org.slf4j.LoggerFactory
import org.slf4j.info
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Admin 自举工作流节点函数：发布工作流定义。
 *
 * 由内置工作流 [ADMIN_WORKFLOW_PUBLISH_WORKFLOW_ID] 调用，
 * 将工作流发布逻辑从 Controller 下沉到工作流引擎执行。
 *
 * 作为 Spring Bean 注册，由 [com.fluxion.di.spring.SpringFunctionInstanceProvider]
 * 从应用上下文中直接获取实例。
 */
@Component
class AdminPublishWorkflowFunction @Autowired constructor(
    private val workflowService: WfDefinitionService,
    private val workflowMapper: WorkflowMapper
) : WorkflowFunction<Any?> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val workflowId = input.requireWfInput<String>("workflowId")
        val publishTarget = input.wfInput<Map<String, Any>>("publishTarget")?.let {
            JsonUtil.convertValue(it, PublishTarget::class.java)
        }

        log.info { "Admin workflow publishing workflow [$workflowId]" }

        val published = workflowService.publish(workflowId, publishTarget)
        val dto = workflowMapper.toDto(published)

        return FunctionResult.success(dto)
    }

    override fun meta(): FunctionMeta = FunctionMeta.builder(FUNCTION_REF)
        .description("Admin self-hosted workflow: publish a workflow definition")
        .build()

    companion object {
        const val FUNCTION_REF = "admin:publishWorkflow"
    }
}
