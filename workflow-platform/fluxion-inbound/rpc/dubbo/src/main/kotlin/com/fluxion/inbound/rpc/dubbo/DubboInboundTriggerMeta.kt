package com.fluxion.inbound.rpc.dubbo

import com.fluxion.core.model.TriggerType
import com.fluxion.core.spi.LegacyBinding
import com.fluxion.core.spi.TriggerFunctionMeta
import com.fluxion.core.spi.TriggerFunctionParam

class DubboInboundTriggerMeta : TriggerFunctionMeta {

    override val functionRef = "trigger:dubboInbound"
    override val label = "Dubbo RPC Inbound"
    override val icon = "ShareAltOutlined"
    override val description =
        "Expose workflow as an Apache Dubbo generic service; callers address via standard Dubbo interface, supporting version/group/load balancing."

    override val paramSchema: List<TriggerFunctionParam> = listOf(
        TriggerFunctionParam(
            name = "interfaceFqcn",
            type = "string",
            required = true,
            label = "Interface FQCN",
            description = "Full Java interface path that callers depend on, e.g. com.example.order.OrderService"
        ),
        TriggerFunctionParam(
            name = "methodName",
            type = "string",
            required = true,
            label = "Method Name",
            description = "Dubbo method name to map to current workflow; multiple methods in one interface → multiple triggers.",
            defaultValue = "execute"
        ),
        TriggerFunctionParam(
            name = "version",
            type = "string",
            label = "Version (optional)",
            description = "Corresponds to Dubbo version filter; empty string matches any version."
        ),
        TriggerFunctionParam(
            name = "group",
            type = "string",
            label = "Group (optional)",
            description = "Corresponds to Dubbo group filter; empty string matches any group."
        ),
        TriggerFunctionParam(
            name = "timeoutMs",
            type = "integer",
            label = "Service Timeout(ms)",
            defaultValue = 30_000
        ),
        TriggerFunctionParam(
            name = "retries",
            type = "integer",
            label = "Retry Count",
            description = "Dubbo framework-level retry, 0=none. Note: only idempotent workflows should use > 0.",
            defaultValue = 0
        ),
        TriggerFunctionParam(
            name = "registryId",
            type = "string",
            label = "Registry Alias",
            description = "Alias for DUBBO_REGISTRY resource bound to App; empty = use default registry."
        )
    )

    override fun extractLegacyBinding(config: Map<String, Any?>): LegacyBinding? {
        val interfaceFqcn = config["interfaceFqcn"]?.toString() ?: return null
        val methodName = config["methodName"]?.toString()
        return LegacyBinding(
            protocol = "DUBBO",
            method = methodName,
            bindKey = interfaceFqcn,
            triggerType = TriggerType.API
        )
    }

    override fun configFromLegacy(protocol: String, method: String?, bindKey: String?): Map<String, Any?> =
        mapOfNotNull(
            "interfaceFqcn" to bindKey?.takeIf { it.isNotBlank() },
            "methodName" to (method?.takeIf { it.isNotBlank() } ?: "execute")
        )

    private fun mapOfNotNull(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
        pairs.filter { it.second != null }.toMap()
}