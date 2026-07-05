package com.fluxion.adapter.http.core

import com.fluxion.core.util.JsonUtil

/**
 * HTTP adapter constants shared by Spring MVC and WebFlux implementations.
 */
object HttpRequestProcessor {

    /** Request attribute key: [RouteMatch] injected by route registry/interceptor */
    const val ATTR_ROUTE_MATCH = "fluxion.routeMatch"

    /** Response header: execution ID for tracing */
    const val HEADER_EXECUTION_ID = "X-Execution-Id"
}

/**
 * Merge params from three sources with priority (highest → lowest):
 *   1. Path variables  (e.g. /api/functions/{name}/publish → name=demo)
 *   2. URL query params (single value → String, multi value → List)
 *   3. Request body JSON (parse failure degrades to _rawBody)
 */
fun mergeParams(
    pathVariables: Map<String, String>,
    queryParams: Map<String, Any>,
    body: String?,
): Map<String, Any> {
    val params = mutableMapOf<String, Any>()

    // 1. Path variables highest priority
    params.putAll(pathVariables)

    // 2. Query params (skip if key already present from path variable)
    queryParams.forEach { (k, v) ->
        if (k !in params) params[k] = v
    }

    // 3. JSON body (lowest priority)
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
