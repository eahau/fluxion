package com.fluxion.adapter.http.springmvc.apollo

import com.ctrip.framework.apollo.Config
import com.ctrip.framework.apollo.ConfigChangeListener
import com.fluxion.adapter.http.core.HttpRouteDefinition
import com.fluxion.core.util.JsonUtil
import com.fluxion.adapter.http.core.RouteChangeListener
import com.fluxion.adapter.http.core.RouteConfigStore
import org.slf4j.*

/**
 * Apollo 路由配置存储实现
 *
 * admin 后台发布 HTTP 接口后，将路由配置写入 Apollo：
 *   - namespace: workflow.http.routes（可配置）
 *   - key:       routes（JSON 数组字符串）
 *
 * Apollo 命名空间内容格式（properties 格式，routes key 存放 JSON）：
 * ```properties
 * routes=[{"routeKey":"POST:/api/user/login","path":"/api/user/login","method":"POST","workflowId":"user-login-workflow","enabled":true}]
 * ```
 *
 * 或使用 JSON 格式命名空间：
 * ```json
 * {
 *   "routes": [
 *     { "routeKey": "POST:/api/user/login", "path": "/api/user/login", "method": "POST", "workflowId": "user-login-workflow", "enabled": true }
 *   ]
 * }
 * ```
 *
 * @param config      Apollo Config 对象（由 Apollo Spring Boot SDK 自动注入）
 * @param routesKey   Apollo 配置 key（默认 routes）
 */
class ApolloRouteConfigStore(
    private val config:        Config,
    private val routesKey:     String
) : RouteConfigStore {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun loadAll(): List<HttpRouteDefinition> {
        val value = config.getProperty(routesKey, null)
        return if (value.isNullOrBlank()) {
            log.warn { "Apollo config key [$routesKey] is empty, no routes loaded." }
            emptyList()
        } else {
            parseRoutes(value).also {
                log.info { "Loaded ${it.size} routes from Apollo key [$routesKey]" }
            }
        }
    }

    override fun watch(listener: RouteChangeListener) {
        val changeListener = ConfigChangeListener { changeEvent ->
            if (changeEvent.isChanged(routesKey)) {
                val newValue = changeEvent.getChange(routesKey)?.newValue
                if (newValue.isNullOrBlank()) {
                    log.warn { "Apollo key [$routesKey] changed to empty, clearing all routes." }
                    listener.onRoutesChanged(emptyList())
                } else {
                    try {
                        val routes = parseRoutes(newValue)
                        log.info { "Apollo config change key [$routesKey]: ${routes.size} routes received." }
                        listener.onRoutesChanged(routes)
                    } catch (ex: Exception) {
                        log.error(ex) { "Failed to parse Apollo config change key [$routesKey]: ${ex.message}" }
                    }
                }
            }
        }
        config.addChangeListener(changeListener, setOf(routesKey))
        log.info { "Started watching Apollo config key [$routesKey] for HTTP route changes." }
    }

    private fun parseRoutes(value: String): List<HttpRouteDefinition> {
        // 支持两种格式：JSON 数组 [...] 或对象 {"routes": [...]}
        val trimmed = value.trim()
        return if (trimmed.startsWith("[")) {
            JsonUtil.deserializeList(trimmed, HttpRouteDefinition::class.java)
        } else {
            data class RoutesWrapper(val routes: List<HttpRouteDefinition> = emptyList())
            JsonUtil.deserialize(trimmed, RoutesWrapper::class.java).routes
        }
    }
}
