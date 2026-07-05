package com.fluxion.adapter.http.core

/**
 * HTTP 路由定义（admin 后台发布后通过配置中心下发）
 *
 * 与具体 HTTP 框架无关，可被 Spring MVC、WebFlux、JAX-RS 等实现复用。
 *
 * @param routeKey   路由唯一标识（通常为 "METHOD:path"，用于追踪注册状态）
 * @param path       HTTP 路径模式，支持路径变量：/api/user/{id}
 * @param method     HTTP 方法（GET / POST / PUT / DELETE / PATCH）
 * @param workflowId 对应的工作流 ID（wf_definition.workflow_id）
 * @param scope      工作流作用域（PLATFORM / PRIVATE / MARKETPLACE）
 * @param enabled    是否启用（false 时由具体框架实现注销）
 *
 * 配置中心 JSON 格式示例（Nacos dataId: workflow.http.routes）：
 * ```json
 * {
 *   "routes": [
 *     {
 *       "routeKey":   "POST:/api/user/login",
 *       "path":       "/api/user/login",
 *       "method":     "POST",
 *       "workflowId": "user-login-workflow",
 *       "scope":      "PRIVATE",
 *       "enabled":    true
 *     }
 *   ]
 * }
 * ```
 */
data class HttpRouteDefinition(
    val routeKey:   String,
    val path:       String,
    val method:     String,
    val workflowId: String,
    val scope:      String  = "PRIVATE",
    val enabled:    Boolean = true
) {
    companion object {
        /** 规范化 routeKey：METHOD:path */
        fun keyOf(method: String, path: String) = "${method.uppercase()}:$path"
    }
}
