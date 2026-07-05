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
 * Custom [HandlerAdapter] for [WebFluxWorkflowHandler].
 *
 * Bridges Spring WebFlux's [ServerWebExchange] to our handler by:
 *   1. Creating a [ServerRequest] from the exchange (with injected message readers)
 *   2. Delegating to [WebFluxWorkflowHandler.handle]
 *   3. Wrapping the [ServerResponse][org.springframework.web.reactive.function.server.ServerResponse]
 *      in a [HandlerResult] for DispatcherHandler to write the response
 *
 * This avoids the `HandlerFunction` path which requires the `RouterFunctions.request` attribute
 * set by `RouterFunctionMapping` infrastructure — our custom `HandlerMapping` bypasses that.
 */
class WorkflowHandlerAdapter(
    private val messageReaders: List<HttpMessageReader<*>>,
) : HandlerAdapter {

    /** MethodParameter for [WebFluxWorkflowHandler.handle] return type: Mono<ServerResponse> */
    private val returnParam: MethodParameter = MethodParameter.forExecutable(
        WebFluxWorkflowHandler::handle.javaMethod!!,
        -1  // return value
    )

    override fun supports(handler: Any): Boolean = handler is WebFluxWorkflowHandler

    override fun handle(exchange: ServerWebExchange, handler: Any): Mono<HandlerResult> {
        val workflowHandler = handler as WebFluxWorkflowHandler
        val request = ServerRequest.create(exchange, messageReaders)
        return workflowHandler.handle(request)
            .map { response -> HandlerResult(workflowHandler, response, returnParam) }
    }
}
