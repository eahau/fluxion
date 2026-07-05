package com.fluxion.adapter.http.springmvc.handler

import com.fluxion.adapter.http.core.HttpRequestProcessor
import com.fluxion.adapter.http.core.HttpRequestProcessor.ATTR_ROUTE_MATCH
import com.fluxion.adapter.http.core.HttpRequestProcessor.HEADER_EXECUTION_ID
import com.fluxion.adapter.http.core.RouteMatch
import com.fluxion.adapter.http.core.mergeParams
import com.fluxion.adapter.spi.UnifiedRequest
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.core.exception.WorkflowNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody

/**
 * Convert Spring MVC [HttpServletRequest] to [UnifiedRequest].
 *
 * Extracts query params, headers from the framework-specific request
 * and merges them with path variables and body into a protocol-agnostic [UnifiedRequest].
 */
fun HttpServletRequest.toUnifiedRequest(
    workflowId: String,
    pathVariables: Map<String, String>,
    body: String?,
): UnifiedRequest {
    val queryParams = mutableMapOf<String, Any>()
    parameterMap.forEach { (k, v) ->
        queryParams[k] = if (v.size == 1) v[0] else v.toList()
    }
    val headers = headerNames.asSequence().associateWith { getHeader(it) }
    val params = mergeParams(pathVariables, queryParams, body)
    return UnifiedRequest.withSchemaFromHeaders(
        protocol = "HTTP",
        workflowId = workflowId,
        headers = headers,
        params = params,
        rawBody = body
    )
}

/**
 * Spring MVC 动态路由处理器。
 *
 * 所有动态路由（由 MvcRouteRegistry 注册）均指向此 Bean 的 handle() 方法。
 * Spring MVC 6 原生支持 suspend controller 方法：
 *   Tomcat 线程在 suspend 时释放，resume 时重新分配。
 */
class MvcWorkflowHandler(
    private val workflowRouter: WorkflowRouter
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val ATTR_ROUTE_MATCH = HttpRequestProcessor.ATTR_ROUTE_MATCH
        const val HEADER_EXECUTION_ID = HttpRequestProcessor.HEADER_EXECUTION_ID
    }

    suspend fun handle(
        request: HttpServletRequest,
        @RequestBody(required = false) body: String?
    ): ResponseEntity<Any> {
        val match = request.getAttribute(ATTR_ROUTE_MATCH) as? RouteMatch
            ?: throw WorkflowNotFoundException("No workflow bound for path: ${request.servletPath}")

        val unifiedRequest = request.toUnifiedRequest(match.workflowId, match.pathVariables, body)
        val result = workflowRouter.executeSuspend(unifiedRequest)

        return ResponseEntity.ok()
            .header(HEADER_EXECUTION_ID, result.executionId ?: "")
            .body(result.data)
    }
}
