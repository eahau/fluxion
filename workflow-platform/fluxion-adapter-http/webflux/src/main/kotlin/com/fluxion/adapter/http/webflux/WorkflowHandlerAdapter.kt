package com.fluxion.adapter.http.webflux

import kotlin.reflect.jvm.javaMethod
import org.springframework.core.MethodParameter
import org.springframework.http.codec.HttpMessageReader
import org.springframework.web.reactive.HandlerAdapter
import org.springframework.web.reactive.HandlerResult
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Custom WebFlux [HandlerAdapter] for [WebFluxWorkflowHandler].
 *
 * Why not just use `RouterFunction` + `HandlerFunction`? The RouterFunction
 * path relies on `RouterFunctionMapping` setting a `ServerRequest` attribute
 * before calling the function. Since we bypass that mapping (see
 * [WorkflowHandlerMapping] — direct-query strategy for O(1) route updates)
 * we need our own adapter that creates the ServerRequest from the raw
 * `ServerWebExchange` + injected HTTP message readers.
 *
 * Invocation flow:
 * 1. DispatcherHandler picks [WorkflowHandlerMapping] → returns [WebFluxWorkflowHandler].
 * 2. DispatcherHandler scans registered HandlerAdapters → picks this one.
 * 3. We build a `ServerRequest` using `ServerRequest.create(exchange, readers)`.
 * 4. We delegate to [WebFluxWorkflowHandler.handle(ServerRequest)] → `Mono<ServerResponse>`.
 * 5. We wrap the ServerResponse in a `HandlerResult` so DispatcherHandler can
 *    render the response via the standard `ServerResponse.writeTo(...)` machinery.
 *
 * @param messageReaders HTTP message readers provided by Spring's shared `ServerCodecConfigurer`.
 */
class WorkflowHandlerAdapter(
    private val messageReaders: List<HttpMessageReader<*>>,
) : HandlerAdapter {

    /** MethodParameter for the return value slot of [WebFluxWorkflowHandler.handle]. */
    private val returnParam: MethodParameter = MethodParameter.forExecutable(
        WebFluxWorkflowHandler::handle.javaMethod!!,
        -1
    )

    override fun supports(handler: Any): Boolean = handler is WebFluxWorkflowHandler

    override fun handle(exchange: ServerWebExchange, handler: Any): Mono<HandlerResult> {
        val workflowHandler = handler as WebFluxWorkflowHandler
        val request = ServerRequest.create(exchange, messageReaders)
        return workflowHandler.handle(request)
            .map { response -> HandlerResult(workflowHandler, response, returnParam) }
    }
}
