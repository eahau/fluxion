package com.fluxion.adapter.http.springmvc.registry

import com.fluxion.adapter.http.core.AbstractRouteRegistry
import com.fluxion.adapter.http.core.HttpRouteDefinition
import com.fluxion.adapter.http.core.RouteMatch
import com.fluxion.adapter.http.springmvc.handler.MvcWorkflowHandler
import org.slf4j.*
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.http.server.PathContainer
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import org.springframework.web.util.pattern.PathPatternParser
import kotlin.reflect.jvm.javaMethod

/**
 * Spring MVC 路由注册表 — 继承自 [AbstractRouteRegistry]。
 *
 * MVC 特有职责：
 *   - 通过 [RequestMappingHandlerMapping] 注册/注销路由
 *   - 将入站请求解析为 workflowId + 路径变量
 *   - 通过工作流路由拦截器将 workflowId 和 pathVariables 注入 request attributes
 *
 * 路由状态管理：
 *   与 [com.fluxion.adapter.http.webflux.WebFluxRouteRegistry] 一致，完全以
 *   父类 [AbstractRouteRegistry.routeDefinitions] 作为**唯一数据源**。
 *   `unregisterMapping()` 所需的 [RequestMappingInfo] 由 [buildMappingInfo] 从
 *   `routeDefinitions` 中的 path/method 即时重建（RequestMappingInfo 基于值相等）。
 */
class MvcRouteRegistry(
    private val requestMappingHandlerMapping: RequestMappingHandlerMapping,
    private val dynamicHandler: MvcWorkflowHandler
) : AbstractRouteRegistry(), ApplicationListener<ApplicationReadyEvent> {

    /** handle() 方法的 Java Method 引用，`registerMapping()` 所需 */
    private val handleMethod = MvcWorkflowHandler::handle.javaMethod!!

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        log.info { "MvcRouteRegistry 已就绪，等待 RouteConfigStore 推送路由配置..." }
    }

    // ─── AbstractRouteRegistry 钩子 ─────────────────────────────────────

    override fun activeRouteKeys(): Set<String> = routeDefinitions.keys.toSet()

    override fun doRegister(route: HttpRouteDefinition) {
        try {
            val info = buildMappingInfo(route.path, route.method)
            requestMappingHandlerMapping.registerMapping(info, dynamicHandler, handleMethod)
            onRouteRegistered(route)
            log.info { "已注册路由: ${route.method} ${route.path} [${route.scope}] → workflowId=[${route.workflowId}]" }
        } catch (ex: Exception) {
            log.error(ex) { "注册路由失败 ${route.method} ${route.path}: ${ex.message}" }
        }
    }

    override fun doUnregister(routeKey: String) {
        val route = routeDefinitions[routeKey] ?: return
        val info = buildMappingInfo(route.path, route.method)
        requestMappingHandlerMapping.unregisterMapping(info)
        onRouteUnregistered(routeKey)
        log.info { "已注销路由: $routeKey" }
    }

    /**
     * 从 path + method 重建 [RequestMappingInfo]。
     *
     * [RequestMappingInfo] 基于值相等（paths / methods / options 等 conditions），
     * 因此重建的对象与原始注册对象在 `equals()` 下完全等价，可正确执行
     * `unregisterMapping()` 查找和移除。
     */
    private fun buildMappingInfo(path: String, method: String): RequestMappingInfo {
        val options = RequestMappingInfo.BuilderConfiguration().apply {
            patternParser = requestMappingHandlerMapping.patternParser
                ?: PathPatternParser.defaultInstance
        }
        return RequestMappingInfo
            .paths(path)
            .methods(RequestMethod.valueOf(method.uppercase()))
            .options(options)
            .build()
    }

    // ─── 请求解析（由拦截器调用）─────────────────────────────────────────

    /**
     * 解析入站请求的 workflowId。
     * 由工作流路由拦截器调用。
     */
    fun resolveWorkflowId(method: String, path: String): String? {
        // 1. 精确匹配（无路径变量）
        super.resolveWorkflowId(HttpRouteDefinition.keyOf(method, path))?.let { return it }

        // 2. 模式匹配
        val requestPath = PathContainer.parsePath(path)
        val patternParser = requestMappingHandlerMapping.patternParser
            ?: PathPatternParser.defaultInstance

        routeDefinitions.forEach { (routeKey, route) ->
            if (route.method.uppercase() == method.uppercase()) {
                val pattern = patternParser.parse(route.path)
                if (pattern.matches(requestPath)) {
                    return resolveWorkflowId(routeKey)
                }
            }
        }
        return null
    }

    /**
     * 解析 workflowId + 路径变量。
     * 由工作流路由拦截器调用，注入 ATTR_ROUTE_MATCH。
     */
    fun resolveRoute(method: String, path: String): RouteMatch? {
        // 1. 精确匹配（无路径变量）
        resolveWorkflowId(HttpRouteDefinition.keyOf(method, path))?.let {
            return RouteMatch(it, emptyMap())
        }

        // 2. 模式匹配并提取路径变量
        val requestPath = PathContainer.parsePath(path)
        val patternParser = requestMappingHandlerMapping.patternParser
            ?: PathPatternParser.defaultInstance

        routeDefinitions.forEach { (routeKey, route) ->
            if (route.method.uppercase() == method.uppercase()) {
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
