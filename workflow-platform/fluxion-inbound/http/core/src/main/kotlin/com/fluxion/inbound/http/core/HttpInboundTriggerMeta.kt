package com.fluxion.inbound.http.core

import com.fluxion.core.model.TriggerType
import com.fluxion.core.spi.LegacyBinding
import com.fluxion.core.spi.TriggerFunctionMeta
import com.fluxion.core.spi.TriggerFunctionParam

class HttpInboundTriggerMeta : TriggerFunctionMeta {

    override val functionRef = "trigger:httpInbound"
    override val label = "HTTP / HTTPS Endpoint"
    override val icon = "ApiOutlined"
    override val description =
        "Trigger workflow via HTTP REST endpoint. Path supports {param} variables; authentication determined by bound auth rules."

    override val paramSchema: List<TriggerFunctionParam> = listOf(
        TriggerFunctionParam(
            name = "method",
            type = "enum",
            required = true,
            label = "HTTP Method",
            defaultValue = "POST",
            options = listOf(
                "GET" to "GET (Read)",
                "POST" to "POST (Create/Command)",
                "PUT" to "PUT (Full Update)",
                "PATCH" to "PATCH (Partial Update)",
                "DELETE" to "DELETE (Delete)",
                "HEAD" to "HEAD",
                "OPTIONS" to "OPTIONS"
            )
        ),
        TriggerFunctionParam(
            name = "path",
            type = "string",
            required = true,
            label = "Request Path",
            description = "Supports /api/order/{orderId} path variables, must start with /. Globally unique (scoped by scope+appGroup).",
            defaultValue = "/api/example"
        ),
        TriggerFunctionParam(
            name = "authRequired",
            type = "boolean",
            label = "Require Authentication",
            description = "When enabled, callers must provide valid JWT; disabled allows anonymous calls (validation can still be done in workflow nodes).",
            defaultValue = true
        ),
        TriggerFunctionParam(
            name = "authRole",
            type = "string",
            label = "Required Roles (comma-separated)",
            description = "Empty = any authenticated user; specified = caller must have at least one role, e.g. WORKFLOW_USER,WORKFLOW_ADMIN.",
            defaultValue = ""
        ),
        TriggerFunctionParam(
            name = "corsAllowOrigin",
            type = "string",
            label = "CORS Allow Origin",
            description = "Default *; can specify specific domains like https://console.example.com, comma-separated.",
            defaultValue = "*"
        ),
        TriggerFunctionParam(
            name = "timeoutMs",
            type = "integer",
            label = "Request Timeout(ms)",
            description = "Maximum workflow execution time after HTTP request received; 0 = use container global default.",
            defaultValue = 60_000
        )
    )

    override fun extractLegacyBinding(config: Map<String, Any?>): LegacyBinding? {
        val path = config["path"]?.toString() ?: return null
        val method = config["method"]?.toString()
        val protocol = if (method == "CONNECT" || path.startsWith("https:")) "HTTPS" else "HTTP"
        return LegacyBinding(
            protocol = protocol,
            method = method,
            bindKey = path,
            triggerType = TriggerType.WEBHOOK
        )
    }

    override fun configFromLegacy(protocol: String, method: String?, bindKey: String?): Map<String, Any?> =
        mapOfNotNull(
            "method" to (method?.takeIf { it.isNotBlank() } ?: "POST"),
            "path" to (bindKey?.takeIf { it.isNotBlank() } ?: "/api/example")
        )

    private fun mapOfNotNull(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
        pairs.filter { it.second != null }.toMap()
}