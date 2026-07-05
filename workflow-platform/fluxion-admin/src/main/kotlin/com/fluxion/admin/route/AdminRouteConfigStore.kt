package com.fluxion.admin.route

import com.fluxion.adapter.http.core.HttpRouteDefinition
import com.fluxion.adapter.http.core.RouteChangeListener
import com.fluxion.adapter.http.core.RouteConfigStore
import com.fluxion.admin.repository.WfDefinitionRepository
import org.slf4j.*
import org.springframework.context.ApplicationListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Admin 内部动态路由配置存储。
 *
 * 默认从 `wf_definition` 表加载 scope = PLATFORM、category = META、status = ACTIVE、protocol = HTTP 的工作流，
 * 注册为 admin 自身的动态路由（admin 自举）。
 *
 * 在 `local` / `dev` 等本地开发 profile 下，admin 实例通常也承担 Worker 职责，
 * 因此会同时加载所有 PRIVATE 工作流，方便本地直接调试业务 API。
 */
@Component
class AdminRouteConfigStore(
    private val definitionRepository: WfDefinitionRepository,
    private val environment: Environment
) : RouteConfigStore, ApplicationListener<WorkflowDefinitionRouteRefreshEvent> {

    private val log = LoggerFactory.getLogger(javaClass)
    private var routeChangeListener: RouteChangeListener? = null

    private val isLocalProfile: Boolean
        get() = environment.activeProfiles.any { it.equals("local", ignoreCase = true) || it.equals("dev", ignoreCase = true) }

    override fun loadAll(): List<HttpRouteDefinition> {
        val definitions = if (isLocalProfile) {
            // 本地开发：加载 PLATFORM + 所有 PRIVATE（充当全量 Worker）
            definitionRepository.findAllActive()
                .filter { it.protocol == "HTTP" && !it.bindKey.isNullOrBlank() }
        } else {
            // 生产环境：只加载 PLATFORM（category=META 的元工作流）
            definitionRepository.findAllActive()
                .filter {
                    it.protocol == "HTTP" &&
                        it.scope == "PLATFORM" &&
                        it.category == "META" &&
                        !it.bindKey.isNullOrBlank()
                }
        }

        log.info {
            "Loaded ${definitions.size} admin HTTP routes from wf_definition " +
                "(isLocal=$isLocalProfile)"
        }

        return definitions.map { def ->
            val method = def.method ?: "POST"
            HttpRouteDefinition(
                routeKey = HttpRouteDefinition.keyOf(method, def.bindKey!!),
                path = def.bindKey!!,
                method = method,
                workflowId = def.workflowId,
                scope = def.scope,
                enabled = true
            )
        }
    }

    /**
     * 加载指定 appGroup 可见的路由（PLATFORM + 该 appGroup 的 PRIVATE）。
     * 供配置下发时使用。
     */
    fun loadAll(appGroup: String): List<HttpRouteDefinition> {
        val platformRoutes = definitionRepository.findAllActive()
            .filter {
                it.protocol == "HTTP" &&
                    it.scope == "PLATFORM" &&
                    !it.bindKey.isNullOrBlank()
            }

        val privateRoutes = definitionRepository.findAllActiveByScopeAndAppGroup("PRIVATE", appGroup)
            .filter { it.protocol == "HTTP" && !it.bindKey.isNullOrBlank() }

        val all = platformRoutes + privateRoutes
        log.info {
            "Loaded ${all.size} routes for appGroup=[$appGroup]: " +
                "${platformRoutes.size} PLATFORM + ${privateRoutes.size} PRIVATE"
        }

        return all.map { def ->
            val method = def.method ?: "POST"
            HttpRouteDefinition(
                routeKey = HttpRouteDefinition.keyOf(method, def.bindKey!!),
                path = def.bindKey!!,
                method = method,
                workflowId = def.workflowId,
                scope = def.scope,
                enabled = true
            )
        }
    }

    override fun watch(listener: RouteChangeListener) {
        this.routeChangeListener = listener
    }

    override fun onApplicationEvent(event: WorkflowDefinitionRouteRefreshEvent) {
        val listener = routeChangeListener
        if (listener == null) {
            log.debug { "RouteChangeListener not registered yet, skip refreshing admin routes" }
            return
        }
        val routes = loadAll()
        log.info { "Refreshing admin HTTP routes after workflow [${event.workflowId}] changed, loaded ${routes.size} routes" }
        listener.onRoutesChanged(routes)
    }
}
