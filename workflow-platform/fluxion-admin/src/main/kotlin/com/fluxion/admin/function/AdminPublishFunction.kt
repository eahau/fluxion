package com.fluxion.admin.function

import com.fluxion.admin.generated.model.PublishTarget
import com.fluxion.admin.mapper.FunctionMapper
import com.fluxion.admin.service.WfFunctionService
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
 * Admin 自举工作流节点函数：发布函数定义。
 *
 * 由内置工作流 [ADMIN_FUNCTION_PUBLISH_WORKFLOW_ID] 调用，
 * 将函数发布逻辑从 Controller 下沉到工作流引擎执行。
 *
 * 作为 Spring Bean 注册，由 [com.fluxion.di.spring.SpringFunctionInstanceProvider]
 * 从应用上下文中直接获取实例。
 */
@Component
class AdminPublishFunction @Autowired constructor(
    private val functionService: WfFunctionService,
    private val functionMapper: FunctionMapper
) : WorkflowFunction<Any?> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val functionName = input.requireWfInput<String>("functionName")
        val publishTarget = input.wfInput<Map<String, Any>>("publishTarget")?.let {
            JsonUtil.convertValue(it, PublishTarget::class.java)
        }

        log.info { "Admin workflow publishing function [$functionName]" }

        val published = functionService.publish(functionName, publishTarget)
        val dto = functionMapper.toDto(published)

        return FunctionResult.success(dto)
    }

    override fun meta(): FunctionMeta = FunctionMeta.builder(FUNCTION_REF)
        .description("Admin self-hosted workflow: publish a function definition")
        .build()

    companion object {
        const val FUNCTION_REF = "admin:publishFunction"
    }
}
