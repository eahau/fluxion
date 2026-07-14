package com.fluxion.inbound.rpc.grpc

import com.fluxion.inbound.rpc.grpc.proto.WorkflowServiceGrpc
import com.fluxion.inbound.rpc.grpc.proto.WorkflowStreamEvent
import com.fluxion.inbound.spi.UnifiedRequest
import com.fluxion.inbound.spi.UnifiedResponse
import com.fluxion.inbound.spi.InboundRouter
import com.fluxion.core.exception.WorkflowException
import com.fluxion.core.util.JsonUtil
import com.fluxion.schema.api.SchemaBackedMap
import com.fluxion.schema.api.SchemaDataProviderRegistry
import com.fluxion.schema.api.SchemaRegistry
import com.fluxion.schema.model.SchemaFormat
import com.google.protobuf.Any as ProtoAny
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.Struct
import com.google.protobuf.util.JsonFormat
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * gRPC workflow service implementation (zero Spring).
 *
 * Handles two RPCs:
 * 1. **`execute(google.protobuf.Any)`** → `google.protobuf.Any`
 *    Standard request/response. Caller passes a protobuf `Any` whose `type_url`
 *    identifies the registered workflow's input-schema name. We translate to
 *    [UnifiedRequest], run the workflow via [InboundRouter], then encode the
 *    result back — either as the caller-specified PROTOBUF output schema OR
 *    (if no proto output schema exists) as `google.protobuf.Struct` (arbitrary
 *    JSON → proto bridging via `JsonFormat.parser`).
 *
 * 2. **`executeStream(google.protobuf.Any)`** → `stream WorkflowStreamEvent`
 *    Server-side streaming of DAG execution progress. Each node completion
 *    emits one event (`NODE_COMPLETE` or `NODE_ERROR`); the stream terminates
 *    with a single `WORKFLOW_DONE` event carrying the final workflow result.
 *    Used by callers that need real-time per-node progress on long-running
 *    workflows (e.g. multi-step async DAGs).
 *
 * Schema resolution is cached keyed by the resolved full type URL so a given
 * PROTOBUF descriptor is built at most once per JVM (builds are expensive
 * because they require `Descriptors.FileDescriptor.buildFrom` which validates
 * the schema).
 */
