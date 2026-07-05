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
 * Convert WebFlux [ServerRequest] to [UnifiedRequest].
 *
 * Extracts query params, headers, body from the framework-specific request
 * and merges them into a protocol-agnostic [UnifiedRequest].
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
 * WebFlux dynamic route handler.
 *
 * Each request matched by [HttpWebFluxAdapterAutoConfiguration.workflowHandlerMapping]
 * is delegated to this handler. The [RouteMatch] is pre-resolved by the HandlerMapping
 * and stored in exchange attributes under [ATTR_ROUTE_MATCH].
 *
 * Bridge: suspend → Mono via kotlinx-coroutines-reactor `mono { }`.
 */
class WebFluxWorkflowHandler(
    private val workflowRouter: WorkflowRouter,
) {
    companion object {
        const val ATTR_ROUTE_MATCH = HttpRequestProcessor.ATTR_ROUTE_MATCH
    }

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Handle an incoming WebFlux request.
     *
     * The [RouteMatch] (workflowId + pathVariables) is read from exchange attributes,
     * pre-resolved by the HandlerMapping — no redundant route lookup needed.
     */
    fun handle(request: ServerRequest): Mono<ServerResponse> {
        val match = request.attribute(ATTR_ROUTE_MATCH)
            .orElse(null) as? RouteMatch
            ?: return ServerResponse.status(HttpStatus.NOT_FOUND)
                .bodyValue(mapOf("error" to "No workflow bound for path: ${request.path()}"))

        return mono {
            val unifiedRequest = request.toUnifiedRequest(match.workflowId, match.pathVariables)
            val result = workflowRouter.executeSuspend(unifiedRequest)

            ServerResponse.ok()
                .header(HEADER_EXECUTION_ID, result.executionId ?: "")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result.data ?: emptyMap<String, Any>())
                .awaitSingle()
        }
    }
}
