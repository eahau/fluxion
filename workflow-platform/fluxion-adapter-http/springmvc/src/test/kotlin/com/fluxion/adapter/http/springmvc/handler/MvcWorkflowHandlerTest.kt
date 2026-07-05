package com.fluxion.adapter.http.springmvc.handler

import com.fluxion.adapter.http.core.RouteMatch
import com.fluxion.adapter.spi.UnifiedRequest
import com.fluxion.adapter.spi.UnifiedResponse
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.core.exception.WorkflowException
import com.fluxion.core.exception.WorkflowNotFoundException
import com.fluxion.core.util.uncheckedCast
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import java.util.Collections
import kotlinx.coroutines.runBlocking

/**
 * MvcWorkflowHandler 单元测试
 *
 * 验证动态路由统一入口能否正确：
 *   1. 从 request attribute 读取 workflowId
 *   2. 拼装路径变量、查询参数、请求体为 params
 *   3. 透传 headers
 *   4. 调用 WorkflowRouter 并回写 X-Execution-Id header
 */
class MvcWorkflowHandlerTest {

    private val fakeRouter = FakeWorkflowRouter()
    private val handler = MvcWorkflowHandler(fakeRouter)

    @Test
    fun `throw WorkflowNotFoundException when workflowId attribute is missing`() {
        val request = mock(HttpServletRequest::class.java)
        `when`(request.servletPath).thenReturn("/api/admin/functions/demo/deprecate")

        assertThrows(WorkflowNotFoundException::class.java) {
            runBlocking { handler.handle(request, null) }
        }
    }

    @Test
    fun `route request with path variables only`() {
        val request = buildRequest(
            workflowId = "admin-function-deprecate",
            pathVariables = mapOf("functionName" to "demoFn"),
            queryParams = emptyMap()
        )

        fakeRouter.nextResult = UnifiedResponse(
            success = true,
            data = mapOf("status" to "DEPRECATED"),
            executionId = "exec-123"
        )

        val response = runBlocking { handler.handle(request, null) }

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("exec-123", response.headers.getFirst(MvcWorkflowHandler.HEADER_EXECUTION_ID))

        val unifiedRequest = fakeRouter.lastRequest
        assertEquals("admin-function-deprecate", unifiedRequest?.workflowId)
        assertEquals("demoFn", unifiedRequest?.params?.get("functionName"))
        assertEquals("HTTP", unifiedRequest?.protocol)
    }

    @Test
    fun `merge path variables query params and body into params`() {
        val request = buildRequest(
            workflowId = "admin-function-publish",
            pathVariables = mapOf("functionName" to "demoFn"),
            queryParams = mapOf("force" to arrayOf("true"))
        )

        fakeRouter.nextResult = UnifiedResponse(
            success = true,
            data = emptyMap<String, Any>(),
            executionId = "exec-456"
        )

        val bodyJson = """{"publishTarget":{"type":"ALL"}}"""
        runBlocking { handler.handle(request, bodyJson) }

        val params = fakeRouter.lastRequest?.params ?: emptyMap()

        // 路径变量优先级最高
        assertEquals("demoFn", params["functionName"])
        // 查询参数
        assertEquals("true", params["force"])
        // 请求体 JSON
        val publishTarget = params["publishTarget"].uncheckedCast<Map<String, String>>()
        assertEquals("ALL", publishTarget?.get("type"))
    }

    @Test
    fun `path variable wins over query param and body with same name`() {
        val request = buildRequest(
            workflowId = "admin-function-deprecate",
            pathVariables = mapOf("functionName" to "fromPath"),
            queryParams = mapOf("functionName" to arrayOf("fromQuery"))
        )

        fakeRouter.nextResult = UnifiedResponse(
            success = true,
            data = null,
            executionId = "exec-789"
        )

        runBlocking { handler.handle(request, """{"functionName":"fromBody"}""") }

        assertEquals("fromPath", fakeRouter.lastRequest?.params?.get("functionName"))
    }

    @Test
    fun `pass request headers to unified request`() {
        val request = mock(HttpServletRequest::class.java)
        `when`(request.getAttribute(MvcWorkflowHandler.ATTR_ROUTE_MATCH))
            .thenReturn(RouteMatch("admin-function-deprecate", emptyMap()))
        `when`(request.servletPath).thenReturn("/api/admin/functions/demo/deprecate")
        `when`(request.parameterMap).thenReturn(emptyMap())
        `when`(request.headerNames)
            .thenReturn(Collections.enumeration(listOf("Authorization", "X-Request-Id")))
        `when`(request.getHeader("Authorization")).thenReturn("Bearer token")
        `when`(request.getHeader("X-Request-Id")).thenReturn("req-001")

        fakeRouter.nextResult = UnifiedResponse(
            success = true,
            data = null,
            executionId = "exec-000"
        )

        runBlocking { handler.handle(request, null) }

        val headers = fakeRouter.lastRequest?.headers ?: emptyMap()
        assertEquals("Bearer token", headers["Authorization"])
        assertEquals("req-001", headers["X-Request-Id"])
    }

    @Test
    fun `propagate workflow exception to caller`() {
        val request = buildRequest(
            workflowId = "admin-function-deprecate",
            pathVariables = mapOf("functionName" to "missingFn"),
            queryParams = emptyMap()
        )

        fakeRouter.nextError = WorkflowException("WF-REGISTRY-NOT_FOUND", "Function not found")

        assertThrows(WorkflowException::class.java) {
            runBlocking { handler.handle(request, null) }
        }
    }

    // ─── helpers ─────────────────────────────────────────────────

    private fun buildRequest(
        workflowId: String,
        pathVariables: Map<String, String>,
        queryParams: Map<String, Array<String>>
    ): HttpServletRequest {
        val request = mock(HttpServletRequest::class.java)
        `when`(request.getAttribute(MvcWorkflowHandler.ATTR_ROUTE_MATCH))
            .thenReturn(RouteMatch(workflowId, pathVariables))
        `when`(request.servletPath).thenReturn("/api/admin/functions/demo/deprecate")
        `when`(request.parameterMap).thenReturn(queryParams)
        `when`(request.headerNames).thenReturn(Collections.emptyEnumeration())
        return request
    }

    private class FakeWorkflowRouter : WorkflowRouter {
        var nextResult: UnifiedResponse = UnifiedResponse(success = true)
        var nextError: WorkflowException? = null
        var lastRequest: UnifiedRequest? = null

        override fun execute(request: UnifiedRequest): UnifiedResponse {
            lastRequest = request
            nextError?.let { throw it }
            return nextResult
        }
    }
}
