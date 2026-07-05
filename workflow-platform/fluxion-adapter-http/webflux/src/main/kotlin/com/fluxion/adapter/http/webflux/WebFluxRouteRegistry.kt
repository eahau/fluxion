package com.fluxion.adapter.http.webflux

import com.fluxion.adapter.http.core.AbstractRouteRegistry
import com.fluxion.adapter.http.core.HttpRouteDefinition
import com.fluxion.adapter.http.core.RouteMatch
import org.slf4j.info

/**
 * WebFlux 路由注册表 — 继承自 [AbstractRouteRegistry]。
 *
 * WebFlux 与 Spring MVC 在动态路由注册上存在根本差异：
 *   - MVC 有 [RequestMappingHandlerMapping.registerMapping] API，支持 O(1) 增量注册/注销
 *   - WebFlux 的 RouterFunction 不支持运行时增删，每次变更需 O(n) 全量重建
 *
 * 因此 WebFlux 采用**直接查询**策略：
 *   - 路由注册/注销仅操作父类 [routeDefinitions]（ConcurrentHashMap）
 *   - HandlerMapping 每次请求时直接调用 [resolveRoute] 实时查询，无中间缓存层
 *   - 路由变更 O(1)，请求解析 O(n) 模式匹配
 *
 * 路径模式匹配使用 Spring Web 共享的 [org.springframework.web.util.pattern.PathPattern]
 * （与 Spring MVC 相同），因此 resolveRoute() 逻辑与 MVC 实现一致。
 */
class WebFluxRouteRegistry : AbstractRouteRegistry() {

    // ─── AbstractRouteRegistry 钩子 ─────────────────────────────────────

    public override fun activeRouteKeys(): Set<String> = routeDefinitions.keys.toSet()

    override fun doRegister(route: HttpRouteDefinition) {
        onRouteRegistered(route)
        log.info { "Registered WebFlux route: ${route.method} ${route.path} [${route.scope}] → workflowId=[${route.workflowId}]" }
    }

    override fun doUnregister(routeKey: String) {
        if (routeDefinitions.containsKey(routeKey)) {
            onRouteUnregistered(routeKey)
            log.info { "Unregistered WebFlux route: $routeKey" }
        }
    }

    // ─── 请求解析 ──────────────────────────────────────────────────────

    /**
     * 为传入请求解析 workflowId + 路径变量。
     *
     * 由 HandlerMapping 直接调用，无需 RouterFunction 中间层。
     * 使用 Spring Web 的 PathPattern（与 Spring MVC 共享）进行模式匹配。
     * 同时支持精确路径和路径变量模式，如 /api/users/{id}。
     */
    fun resolveRoute(method: String, path: String): RouteMatch? {
        // 1. 精确匹配（无路径变量）
        resolveWorkflowId(HttpRouteDefinition.keyOf(method, path))?.let {
            return RouteMatch(it, emptyMap())
        }

        // 2. 模式匹配并提取路径变量
        val requestPath = org.springframework.http.server.PathContainer.parsePath(path)
        val patternParser = org.springframework.web.util.pattern.PathPatternParser.defaultInstance

        routeDefinitions.forEach { (routeKey, route) ->
            if (route.method.equals(method, ignoreCase = true)) {
                val pattern = patternParser.parse(route.path)
                val match = pattern.matchAndExtract(requestPath)
                if (match != null) {
                    val workflowId = resolveWorkflowId(routeKey) ?: return@forEach
                    return RouteMatch(workflowId, match.uriVariables)
                }
            }
        }
        return null
    }
}
