package com.fluxion.adapter.spi

import com.fluxion.core.value.EngineResult
import com.fluxion.core.value.NodeExecutionRecord

/**
 * Protocol-agnostic request model used across all transport adapters.
 *
 * Each adapter (HTTP, Dubbo, gRPC, Kafka, internal) converts its native
 * request format into this structure before delegating to [WorkflowRouter].
 *
 * @param protocol       Protocol identifier: HTTP / DUBBO / GRPC / KAFKA / INTERNAL
 * @param workflowId     Workflow ID (resolved by WorkflowRouter from protocol/path/method;
 *                       may be null in routing-only mode)
 * @param headers        Request headers (HTTP headers, Dubbo attachments, gRPC metadata)
 * @param params         Parsed request parameters (sources vary by protocol)
 * @param rawBody        Raw request body (for debugging; may be null in production)
 * @param schemaFormat   Schema format identifier: "json-schema" / "protobuf" / "avro".
 *                       Extracted by adapters from headers like `X-Schema-Format`.
 *                       Falls back to WorkflowDefinition.inputSchemaFormat or default JSON Schema when null.
 * @param schemaName     Optional schema name for looking up full Schema definition from SchemaRegistry
 * @param schemaVersion  Optional schema version number; null = latest version
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

        /**
         * Factory for internal direct invocations that bypass routing resolution.
         *
         * @param workflowId Target workflow ID
         * @param params     Pre-parsed parameter map
         * @return UnifiedRequest with protocol=INTERNAL
         */
        @JvmStatic
        fun internal(workflowId: String, params: Map<String, Any>) =
            UnifiedRequest("INTERNAL", workflowId, emptyMap(), params)

        /**
         * Build a [UnifiedRequest] extracting Schema metadata from a protocol-agnostic
         * headers Map, avoiding duplicate parsing logic in every adapter.
         *
         * @param protocol   Protocol identifier
         * @param workflowId Workflow ID (may be null in routing mode)
         * @param headers    Request headers map
         * @param params     Parsed parameters
         * @param rawBody    Raw request body (optional)
         * @return UnifiedRequest with schema fields populated from headers
         */
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
 * Protocol-agnostic response model returned by [WorkflowRouter].
 *
 * Adapters convert this back to their native response format (HTTP JSON,
 * Dubbo return value, gRPC Any, etc.).
 *
 * Mirrors [UnifiedRequest] at the response layer so adapters do not need
 * to depend on fluxion-core's [EngineResult] directly.
 *
 * @param success          Whether workflow execution succeeded
 * @param data             Final workflow output data
 * @param executionId      Execution ID for distributed tracing
 * @param errorMsg         Error message (populated on failure)
 * @param trace            Node-level execution trace (for debug / gRPC streaming)
 * @param outputSchema     Output Schema content (passed through from WorkflowDefinition.outputSchema).
 *                         Adapters use this to choose serialization:
 *                         - gRPC: FileDescriptorProto -> DynamicMessage -> Any
 *                         - HTTP/other adapters may ignore this field
 * @param outputSchemaFormat Output Schema format: "json-schema" / "protobuf" / "avro".
 *                           Used with [outputSchema]; defaults to JSON Schema when null.
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
        /**
         * Convert from the engine-level result to the adapter-level response.
         *
         * @param result Engine execution result
         * @return UnifiedResponse with core fields mapped
         */
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
 * Workflow Router — central coordination interface on the adapter layer.
 *
 * Adapters (HTTP / RPC / MQ) convert transport-specific requests into a
 * [UnifiedRequest] and delegate execution to this router. The router is
 * responsible for workflow lookup, execution, and returning a [UnifiedResponse].
 */
interface WorkflowRouter {
    /**
     * Execute a workflow in blocking mode — for non-coroutine callers
     * (e.g. Dubbo, Kafka consumer, legacy code).
     *
     * @param request Protocol-agnostic request
     * @return Protocol-agnostic response
     */
    fun execute(request: UnifiedRequest): UnifiedResponse

    /**
     * Execute a workflow in suspend mode — non-blocking, for coroutine callers.
     *
     * Default implementation delegates to the blocking [execute] for backward
     * compatibility. Coroutine-native implementations (e.g. WebFlux) should
     * override this to avoid blocking the calling thread.
     *
     * @param request Protocol-agnostic request
     * @return Protocol-agnostic response
     */
    suspend fun executeSuspend(request: UnifiedRequest): UnifiedResponse = execute(request)
}
