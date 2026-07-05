package com.fluxion.adapter.http.springmvc.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.alibaba.nacos.api.config.listener.Listener
import com.fluxion.adapter.http.core.HttpRouteDefinition
import com.fluxion.core.util.JsonUtil
import com.fluxion.adapter.http.core.RouteChangeListener
import com.fluxion.adapter.http.core.RouteConfigStore
import org.slf4j.*
import java.util.concurrent.Executor

/**
 * Nacos 路由配置存储实现
 *
 * admin 后台发布 HTTP 接口后，将路由配置写入 Nacos：
 *   - dataId: workflow.http.routes（可配置）
 *   - group:  WORKFLOW（可配置）
 *   - 格式:   JSON（见 HttpRouteDefinition 注释中的格式说明）
 *
 * 本类监听 Nacos 配置变更，收到推送后调用 RouteChangeListener.onRoutesChanged()，
 * 由 RouteRegistry 实现执行 diff + registerMapping/unregisterMapping。
 *
 * Nacos 配置示例：
 * ```json
 * {
 *   "routes": [
 *     { "routeKey": "POST:/api/user/login",  "path": "/api/user/login",  "method": "POST", "workflowId": "user-login-workflow",  "enabled": true },
 *     { "routeKey": "POST:/api/order/create","path": "/api/order/create","method": "POST", "workflowId": "create-order-workflow", "enabled": true },
 *     { "routeKey": "GET:/api/user/{id}",    "path": "/api/user/{id}",   "method": "GET",  "workflowId": "get-user-workflow",     "enabled": true }
 *   ]
 * }
 * ```
 *
 * @param configService Nacos ConfigService（由 spring-cloud-starter-alibaba-nacos-config 自动配置）
 * @param dataId        Nacos DataId（默认 workflow.http.routes）
 * @param group         Nacos Group（默认 WORKFLOW）
 */
class NacosRouteConfigStore(
    private val configService: ConfigService,
    private val dataId:        String,
    private val group:         String
) : RouteConfigStore {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 路由配置 JSON 顶层容器 */
    private data class RoutesConfig(val routes: List<HttpRouteDefinition> = emptyList())

    // ─── RouteConfigStore ─────────────────────────────────────────────

    override fun loadAll(): List<HttpRouteDefinition> {
        return try {
            val content = configService.getConfig(dataId, group, TIMEOUT_MS)
            if (content.isNullOrBlank()) {
                log.warn { "Nacos config [$group/$dataId] is empty, no routes loaded." }
                emptyList()
            } else {
                parseRoutes(content).also {
                    log.info { "Loaded ${it.size} routes from Nacos [$group/$dataId]" }
                }
            }
        } catch (ex: Exception) {
            log.error(ex) { "Failed to load routes from Nacos [$group/$dataId]: ${ex.message}" }
            emptyList()
        }
    }

    override fun watch(listener: RouteChangeListener) {
        configService.addListener(dataId, group, object : Listener {

            override fun receiveConfigInfo(configInfo: String?) {
                if (configInfo.isNullOrBlank()) {
                    log.warn { "Received empty config from Nacos [$group/$dataId], clearing all routes." }
                    listener.onRoutesChanged(emptyList())
                    return
                }
                try {
                    val routes = parseRoutes(configInfo)
                    log.info { "Nacos config change [$group/$dataId]: ${routes.size} routes received." }
                    listener.onRoutesChanged(routes)
                } catch (ex: Exception) {
                    log.error(ex) { "Failed to parse Nacos config change [$group/$dataId]: ${ex.message}" }
                }
            }

            /** Nacos 回调线程：null 使用 Nacos 内置线程池 */
            override fun getExecutor(): Executor? = null
        })

        log.info { "Started watching Nacos config [$group/$dataId] for HTTP route changes." }
    }

    // ─── 解析 ─────────────────────────────────────────────────────────

    private fun parseRoutes(content: String): List<HttpRouteDefinition> {
        val config = JsonUtil.deserialize(content, RoutesConfig::class.java)
        return config.routes
    }

    companion object {
        private const val TIMEOUT_MS = 5_000L
    }
}
