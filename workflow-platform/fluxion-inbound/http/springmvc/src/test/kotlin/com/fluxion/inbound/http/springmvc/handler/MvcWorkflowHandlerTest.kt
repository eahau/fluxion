package com.fluxion.inbound.http.springmvc.handler

import com.fluxion.inbound.http.core.RouteMatch
import com.fluxion.inbound.spi.UnifiedRequest
import com.fluxion.inbound.spi.UnifiedResponse
import com.fluxion.inbound.spi.InboundRouter
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
 * Unit tests for [MvcWorkflowHandler].
 *
 * Verifies the full dynamic-route pipeline end-to-end without spinning up a
 * Spring context:
 * 1. Reading `workflowId` from request attributes (attribute set by interceptor).
 * 2. Merging path vars, query params, JSON body into the `params` map.
 * 3. Passing request headers through untouched to the unified request.
 * 4. Emitting the `X-Execution-Id` response header on success.
 *
 * Uses a fake [InboundRouter] implementation to spy on the exact
 * [UnifiedRequest] that would have been sent to the DAG engine.
 */
class MvcWorkflowHandlerTest {

    private val fakeRouter = FakeInboundRouter()
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

        // Path variable wins (highest precedence)
        assertEquals("demoFn", params["functionName"])
        // Query param (medium precedence)
        assertEquals("true", params["force"])
        // Body JSON parsed into nested maps
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

    // ----- helpers -----------------------------------------------------------

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

    /**
     * Test-stub InboundRouter that records the last inbound request and
     * returns a preconfigured result or exception.
     */
    private class FakeInboundRouter : InboundRouter {
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
