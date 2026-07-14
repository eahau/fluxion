package com.fluxion.inbound.http.webflux

import org.slf4j.*
import org.springframework.core.Ordered
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Dynamic WebFlux [HandlerMapping] for workflow routes.
 *
 * Plugs into the standard `DispatcherHandler` handler-resolution chain at
 * `Ordered#order = 1`. The ordering is carefully chosen:
 * ```
 * RequestMappingHandlerMapping (0) — compile-time @RequestMapping
 *   → WorkflowHandlerMapping (1)  ← THIS; dynamic workflow routes
 *     → RouterFunctionMapping (-1)
 *       → ResourceHandlerMapping (LOWEST)
 * ```
 *
 * Placement right AFTER `@RequestMapping` ensures that hand-written controllers
 * take precedence over dynamic workflow routes (application code always wins
 * over platform-defined URLs).
 *
 * Resolution works by delegating directly to [WebFluxRouteRegistry.resolveRoute].
 * If a match is found the same handler singleton — [WebFluxWorkflowHandler] — is
 * returned every time, with the resolved [RouteMatch] pre-stashed in
 * `exchange.attributes` so the handler can consume it without re-resolving.
 */
class WorkflowHandlerMapping(
    private val routeRegistry: WebFluxRouteRegistry,
    private val handler: WebFluxWorkflowHandler,
) : HandlerMapping, Ordered {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getOrder(): Int = 1

    override fun getHandler(exchange: ServerWebExchange): Mono<Any> {
        val method = exchange.request.method.name()
        val path = exchange.request.uri.path
        return Mono.justOrEmpty(routeRegistry.resolveRoute(method, path))
            .doOnNext { match ->
                log.debug { "WebFlux workflow route matched: ${method} ${path} -> workflowId=${match.workflowId}" }
                exchange.attributes[WebFluxWorkflowHandler.ATTR_ROUTE_MATCH] = match
            }
            .map { handler as Any }
    }
}
