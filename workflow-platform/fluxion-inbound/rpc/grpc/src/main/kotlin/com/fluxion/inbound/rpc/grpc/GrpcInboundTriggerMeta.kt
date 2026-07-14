package com.fluxion.inbound.rpc.grpc

import com.fluxion.core.model.TriggerType
import com.fluxion.core.spi.LegacyBinding
import com.fluxion.core.spi.TriggerFunctionMeta
import com.fluxion.core.spi.TriggerFunctionParam

class GrpcInboundTriggerMeta : TriggerFunctionMeta {

    override val functionRef = "trigger:grpcInbound"
    override val label = "gRPC Inbound"
    override val icon = "ThunderboltOutlined"
    override val description =
        "Expose workflow as a gRPC service. Generic mode infers marshaller from input/output Schema without proto; also supports manual binding to compiled Proto Service."

    override val paramSchema: List<TriggerFunctionParam> = listOf(
        TriggerFunctionParam(
            name = "packageName",
            type = "string",
            required = true,
            label = "Protobuf Package",
            description = "Package declaration value, e.g. example.order.v1; combined with serviceName for gRPC fully qualified name."
        ),
        TriggerFunctionParam(
            name = "serviceName",
            type = "string",
            required = true,
            label = "Service Name",
            defaultValue = "OrderWorkflowService"
        ),
        TriggerFunctionParam(
            name = "methodName",
            type = "string",
            required = true,
            label = "Method Name",
            description = "Case-sensitive, must match the rpc name in client proto exactly.",
            defaultValue = "Execute"
        ),
        TriggerFunctionParam(
            name = "callType",
            type = "enum",
            label = "Call Type",
            defaultValue = "UNARY",
            options = listOf(
                "UNARY" to "UNARY (Unary)",
                "SERVER_STREAMING" to "Server Streaming",
                "CLIENT_STREAMING" to "Client Streaming",
                "BIDI_STREAMING" to "Bidirectional Streaming"
            )
        ),
        TriggerFunctionParam(
            name = "deadlineMs",
            type = "integer",
            label = "Default Deadline(ms)",
            description = "Used when client does not set gRPC Deadline; 0 = never timeout.",
            defaultValue = 30_000
        ),
        TriggerFunctionParam(
            name = "enableReflection",
            type = "boolean",
            label = "Enable gRPC Reflection",
            description = "When enabled, grpcurl/grpcui can access directly; can be disabled in production.",
            defaultValue = true
        ),
        TriggerFunctionParam(
            name = "interceptors",
            type = "string",
            label = "Additional ServerInterceptor Bean names (comma-separated)",
            description = "List of Bean names for io.grpc.ServerInterceptor type beans in Spring container."
        )
    )

    override fun extractLegacyBinding(config: Map<String, Any?>): LegacyBinding? {
        val packageName = config["packageName"]?.toString()
        val serviceName = config["serviceName"]?.toString()
        val methodName = config["methodName"]?.toString()
        val bindKey = when {
            packageName != null && serviceName != null && methodName != null -> "$packageName.$serviceName/$methodName"
            packageName != null && serviceName != null -> "$packageName.$serviceName"
            serviceName != null -> serviceName
            else -> return null
        }
        return LegacyBinding(
            protocol = "GRPC",
            method = config["callType"]?.toString(),
            bindKey = bindKey,
            triggerType = TriggerType.API
        )
    }

    override fun configFromLegacy(protocol: String, method: String?, bindKey: String?): Map<String, Any?> {
        val bk = bindKey ?: return emptyMap()
        val slashParts = bk.split('/', limit = 2)
        val pkgAndService = slashParts[0]
        val methodFromBind = slashParts.getOrNull(1)
        val dotIdx = pkgAndService.lastIndexOf('.')
        val (pkg, svc) = if (dotIdx < 0) "" to pkgAndService else pkgAndService.substring(0, dotIdx) to pkgAndService.substring(dotIdx + 1)
        return mapOfNotNull(
            "packageName" to pkg.takeIf { it.isNotBlank() },
            "serviceName" to svc.takeIf { it.isNotBlank() },
            "methodName" to (methodFromBind?.takeIf { it.isNotBlank() } ?: method?.takeIf { it.isNotBlank() } ?: "Execute"),
            "callType" to method?.takeIf { it.isNotBlank() }
        )
    }

    private fun mapOfNotNull(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
        pairs.filter { it.second != null }.toMap()
}