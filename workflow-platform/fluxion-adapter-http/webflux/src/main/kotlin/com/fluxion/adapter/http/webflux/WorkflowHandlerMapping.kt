package com.fluxion.adapter.http.webflux

import org.springframework.core.Ordered
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * WebFlux 动态路由 HandlerMapping。
 *
 * 实现 [Ordered]（order=1），确保在 DispatcherHandler 的 HandlerMapping 链中
 * 优先于 Spring 默认的 ResourceHandlerMapping 被匹配。
 *
 * 匹配到路由后直接返回 [WebFluxWorkflowHandler] 实例，由 [WorkflowHandlerAdapter] 负责调用。
 * 不使用 HandlerFunction（它依赖 RouterFunction 基础设施设置的 request 属性）。
 *
 * 优先级链：
 *   RequestMappingHandlerMapping (0) → **WorkflowHandlerMapping (1)** → RouterFunctionMapping (-1) → ResourceHandlerMapping (LOWEST)
 */
class WorkflowHandlerMapping(
    private val routeRegistry: WebFluxRouteRegistry,
    private val handler: WebFluxWorkflowHandler,
) : HandlerMapping, Ordered {

    override fun getOrder(): Int = 1

    override fun getHandler(exchange: ServerWebExchange): Mono<Any> {
        val method = exchange.request.method.name()
        val path = exchange.request.uri.path
        return Mono.justOrEmpty(routeRegistry.resolveRoute(method, path))
            .doOnNext { match -> exchange.attributes[WebFluxWorkflowHandler.ATTR_ROUTE_MATCH] = match }
            .map { handler as Any }
    }
}
