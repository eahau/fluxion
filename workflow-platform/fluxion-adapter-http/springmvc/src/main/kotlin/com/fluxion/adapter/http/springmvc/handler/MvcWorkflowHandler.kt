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
 * Extension on [HttpServletRequest] that converts a Servlet-native request
 * into the protocol-agnostic [UnifiedRequest] consumed by [WorkflowRouter].
 *
 * Extracts:
 * - Query params from `parameterMap` (single → String, multi → List).
 * - All HTTP headers as `String → String` map.
 * - Path variables already resolved by the interceptor.
 * - Optional raw string body (null for GET/DELETE/etc.)
 *
 * All three input sets are merged via [mergeParams] with well-defined
 * precedence.
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
 * Spring MVC handler bean that every dynamically-registered HTTP route points at.
 *
 * All routes published by `MvcRouteRegistry` via `RequestMappingHandlerMapping`
 * are bound to the single [handle] method on this bean — that's how a single
 * handler can serve thousands of distinct URL shapes without recompilation.
 *
 * Coroutine support: `handle()` is a `suspend fun`. Spring Framework 6+
 * supports suspend controller methods natively — the Tomcat request thread
 * is released on `suspend` and reacquired from the pool on `resume`, so
 * concurrent throughput stays high even when a DAG does slow I/O.
 */
class MvcWorkflowHandler(
    private val workflowRouter: WorkflowRouter
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Mirror of [HttpRequestProcessor.ATTR_ROUTE_MATCH] — copied for call-site convenience. */
        const val ATTR_ROUTE_MATCH = HttpRequestProcessor.ATTR_ROUTE_MATCH
        /** Mirror of [HttpRequestProcessor.HEADER_EXECUTION_ID] — copied for call-site convenience. */
        const val HEADER_EXECUTION_ID = HttpRequestProcessor.HEADER_EXECUTION_ID
    }

    /**
     * Handle a single matched dynamic MVC route.
     *
     * Flow:
     * 1. Read the pre-resolved [RouteMatch] from request attributes (set by
     *    the `WorkflowRouteInterceptor` registered in the auto-config).
     * 2. Convert the Servlet request → [UnifiedRequest].
     * 3. Delegate to the shared [WorkflowRouter] (coroutine-native suspend path).
     * 4. Emit the response with `X-Execution-Id` tracing header.
     *
     * @param request Servlet-native request; body is injected by Spring's
     *                `@RequestBody` argument resolver only if there is content.
     * @param body    Optional raw request body string (null for empty bodies).
     * @return HTTP 200 with `result.data` as body, plus tracing headers.
     */
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
