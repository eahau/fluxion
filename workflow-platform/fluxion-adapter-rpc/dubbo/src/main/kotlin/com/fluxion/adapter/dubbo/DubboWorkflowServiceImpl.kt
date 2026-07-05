package com.fluxion.adapter.dubbo

import com.fluxion.adapter.spi.UnifiedRequest
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.core.exception.WorkflowException
import org.apache.dubbo.rpc.RpcContext
import org.slf4j.*

/**
 * Dubbo 工作流服务实现（零 Spring）
 *
 * 通过 Apache Dubbo 暴露工作流调用能力
 *
 * 协议标识：DUBBO
 * 路由规则：
 *   - params 含 `_workflowId` → 跳过路由，直接按 ID 执行
 *   - Dubbo attachments 含 `x-service-key` → 按该键路由（对应 wf_definition.bind_key）
 *   - params 含 `_serviceKey` → 按该键路由（兼容方式）
 */
class DubboWorkflowServiceImpl(
    private val workflowRouter: WorkflowRouter
) : DubboWorkflowApi {

    companion object {
        /** params 中携带此键时跳过路由，直接按 workflowId 执行 */
        const val PARAM_WORKFLOW_ID = "_workflowId"
        /** params 中携带此键时按 bind_key 路由（兼容方式） */
        const val PARAM_SERVICE_KEY = "_serviceKey"
        /** Dubbo attachments 中携带此键时按 bind_key 路由（与 gRPC/HTTP 对齐） */
        const val ATTACHMENT_SERVICE_KEY = "x-service-key"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Dubbo 服务接口实现。
     *
     * 路由策略（优先级从高到低）：
     *   1. params 含 `_workflowId` → 直接按 ID 执行（protocol=INTERNAL）
     *   2. Dubbo attachments 含 `x-service-key` → 按该键路由
     *   3. params 含 `_serviceKey` → 按该键路由
     *
     * @param params 业务参数
     * @return 工作流执行结果，或错误体 Map
     */
    override fun execute(params: Map<String, Any>): Any? {
        val attachments = extractDubboAttachments()
        val workflowId = params[PARAM_WORKFLOW_ID] as? String

        val serviceKey = attachments[ATTACHMENT_SERVICE_KEY]?.takeIf { it.isNotBlank() }
            ?: (params[PARAM_SERVICE_KEY] as? String)?.takeIf { it.isNotBlank() }

        val cleanParams = params
            .let { if (workflowId != null) it - PARAM_WORKFLOW_ID else it }
            .let { if (serviceKey != null && it.containsKey(PARAM_SERVICE_KEY)) it - PARAM_SERVICE_KEY else it }

        val request = when {
            workflowId != null -> UnifiedRequest.withSchemaFromHeaders("INTERNAL", workflowId, attachments, cleanParams)
            serviceKey != null -> UnifiedRequest.withSchemaFromHeaders("DUBBO:$serviceKey", null, attachments, cleanParams)
            else -> throw IllegalArgumentException(
                "Dubbo workflow routing requires either '_workflowId' in params " +
                    "or 'x-service-key' in Dubbo attachments / '_serviceKey' in params"
            )
        }

        val target = workflowId ?: serviceKey

        return try {
            workflowRouter.execute(request).data
        } catch (ex: WorkflowException) {
            log.warn { "Dubbo workflow error [${ex.errorCode}] for [$target]: ${ex.message}" }
            errorBody(ex.errorCode, ex.message)
        } catch (ex: Exception) {
            log.error(ex) { "Dubbo unexpected error for [$target]: ${ex.message}" }
            errorBody("WF-INTERNAL", ex.message)
        }
    }

    /** 构建统一错误响应体（success=false + errorCode + message）。 */
    private fun errorBody(errorCode: String, message: String?): Map<String, Any> =
        mapOf(
            "success"   to false,
            "errorCode" to errorCode,
            "message"   to (message ?: "Workflow execution failed")
        )

    /**
     * 提取 Dubbo 隐式参数（attachments）作为 headers
     * 包含：traceId、userId、tenantId、x-service-key 等透传参数
     */
    private fun extractDubboAttachments(): Map<String, String> {
        return try {
            val context = RpcContext.getServiceContext()
            @Suppress("DEPRECATION")
            context.attachments?.mapValues { it.value ?: "" } ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
