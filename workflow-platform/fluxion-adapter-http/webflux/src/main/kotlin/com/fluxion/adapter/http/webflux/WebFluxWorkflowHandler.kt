package com.fluxion.adapter.http.webflux

import com.fluxion.adapter.http.core.HttpRequestProcessor
import com.fluxion.adapter.http.core.HttpRequestProcessor.ATTR_ROUTE_MATCH
import com.fluxion.adapter.http.core.HttpRequestProcessor.HEADER_EXECUTION_ID
import com.fluxion.adapter.http.core.RouteMatch
import com.fluxion.adapter.http.core.mergeParams
import com.fluxion.adapter.spi.UnifiedRequest
import com.fluxion.adapter.spi.WorkflowRouter
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.slf4j.*
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

/**
 * Extension on [ServerRequest] that converts WebFlux-native request to [UnifiedRequest].
 *
 * Extracts:
 * - Query params (via `queryParams()` — single→String, multi→List).
 * - All HTTP headers as single-value map.
 * - Path variables (already set by the HandlerMapping in [RouteMatch]).
 * - Optional raw body via `bodyToMono(String::class.java)` (collected only on JSON bodies).
 */
suspend fun ServerRequest.toUnifiedRequest(
    workflowId: String,
    pathVariables: Map<String, String>,
): UnifiedRequest {
    val body = bodyToMono(String::class.java).awaitSingleOrNull()
    val queryParams = mutableMapOf<String, Any>()
    queryParams().forEach { (k, v) ->
        queryParams[k] = if (v.size == 1) v[0] else v
    }
    val headers = headers().asHttpHeaders().toSingleValueMap()
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
 * WebFlux handler for dynamically-routed workflow HTTP requests.
 *
 * Each request matched by [WorkflowHandlerMapping] is dispatched to this
 * handler. The `RouteMatch` (workflowId + path variables) is pre-resolved by
 * the HandlerMapping and stored in `exchange.attributes` under
 * [ATTR_ROUTE_MATCH] to avoid redundant work here.
 *
 * Coroutine bridge: the public `handle(ServerRequest): Mono<ServerResponse>`
 * entry point uses `mono { ... }` from kotlinx-coroutines-reactor so the
 * suspend-based `WorkflowRouter.executeSuspend` path integrates seamlessly
 * with the WebFlux reactive pipeline.
 */
class WebFluxWorkflowHandler(
    private val workflowRouter: WorkflowRouter,
) {
    companion object {
        /** Mirror of [HttpRequestProcessor.ATTR_ROUTE_MATCH] — convenience constant. */
        const val ATTR_ROUTE_MATCH = HttpRequestProcessor.ATTR_ROUTE_MATCH
    }

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Handle a single reactive request.
     *
     * Reads the pre-resolved [RouteMatch] from exchange attributes (set by
     * [WorkflowHandlerMapping]). If absent (HandlerMapping returned handler
     * without setting match), returns 404. Otherwise converts to
     * [UnifiedRequest] and delegates to the shared coroutine-based router.
     */
    fun handle(request: ServerRequest): Mono<ServerResponse> {
        val match = request.attribute(ATTR_ROUTE_MATCH)
            .orElse(null) as? RouteMatch
            ?: return ServerResponse.status(HttpStatus.NOT_FOUND)
                .bodyValue(mapOf("error" to "No workflow bound for path: ${request.path()}"))

        return mono {
            val unifiedRequest = request.toUnifiedRequest(match.workflowId, match.pathVariables)
            log.debug { "WebFlux routing request path=${request.path()} -> workflowId=${match.workflowId}" }
            val result = workflowRouter.executeSuspend(unifiedRequest)

            ServerResponse.ok()
                .header(HEADER_EXECUTION_ID, result.executionId ?: "")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result.data ?: emptyMap<String, Any>())
                .awaitSingle()
        }
    }
}
