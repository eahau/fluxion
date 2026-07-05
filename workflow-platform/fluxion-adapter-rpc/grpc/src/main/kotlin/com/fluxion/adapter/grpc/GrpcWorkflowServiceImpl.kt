package com.fluxion.adapter.grpc

import com.fluxion.adapter.grpc.proto.WorkflowServiceGrpc
import com.fluxion.adapter.grpc.proto.WorkflowStreamEvent
import com.fluxion.adapter.spi.UnifiedRequest
import com.fluxion.adapter.spi.UnifiedResponse
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.core.exception.WorkflowException
import com.fluxion.core.util.JsonUtil
import com.fluxion.schema.api.SchemaBackedMap
import com.fluxion.schema.api.SchemaDataProviderRegistry
import com.fluxion.schema.api.SchemaRegistry
import com.fluxion.schema.model.SchemaFormat
import com.google.protobuf.Any
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
 * gRPC 工作流服务实现（零 Spring）
 *
 * 实现 proto 定义的 WorkflowService：
 *   - Execute: 同步单次执行，请求/响应均为 `google.protobuf.Any`
 *   - ExecuteStream: 流式执行，推送节点级别事件
 *
 * ### 协议设计
 * - **请求参数**：`Any` — `type_url` 标识输入 schema，`value` 为序列化字节
 * - **响应数据**：`Any` — 服务端从 `WorkflowDefinition.outputSchema` 自动构建 DynamicMessage
 * - **路由标识**：通过 gRPC metadata 传递（`x-workflow-id` / `x-service-key`）
 * - **错误处理**：通过 gRPC 原生 `StatusRuntimeException` 返回，无需 message 包装
 *
 * 约定：SchemaRegistry 中的 schema 名称必须与 protobuf message 全限定名一致。
 *
 * @param workflowRouter    工作流路由器
 * @param schemaRegistry    Schema 注册表，用于动态解析/构建 DynamicMessage
 * @param providerRegistry  Schema 数据提供者注册表，null 时 DynamicMessage 降级为 Map
 */
