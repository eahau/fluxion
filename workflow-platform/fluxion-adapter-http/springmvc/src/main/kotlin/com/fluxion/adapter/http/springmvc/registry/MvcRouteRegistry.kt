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
 * Spring MVC HTTP route registry — concrete subclass of [AbstractRouteRegistry].
 *
 * MVC-specific responsibilities:
 * - Register/unregister routes via `RequestMappingHandlerMapping` dynamic APIs.
 * - Resolve incoming requests to `workflowId` + path variables.
 * - Wire up a `HandlerInterceptor` (via the auto-config) so the handler can
 *   consume a pre-resolved [RouteMatch] from request attributes.
 *
 * Single-source-of-truth invariant (same as WebFlux implementation):
 * `AbstractRouteRegistry.routeDefinitions` is ALWAYS authoritative.
 * `RequestMappingInfo` instances used for `unregisterMapping()` are NOT cached;
 * they are re-built on the fly from `routeDefinitions` through [buildMappingInfo].
 * This works because `RequestMappingInfo.equals()` is value-based (paths, methods,
 * options conditions) — rebuilt instances compare equal to the originally
 * registered ones.
 */
class MvcRouteRegistry(
    private val requestMappingHandlerMapping: RequestMappingHandlerMapping,
    private val dynamicHandler: MvcWorkflowHandler
) : AbstractRouteRegistry(), ApplicationListener<ApplicationReadyEvent> {

    /** `java.lang.reflect.Method` reference to [MvcWorkflowHandler.handle] required by the registerMapping API. */
    private val handleMethod = MvcWorkflowHandler::handle.javaMethod!!

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        log.info { "MvcRouteRegistry ready, waiting for RouteConfigStore route config push..." }
    }

    // ----- AbstractRouteRegistry hooks ----------------------------------------

    override fun activeRouteKeys(): Set<String> = routeDefinitions.keys.toSet()

    override fun doRegister(route: HttpRouteDefinition) {
        try {
            val info = buildMappingInfo(route.path, route.method)
            requestMappingHandlerMapping.registerMapping(info, dynamicHandler, handleMethod)
            onRouteRegistered(route)
            log.info { "Registered route: ${route.method} ${route.path} [${route.scope}] → workflowId=[${route.workflowId}]" }
        } catch (ex: Exception) {
            log.error(ex) { "Failed to register route ${route.method} ${route.path}" }
        }
    }

    override fun doUnregister(routeKey: String) {
        val route = routeDefinitions[routeKey] ?: return
        val info = buildMappingInfo(route.path, route.method)
        requestMappingHandlerMapping.unregisterMapping(info)
        onRouteUnregistered(routeKey)
        log.info { "Unregistered route: $routeKey" }
    }

    /**
     * Reconstruct a `RequestMappingInfo` value object from path + method.
     *
     * Uses the parent handler-mapping's own `PathPatternParser` if set, otherwise
     * falls back to the global default, so pattern syntax stays consistent with
     * compile-time registered controllers.
     *
     * @return Value-based RequestMappingInfo instance — safe to use for both
     *         registerMapping and unregisterMapping.
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

    // ----- Request resolution (called by interceptor) -------------------------

    /**
     * Fast-path resolution that only returns `workflowId`.
     *
     * Kept as a separate public method because some lightweight interceptors
     * only need to know whether the request targets a workflow at all, without
     * extracting path variables yet.
     *
     * @return workflowId for this request, or null if no route matches
     */
    fun resolveWorkflowId(method: String, path: String): String? {
        super.resolveWorkflowId(HttpRouteDefinition.keyOf(method, path))?.let { return it }

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
     * Full resolution: `workflowId` + extracted URI variables.
     *
     * Invoked by `WorkflowRouteInterceptor` on every inbound request so the
     * handler can read the result directly from request attributes.
     *
     * Two-stage lookup:
     * 1. Exact match (no path variables) — O(1) hash lookup against routeDefinitions.
     * 2. Pattern match with `matchAndExtract` — O(n) scan but short-circuits on first hit.
     *
     * @return Route match if a route fits, null otherwise
     */
    fun resolveRoute(method: String, path: String): RouteMatch? {
        resolveWorkflowId(HttpRouteDefinition.keyOf(method, path))?.let {
            return RouteMatch(it, emptyMap())
        }

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
