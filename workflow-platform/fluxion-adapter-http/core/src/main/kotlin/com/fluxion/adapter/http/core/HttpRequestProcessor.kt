package com.fluxion.adapter.http.core

import com.fluxion.core.util.JsonUtil

/**
 * Shared constants and helpers for both the Spring MVC and WebFlux HTTP adapters.
 *
 * Lives in a `-core` module (zero Spring dependencies) so both adapter stacks
 * can reference the same attribute/header names without string duplication.
 */
object HttpRequestProcessor {

    /**
     * Request attribute / ServerWebExchange attribute key under which the
     * pre-resolved [RouteMatch] is stored by the MVC interceptor or the
     * WebFlux HandlerMapping, ready for the handler to consume without
     * re-doing pattern matching.
     */
    const val ATTR_ROUTE_MATCH = "fluxion.routeMatch"

    /**
     * Response header name for the opaque execution ID produced by the DAG
     * engine. Lets callers correlate HTTP responses with execution-snapshot
     * entries and distributed traces.
     */
    const val HEADER_EXECUTION_ID = "X-Execution-Id"
}

/**
 * Merge request parameters from three independent sources into a single
 * flat `Map<String, Any>` with well-defined precedence.
 *
 * Priority (highest → lowest):
 * 1. **Path variables** (`/api/functions/{name}/publish` → name=demo) —
 *    these are always intentionally constrained by the route template so
 *    they win over any user-controlled input.
 * 2. **URL query parameters** — single-valued → `String`, multi-valued → `List`.
 *    Skipped if the same key already came from a path variable.
 * 3. **JSON body** — the raw string body is JSON-deserialised and every key
 *    is added only if not already present. If JSON parsing fails, the body
 *    is preserved under the `_rawBody` fallback key so workflow functions
 *    can inspect it manually (form-data, XML, legacy formats).
 *
 * @param pathVariables Template-variable bindings extracted by pattern matching
 * @param queryParams   URL query string bindings (converted via Servlet / WebFlux API)
 * @param body          Raw request body as a string (null for GET requests, etc.)
 * @return Merged parameter map — safe mutable copy, writes do not affect inputs
 */
fun mergeParams(
    pathVariables: Map<String, String>,
    queryParams: Map<String, Any>,
    body: String?,
): Map<String, Any> {
    val params = mutableMapOf<String, Any>()

    params.putAll(pathVariables)

    queryParams.forEach { (k, v) ->
        if (k !in params) params[k] = v
    }

    if (!body.isNullOrBlank()) {
        try {
            JsonUtil.toMap(body).forEach { (k, v) ->
                if (k !in params) params[k] = v
            }
        } catch (_: Exception) {
            params["_rawBody"] = body
        }
    }

    return params
}
