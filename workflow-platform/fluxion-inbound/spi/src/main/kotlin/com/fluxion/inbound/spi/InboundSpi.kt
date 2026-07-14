package com.fluxion.inbound.spi

import com.fluxion.core.value.EngineResult
import com.fluxion.core.value.NodeExecutionRecord

/**
 * Protocol-agnostic request model used across all transport adapters.
 *
 * Each adapter (HTTP, Dubbo, gRPC, Kafka, internal) converts its native
 * request format into this structure before delegating to [InboundRouter].
 */
data class UnifiedRequest(
    val protocol: String,
    val workflowId: String?,
    val headers: Map<String, String>,
    val params: Map<String, Any>,
    val rawBody: String? = null,
    val schemaFormat: String? = null,
    val schemaName: String? = null,
    val schemaVersion: Long? = null
) {
    companion object {
        const val HEADER_SCHEMA_FORMAT = "X-Schema-Format"
        const val HEADER_SCHEMA_NAME = "X-Schema-Name"
        const val HEADER_SCHEMA_VERSION = "X-Schema-Version"

        @JvmStatic
        fun internal(workflowId: String, params: Map<String, Any>) =
            UnifiedRequest("INTERNAL", workflowId, emptyMap(), params)

        @JvmStatic
        fun withSchemaFromHeaders(
            protocol: String,
            workflowId: String?,
            headers: Map<String, String>,
            params: Map<String, Any>,
            rawBody: String? = null
        ): UnifiedRequest {
            val schemaFormat = headers[HEADER_SCHEMA_FORMAT]
            val schemaName = headers[HEADER_SCHEMA_NAME]
            val schemaVersion = headers[HEADER_SCHEMA_VERSION]?.toLongOrNull()
            return UnifiedRequest(
                protocol, workflowId, headers, params, rawBody,
                schemaFormat, schemaName, schemaVersion
            )
        }
    }
}

/**
 * Protocol-agnostic response model returned by [InboundRouter].
 */
data class UnifiedResponse(
    val success: Boolean,
    val data: Any? = null,
    val executionId: String? = null,
    val errorMsg: String? = null,
    val trace: List<NodeExecutionRecord> = emptyList(),
    val outputSchema: Any? = null,
    val outputSchemaFormat: String? = null
) {
    companion object {
        @JvmStatic
        fun from(result: EngineResult) = UnifiedResponse(
            success = result.success,
            data = result.data,
            executionId = result.executionId,
            errorMsg = result.errorMsg,
            trace = result.trace.toList()
        )
    }
}

/**
 * Inbound Router — central coordination interface on the adapter layer.
 *
 * Receives protocol-agnostic [UnifiedRequest] from any transport adapter and routes
 * to the appropriate workflow definition for execution.
 */
interface InboundRouter {
    fun execute(request: UnifiedRequest): UnifiedResponse

    suspend fun executeSuspend(request: UnifiedRequest): UnifiedResponse = execute(request)
}
