package com.fluxion.inbound.http.core

/**
 * HTTP route transfer object — published from Admin via a config center and
 * consumed by both Spring MVC and WebFlux Worker adapters.
 *
 * Purposefully framework-agnostic so the same DTO can drive the Spring MVC
 * dynamic `RequestMappingHandlerMapping` registration path and the WebFlux
 * direct-query `WorkflowHandlerMapping` path without recompilation or
 * duplicated definitions.
 *
 * Config-center JSON shape (Nacos dataId `workflow.http.routes`):
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
 *
 * @param routeKey   Opaque unique key for the route. Conventionally `"METHOD:path"`;
 *                   used as the primary identity for diff/register/unregister operations.
 * @param path       URL path template supporting Spring-style path variables
 *                   (`/api/users/{id}`, `/api/orders/{orderId}/items/{itemId}`).
 * @param method     Upper-case HTTP method token: GET / POST / PUT / DELETE / PATCH.
 * @param workflowId Primary key of the workflow that should execute when this
 *                   route matches a request (`wf_definition.workflow_id`).
 * @param scope      Scope label (PLATFORM / PRIVATE / MARKETPLACE). Used as a
 *                   dedup tie-breaker when the same `routeKey` is defined in
 *                   multiple scopes (PRIVATE wins over PLATFORM if both present).
 * @param enabled    Soft-delete flag; `false` routes are filtered out by the
 *                   config-center layer before reaching the live registry.
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
        /**
         * Build the canonical `"METHOD:path"` route key.
         *
         * @param method HTTP method (normalized to upper-case internally)
         * @param path   URL path template
         * @return Canonical opaque route key used as the registry identity
         */
        fun keyOf(method: String, path: String) = "${method.uppercase()}:$path"
    }
}