class GrpcWorkflowServiceImpl(
    private val workflowRouter: WorkflowRouter,
    private val schemaRegistry: SchemaRegistry,
    private val providerRegistry: SchemaDataProviderRegistry? = null
) : WorkflowServiceGrpc.WorkflowServiceImplBase() {

    companion object {
        /** gRPC metadata key：工作流 ID（直接执行，跳过路由） */
        const val HEADER_WORKFLOW_ID = "x-workflow-id"
        /** gRPC metadata key：服务键（按 bind_key 路由） */
        const val HEADER_SERVICE_KEY = "x-service-key"

        /** Any.type_url 前缀 */
        private const val TYPE_URL_PREFIX = "type.googleapis.com/"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    // ─── Execute RPC ──────────────────────────────────────────────

    /**
     * gRPC Execute RPC 实现（同步单次执行）。
     *
     * 成功：将工作流输出序列化为 DynamicMessage → Any 返回
     * 失败：抛出 StatusRuntimeException
     */
    override fun execute(payload: Any, responseObserver: StreamObserver<Any>) {
        try {
            val unified = buildUnifiedRequest(payload)
            val result = workflowRouter.execute(unified)

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

    // ─── ExecuteStream RPC ────────────────────────────────────────

    /**
     * 流式执行：执行工作流并推送节点级别事件
     * 每个节点完成时推送一个 NODE_COMPLETE 事件
     * 全部完成后推送 WORKFLOW_DONE 并关闭流
     */
    override fun executeStream(payload: Any, responseObserver: StreamObserver<WorkflowStreamEvent>) {
        try {
            val unified = buildUnifiedRequest(payload)
            val result = workflowRouter.execute(unified)

            // 推送各节点完成事件
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
            // 推送工作流完成事件
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

    // ─── 请求解析 ─────────────────────────────────────────────────

    /**
     * 将 Any payload + metadata 转换为 UnifiedRequest。
     *
     * 路由策略（从 gRPC metadata 读取）：
     *   - x-workflow-id 非空 → 直接按 ID 执行（跳过路由）
     *   - x-service-key 非空 → 按 bind_key 路由
     */
    private fun buildUnifiedRequest(payload: Any): UnifiedRequest {
        val params = parseProtobufPayload(payload)
        val metadata = extractMetadata()
        val workflowId = metadata[HEADER_WORKFLOW_ID].orEmpty()
        val serviceKey = metadata[HEADER_SERVICE_KEY].orEmpty()

        return if (workflowId.isNotBlank()) {
            UnifiedRequest.withSchemaFromHeaders("INTERNAL", workflowId, metadata, params)
        } else {
            UnifiedRequest.withSchemaFromHeaders("GRPC:$serviceKey", null, metadata, params)
        }
    }

    /**
     * 动态解析 Any payload 为 SchemaBackedMap(DynamicMessage)。
     *
     * 流程：
     * 1. 从 Any.type_url 末段提取 message 全限定名作为 schema 名称
     * 2. 从 SchemaRegistry 获取 FileDescriptorProto
     * 3. 构建 Descriptors.Descriptor（缓存）
     * 4. DynamicMessage.parseFrom() 解析二进制
     * 5. SchemaBackedMap.wrap() 包装为 schema 感知 Map
     */
    private fun parseProtobufPayload(payload: Any): Map<String, Any> {
        require(payload.typeUrl.isNotBlank()) { "Any.type_url is required" }

        val schemaName = payload.typeUrl.substringAfterLast('/')

        val schema = schemaRegistry.get(schemaName)
            ?: throw IllegalArgumentException("Schema not found in registry: $schemaName")
        require(schema.format == SchemaFormat.PROTOBUF) {
            "Schema [$schemaName] is not PROTOBUF format: ${schema.format}"
        }

        val descriptor = descriptorCache.computeIfAbsent(schemaName) {
            buildDescriptor(schema.parsed)
        }

        val message = DynamicMessage.parseFrom(descriptor, payload.value)

        val provider = providerRegistry?.getProvider(SchemaFormat.PROTOBUF)
        @Suppress("UNCHECKED_CAST")
        return SchemaBackedMap.wrap(message, provider) as Map<String, Any>
    }

    // ─── 响应构建 ─────────────────────────────────────────────────

    /**
     * 将工作流输出数据构建为 Any。
     *
     * 格式自动推断（不依赖 outputSchemaFormat 字段）：
     * - 从 outputSchema 内容结构自动判断是否为 protobuf（FileDescriptorProto）
     * - protobuf → DynamicMessage → Any（完全动态，无需编译期类）
     * - 其他 → google.protobuf.Struct（通用 key-value）
     *
     * 设计原则：Schema 内容本身是权威源，格式是内容的固有属性，无需外部标签。
     */
    private fun buildResponseAny(result: UnifiedResponse): Any {
        val data = result.data ?: return Any.getDefaultInstance()
        val schema = result.outputSchema

        if (schema != null && isProtobufSchema(schema)) {
            return buildProtobufResponse(data, schema)
        }

        // 降级：非 protobuf outputSchema 时，使用 Struct（通用 key-value）
        return buildStructResponse(data)
    }

    /**
     * 将 Map 数据序列化为 DynamicMessage → Any。
     *
     * 流程：
     * 1. 从 outputSchema 解析 FileDescriptorProto（SchemaRegistry 或 inline）
     * 2. 构建 Descriptor（缓存）
     * 3. 通过 JsonFormat 将数据 merge 到 DynamicMessage Builder
     * 4. 包装为 Any（type_url = package.messageName）
     */
    private fun buildProtobufResponse(data: kotlin.Any, outputSchema: kotlin.Any): Any {
        val fileDescProto = resolveOutputFileDescriptor(outputSchema)
        val messageName = resolveMessageName(fileDescProto)
        val typeUrl = "$TYPE_URL_PREFIX$messageName"

        val descriptor = descriptorCache.computeIfAbsent(typeUrl) {
            buildDescriptor(fileDescProto)
        }

        val json = JsonUtil.serialize(data)
        val builder = DynamicMessage.newBuilder(descriptor)
        JsonFormat.parser().merge(json, builder)
        val message = builder.build()

        return Any.newBuilder()
            .setTypeUrl(typeUrl)
            .setValue(message.toByteString())
            .build()
    }

    /**
     * 降级响应：将数据包装为 google.protobuf.Struct → Any。
     * 适用于未配置 protobuf outputSchema 的工作流。
     */
    private fun buildStructResponse(data: kotlin.Any): Any {
        val json = JsonUtil.serialize(data)
        val structBuilder = Struct.newBuilder()
        JsonFormat.parser().merge(json, structBuilder)
        val struct = structBuilder.build()

        return Any.newBuilder()
            .setTypeUrl("${TYPE_URL_PREFIX}google.protobuf.Struct")
            .setValue(struct.toByteString())
            .build()
    }

    /**
     * 从 Schema 内容自动推断是否为 protobuf 格式。
     *
     * 检测规则（各格式结构特征天然正交，不会误判）：
     * - FileDescriptorProto 实例 → protobuf（来自 SchemaRegistry.parsed）
     * - Map 包含 "messageType" 键 → protobuf（FileDescriptorProto JSON 表示）
     * - String 包含 "messageType" → protobuf（FileDescriptorProto JSON 文本）
     * - 其他 → 非 protobuf（JSON Schema / Avro 等）
     *
     * 扩展：新增格式时只需添加对应的检测方法，不改 enum、不改 DB。
     */
    private fun isProtobufSchema(schema: kotlin.Any): Boolean {
        return when (schema) {
            is DescriptorProtos.FileDescriptorProto -> true
            is Map<*, *> -> schema.containsKey("messageType")
            is String -> schema.contains("\"messageType\"")
            else -> false
        }
    }

    /**
     * 从 outputSchema 内容解析 FileDescriptorProto。
     *
     * 支持两种来源：
     * - SchemaRegistry 已注册的 Schema（parsed = FileDescriptorProto）
     * - 内联 Map/String（通过 JsonFormat 解析）
     */
    private fun resolveOutputFileDescriptor(outputSchema: kotlin.Any): DescriptorProtos.FileDescriptorProto {
        // 1. 已是 FileDescriptorProto（来自 SchemaRegistry.parsed）
        if (outputSchema is DescriptorProtos.FileDescriptorProto) {
            return outputSchema
        }

        // 2. Map 或 String（内联 schema），通过 JSON 解析
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

    /**
     * 从 FileDescriptorProto 提取消息全限定名。
     * 格式：package.MessageName（用于 Any.type_url）
     */
    private fun resolveMessageName(fileDescProto: DescriptorProtos.FileDescriptorProto): String {
        require(fileDescProto.messageTypeCount > 0) {
            "FileDescriptorProto contains no message type definitions"
        }
        val pkg = if (fileDescProto.hasPackage()) "${fileDescProto.`package`}." else ""
        return "$pkg${fileDescProto.getMessageType(0).name}"
    }

    // ─── Descriptor 构建 ──────────────────────────────────────────

    /**
     * 从 FileDescriptorProto 构建 Descriptors.Descriptor。
     * 取第一个 messageType 作为主 message 定义。
     */
    private fun buildDescriptor(parsed: kotlin.Any): Descriptors.Descriptor {
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

    // ─── 工具方法 ─────────────────────────────────────────────────

    /**
     * 从 gRPC Context 中提取 metadata
     * 由 GrpcMetadataInterceptor 从 HTTP/2 headers 注入
     */
    private fun extractMetadata(): Map<String, String> =
        GrpcMetadataInterceptor.METADATA_CONTEXT_KEY.get() ?: emptyMap()

    /**
     * 将异常转换为 gRPC StatusRuntimeException。
     *
     * - WorkflowException → INTERNAL，携带 errorCode 描述
     * - IllegalArgumentException → INVALID_ARGUMENT
     * - 其他 → INTERNAL
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
