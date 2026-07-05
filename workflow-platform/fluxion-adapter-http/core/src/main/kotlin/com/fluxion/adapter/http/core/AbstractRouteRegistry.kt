package com.fluxion.adapter.http.core

import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 框架无关的 HTTP 路由注册基类。
 *
 * 本类承担的职责（Spring MVC 和 WebFlux 实现共享）：
 *   - 路由 diff 算法：新增 / 移除过期 / 更新变更
 *   - PRIVATE > PLATFORM scope 优先级去重
 *   - 线程安全的并发状态管理
 *   - routeKey → HttpRouteDefinition 映射（唯一数据源）
 *
 * 子类只需实现框架相关的注册操作：
 *   - [doRegister]:   调用框架 API 注册路由（如 RequestMappingHandlerMapping 或 RouterFunction）
 *   - [doUnregister]: 调用框架 API 注销路由
 *   - [activeRouteKeys]: 返回当前已注册的 routeKey 集合
 */
abstract class AbstractRouteRegistry : RouteChangeListener {

    protected val log = LoggerFactory.getLogger(javaClass)

    /**
     * routeKey → 完整路由定义 — 所有路由状态的**唯一数据源**。
     *
     * `workflowId`、`scope`、`path`、`method` 均可通过存储的
     * [HttpRouteDefinition] 获取，无需额外的投影 Map。
     *
     * 由 [onRouteRegistered] / [onRouteUnregistered] 管理；子类通过它
     * 重建框架特定的路由对象或直接查询以解析请求。
     */
    val routeDefinitions = ConcurrentHashMap<String, HttpRouteDefinition>()

    /** 根据 routeKey 查找 workflowId（[routeDefinitions] 的便捷快捷方式） */
    fun resolveWorkflowId(routeKey: String): String? = routeDefinitions[routeKey]?.workflowId

    protected val lock = ReentrantLock()

    // ─── RouteChangeListener：diff 算法 ────────────────────────────

    /**
     * 配置中心推送新路由快照时触发。
     *
     * 执行 diff：
     *   1. 过滤已启用的路由
     *   2. 按 routeKey 去重（PRIVATE 优先于 PLATFORM）
     *   3. 移除不再存在或已禁用的路由
     *   4. 注册新路由；重新注册 workflowId 变更的路由
     */
    override fun onRoutesChanged(snapshot: List<HttpRouteDefinition>) {
        lock.withLock {
            val deduped = dedupeRoutes(snapshot)
            val newKeys = deduped.map { it.routeKey }.toSet()
            val oldKeys = activeRouteKeys()

            // 1. 移除过期或已禁用的路由
            val toRemove = oldKeys - newKeys
            toRemove.forEach { doUnregister(it) }

            // 2. 注册新路由 / 重新注册变更的路由
            deduped.forEach { route ->
                val existing = routeDefinitions[route.routeKey]?.workflowId
                when {
                    existing == null -> doRegister(route)
                    existing != route.workflowId -> {
                        doUnregister(route.routeKey)
                        doRegister(route)
                    }
                    // 路径和 workflowId 均未变更 → 跳过
                }
            }

            log.info {
                val added = newKeys - oldKeys
                val removed = toRemove
                "Route sync done: +${added.size} added, -${removed.size} removed, ${activeRouteKeys().size} total active"
            }
        }
    }

    /**
     * 使用启动时加载的路由进行初始化。
     * 由自动装配在应用上下文就绪后调用。
     */
    fun init(initialRoutes: List<HttpRouteDefinition>) {
        log.info { "Loading ${initialRoutes.size} initial HTTP routes..." }
        onRoutesChanged(initialRoutes)
    }

    // ─── 抽象钩子 ───────────────────────────────────────────────────

    /** 返回当前活跃路由 key 集合（由框架特定存储提供） */
    protected abstract fun activeRouteKeys(): Set<String>

    /** 在框架中注册路由（如 Spring MVC RequestMapping）或写入共享状态（如 WebFlux 直接查询） */
    protected abstract fun doRegister(route: HttpRouteDefinition)

    /** 按 routeKey 从框架中注销路由 */
    protected abstract fun doUnregister(routeKey: String)

    // ─── 共享内部状态管理 ──────────────────────────────────────────────

    /**
     * 框架注册成功后更新共享状态。
     * 子类应在 [doRegister] 末尾调用此方法。
     */
    protected fun onRouteRegistered(route: HttpRouteDefinition) {
        routeDefinitions[route.routeKey] = route
    }

    /**
     * 框架注销后清除共享状态。
     * 子类应在 [doUnregister] 末尾调用此方法。
     */
    protected fun onRouteUnregistered(routeKey: String) {
        routeDefinitions.remove(routeKey)
    }

    /** 当前已注册路由快照：routeKey → workflowId（用于健康检查/监控） */
    fun registeredRoutes(): Map<String, String> =
        routeDefinitions.mapValues { it.value.workflowId }

    // ─── 私有方法 ──────────────────────────────────────────────────────

    /** 去重：同一 routeKey 同时存在 PRIVATE 和 PLATFORM 时，PRIVATE 优先 */
    private fun dedupeRoutes(snapshot: List<HttpRouteDefinition>): List<HttpRouteDefinition> =
        snapshot
            .filter { it.enabled }
            .groupBy { it.routeKey }
            .map { (_, routes) ->
                routes.firstOrNull { it.scope == "PRIVATE" } ?: routes.first()
            }
}
