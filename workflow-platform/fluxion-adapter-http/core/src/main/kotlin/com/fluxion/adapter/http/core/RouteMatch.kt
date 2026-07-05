package com.fluxion.adapter.http.core

/**
 * Route resolution result — framework-agnostic.
 *
 * Returned by both Spring MVC and WebFlux registry implementations
 * when a request path matches a registered route pattern.
 *
 * @param workflowId   Target workflow ID to execute
 * @param pathVariables Extracted URI variables (e.g. /api/users/{id} → id=123)
 */
data class RouteMatch(
    val workflowId: String,
    val pathVariables: Map<String, String>
)
