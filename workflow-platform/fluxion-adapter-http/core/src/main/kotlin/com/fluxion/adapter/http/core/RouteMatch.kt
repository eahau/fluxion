package com.fluxion.adapter.http.core

/**
 * Immutable result of an HTTP route resolution pass — framework-agnostic.
 *
 * Both the Spring MVC interceptor path and the WebFlux `HandlerMapping`
 * path produce the same structure so the downstream handler can share the
 * parameter-merging logic (`mergeParams`) across stacks.
 *
 * @param workflowId    Primary key of the matched workflow — this is what
 *                      gets handed to [WorkflowRouter.executeSuspend].
 * @param pathVariables URI template bindings extracted by pattern matching
 *                      (e.g. `/api/users/{id}` → `{id=123}`). Empty map for
 *                      exact-match routes with no template variables.
 */
data class RouteMatch(
    val workflowId: String,
    val pathVariables: Map<String, String>
)