class GrpcWorkflowServiceImpl(
    private val inboundRouter: InboundRouter,
    private val schemaRegistry: SchemaRegistry,
    private val providerRegistry: SchemaDataProviderRegistry? = null
) : WorkflowServiceGrpc.WorkflowServiceImplBase() {

    companion object {
        /** gRPC metadata key for direct execution by workflow id. */
        const val HEADER_WORKFLOW_ID = "x-workflow-id"
        /** gRPC metadata key for bind-key routing (matches HTTP/Dubbo convention). */
        const val HEADER_SERVICE_KEY = "x-service-key"
        /** Standard prefix used by `google.protobuf.Any` type URLs. */
        private const val TYPE_URL_PREFIX = "type.googleapis.com/"
    }

    private val log = LoggerFactory.getLogger(javaClass)
    /** Cache of compiled protobuf descriptors, keyed by schema name / type url. */
    private val descriptorCache: ConcurrentHashMap<String, Descriptors.Descriptor> = ConcurrentHashMap()

    /**
     * Unary execute — request Any, response Any.
     */
    override fun execute(payload: ProtoAny, responseObserver: StreamObserver<ProtoAny>) {
        try {
            val unified = buildUnifiedRequest(payload)
            val result = inboundRouter.execute(unified)

            if (!result.success) {
                responseObserver.onError(
                    Status.INTERNAL
                        .withDescription(result.errorMsg ?: "Workflow execution failed")
                        .asRuntimeException()
                )
                return
            }

            val responseAny = buildResponseAny(result)
            responseObserver.onNext(responseAny)
            responseObserver.onCompleted()
        } catch (ex: Exception) {
            log.error(ex) { "gRPC execute error: ${ex.message}" }
            responseObserver.onError(toStatusException(ex))
        }
    }

    /**
     * Streaming execute — request Any, stream of WorkflowStreamEvent.
     *
     * Walks the post-execution `trace` list stored on `UnifiedResponse` — the
     * runtime has already populated the entire trace synchronously before we
     * return (this method is a transport-layer streamer, NOT a reactive
     * back-pressure aware subscriber). For a true per-node subscriber the
     * caller should instead wire a DAG-node-level event listener.
     */
    override fun executeStream(payload: ProtoAny, responseObserver: StreamObserver<WorkflowStreamEvent>) {
        try {
            val unified = buildUnifiedRequest(payload)
            val result = inboundRouter.execute(unified)

            result.trace.forEach { record ->
                val eventType = if (record.status.name == "FAILED")
                    WorkflowStreamEvent.EventType.NODE_ERROR
                else
                    WorkflowStreamEvent.EventType.NODE_COMPLETE

                responseObserver.onNext(
                    WorkflowStreamEvent.newBuilder()
                        .setEventType(eventType)
                        .setNodeId(record.nodeId)
                        .setDataJson(JsonUtil.serialize(record.output))
                        .setTimestamp(System.nanoTime() / 1_000_000L)
                        .build()
                )
            }
            responseObserver.onNext(
                WorkflowStreamEvent.newBuilder()
                    .setEventType(WorkflowStreamEvent.EventType.WORKFLOW_DONE)
                    .setDataJson(JsonUtil.serialize(result.data))
                    .setTimestamp(System.currentTimeMillis())
                    .build()
            )
            responseObserver.onCompleted()
        } catch (ex: Exception) {
            log.error(ex) { "gRPC stream execution error: ${ex.message}" }
            responseObserver.onError(toStatusException(ex))
        }
    }

    // ----- Request assembly ---------------------------------------------------

    /**
     * Convert a protobuf-Any payload + gRPC metadata into [UnifiedRequest].
     * Routing mirrors HTTP/Dubbo: prefer `x-workflow-id` over `x-service-key`.
     */
    private fun buildUnifiedRequest(payload: ProtoAny): UnifiedRequest {
        val params = parseProtobufPayload(payload)
        val metadata = extractMetadata()
        val workflowId = metadata[HEADER_WORKFLOW_ID].orEmpty()
        val serviceKey = metadata[HEADER_SERVICE_KEY].orEmpty()

        return if (workflowId.isNotBlank()) {
            UnifiedRequest.withSchemaFromHeaders("INTERNAL", workflowId, metadata, params)
        } else {
            UnifiedRequest.withSchemaFromHeaders("GRPC", serviceKey, metadata, params)
        }
    }

    /**
     * Parse `google.protobuf.Any` into a Map<String, Any> view.
     *
     * Pipeline:
     * 1. `type_url.last('/')` → registered schema name.
     * 2. Look up schema in [schemaRegistry] (must be `PROTOBUF` format).
     * 3. Build/retrieve cached protobuf Descriptor.
     * 4. Parse raw bytes into [DynamicMessage].
     * 5. Wrap via [SchemaBackedMap.wrap] so downstream functions see a Map
     *    API backed by the real protobuf message (zero-copy field reads).
     */
    private fun parseProtobufPayload(payload: ProtoAny): Map<String, Any> {
        require(payload.typeUrl.isNotBlank()) { "Any.type_url is required" }

        val schemaName = payload.typeUrl.substringAfterLast('/')

        val schema = schemaRegistry.get(schemaName)
            ?: throw IllegalArgumentException("Schema not found in registry: `$schemaName")
        require(schema.format == SchemaFormat.PROTOBUF) {
            "Schema [$schemaName] is not PROTOBUF format: ${schema.format}"
        }

        val descriptor = descriptorCache.computeIfAbsent(schemaName) { _: String ->
            buildDescriptor(schema.parsed)
        }

        val message = DynamicMessage.parseFrom(descriptor, payload.value)

        val provider = providerRegistry?.getProvider(SchemaFormat.PROTOBUF)
        @Suppress("UNCHECKED_CAST")
        return SchemaBackedMap.wrap(message, provider) as Map<String, Any>
    }

    // ----- Response encoding --------------------------------------------------

    /**
     * Encode a workflow result as `google.protobuf.Any`.
     *
     * Strategy by output-schema availability:
     * - If the workflow declared a PROTOBUF output schema → build dynamic proto
     *   response using that schema (callers get strong typing on the wire).
     * - Otherwise → round-trip result through JSON into `google.protobuf.Struct`
     *   (universal fallback that carries any JSON-serializable map).
     */
    private fun buildResponseAny(result: UnifiedResponse): ProtoAny {
        val data = result.data ?: return ProtoAny.getDefaultInstance()
        val schema = result.outputSchema

        if (schema != null && isProtobufSchema(schema)) {
            return buildProtobufResponse(data, schema)
        }

        return buildStructResponse(data)
    }

    /** Encode the result against the caller-supplied protobuf output schema. */
    private fun buildProtobufResponse(data: Any, outputSchema: Any): ProtoAny {
        val fileDescProto = resolveOutputFileDescriptor(outputSchema)
        val messageName = resolveMessageName(fileDescProto)
        val typeUrl = "$TYPE_URL_PREFIX$messageName"

        val descriptor = descriptorCache.computeIfAbsent(typeUrl) { _: String ->
            buildDescriptor(fileDescProto)
        }

        val json = JsonUtil.serialize(data)
        val builder = DynamicMessage.newBuilder(descriptor)
        JsonFormat.parser().merge(json, builder)
        val message = builder.build()

        return ProtoAny.newBuilder()
            .setTypeUrl(typeUrl)
            .setValue(message.toByteString())
            .build()
    }

    /** Encode arbitrary data via `google.protobuf.Struct` (JSON ↔ proto bridge). */
    private fun buildStructResponse(data: Any): ProtoAny {
        val json = JsonUtil.serialize(data)
        val structBuilder = Struct.newBuilder()
        JsonFormat.parser().merge(json, structBuilder)
        val struct = structBuilder.build()

        return ProtoAny.newBuilder()
            .setTypeUrl("${TYPE_URL_PREFIX}google.protobuf.Struct")
            .setValue(struct.toByteString())
            .build()
    }

    // ----- Schema helpers -----------------------------------------------------

    /**
     * Best-effort detector — accepts FileDescriptorProto directly, a
     * pre-serialised JSON string, or a Map mirror of the proto JSON form.
     */
    private fun isProtobufSchema(schema: Any): Boolean {
        return when (schema) {
            is DescriptorProtos.FileDescriptorProto -> true
            is Map<*, *> -> schema.containsKey("messageType")
            is String -> schema.contains("\"messageType\"")
            else -> false
        }
    }

    /** Normalise an arbitrary output-schema carrier into a FileDescriptorProto. */
    private fun resolveOutputFileDescriptor(outputSchema: Any): DescriptorProtos.FileDescriptorProto {
        if (outputSchema is DescriptorProtos.FileDescriptorProto) {
            return outputSchema
        }

        val json = when (outputSchema) {
            is String -> outputSchema
            is Map<*, *> -> JsonUtil.serialize(outputSchema)
            else -> throw IllegalArgumentException(
                "Unsupported outputSchema type: ${outputSchema::class.java.name}"
            )
        }

        val builder = DescriptorProtos.FileDescriptorProto.newBuilder()
        JsonFormat.parser().merge(json, builder)
        return builder.build()
    }

    /** Pick the first declared message type as the response message (convention). */
    private fun resolveMessageName(fileDescProto: DescriptorProtos.FileDescriptorProto): String {
        require(fileDescProto.messageTypeCount > 0) {
            "FileDescriptorProto contains no message type definitions"
        }
        val pkg = if (fileDescProto.hasPackage()) "${fileDescProto.`package`}." else ""
        return "$pkg${fileDescProto.getMessageType(0).name}"
    }

    /**
     * Compile a `FileDescriptorProto` into a live `Descriptors.Descriptor` for
     * the first declared message type. Throws if descriptor validation fails.
     */
    private fun buildDescriptor(parsed: Any): Descriptors.Descriptor {
        val fileDescriptorProto = parsed as? DescriptorProtos.FileDescriptorProto
            ?: throw IllegalArgumentException("Schema parsed object is not a FileDescriptorProto")
        require(fileDescriptorProto.messageTypeCount > 0) {
            "FileDescriptorProto contains no message type definitions"
        }

        val fileDescriptor = try {
            Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, emptyArray())
        } catch (e: Descriptors.DescriptorValidationException) {
            throw IllegalStateException("Failed to build FileDescriptor: ${e.message}", e)
        }

        return fileDescriptor.messageTypes.first()
    }

    // ----- Metadata / error helpers ------------------------------------------

    /** Read the captured metadata map (set by [GrpcMetadataInterceptor]) from gRPC Context. */
    private fun extractMetadata(): Map<String, String> =
        GrpcMetadataInterceptor.METADATA_CONTEXT_KEY.get() ?: emptyMap()

    /**
     * Translate generic + workflow exceptions into gRPC canonical Status codes.
     *
     * Intentional mapping:
     * - `WorkflowException` → `INTERNAL` with prefixed description (retryable
     *   by default; callers inspect the bracketed error-code).
     * - `IllegalArgumentException` → `INVALID_ARGUMENT` (caller supplied bad
     *   payload / missing route key; never retry).
     * - Anything else → `INTERNAL` (generic infrastructure error; log full stack).
     */
    private fun toStatusException(ex: Throwable): StatusRuntimeException {
        return when (ex) {
            is WorkflowException -> {
                log.warn { "gRPC workflow error [${ex.errorCode}]: ${ex.message}" }
                Status.INTERNAL
                    .withDescription("[${ex.errorCode}] ${ex.message ?: "Workflow execution failed"}")
                    .asRuntimeException()
            }

            is IllegalArgumentException -> {
                Status.INVALID_ARGUMENT
                    .withDescription(ex.message ?: "Invalid argument")
                    .asRuntimeException()
            }

            else -> {
                log.error(ex) { "gRPC unexpected error: ${ex.message}" }
                Status.INTERNAL
                    .withDescription(ex.message ?: "Internal error")
                    .asRuntimeException()
            }
        }
    }
}
