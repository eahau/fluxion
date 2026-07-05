package com.fluxion.adapter.spi

import com.fluxion.core.value.EngineResult
import com.fluxion.core.value.NodeExecutionRecord

/**
 * 统一请求模型（协议无关）
 * 适配器层将各种协议的原生请求转换为此类型，再交给 WorkflowRouter
 */
data class UnifiedRequest(
    /** 协议标识：HTTP / DUBBO / GRPC / KAFKA / INTERNAL */
    val protocol: String,
    /** 工作流 ID（由 WorkflowRouter 根据 protocol/path/method 路由解析，路由模式下可为 null） */
    val workflowId: String?,
    /** 请求头（HTTP header / Dubbo attachment / gRPC metadata） */
    val headers: Map<String, String>,
    /** 请求参数（已解析的 Map，不同协议的参数来源不同） */
    val params: Map<String, Any>,
    /** 原始请求体（调试用，生产环境可为 null） */
    val rawBody: String? = null,
    /**
     * Schema 格式标识（"json-schema" / "protobuf" / "avro"）。
     *
     * 由适配器从请求头（如 `X-Schema-Format`）或协议 metadata 中提取。
     * 为 null 时回退到 WorkflowDefinition.inputSchemaFormat 或默认 JSON Schema。
     */
    val schemaFormat: String? = null,
    /** Schema 名称（可选，用于从 SchemaRegistry 查找完整 Schema 定义） */
    val schemaName: String? = null,
    /** Schema 版本号（可选，null 表示最新版本） */
    val schemaVersion: Long? = null
) {
    companion object {
        /** Schema 格式请求头（HTTP header / gRPC metadata / Kafka header / Dubbo attachment） */
        const val HEADER_SCHEMA_FORMAT = "X-Schema-Format"
        /** Schema 名称请求头 */
        const val HEADER_SCHEMA_NAME = "X-Schema-Name"
        /** Schema 版本请求头 */
        const val HEADER_SCHEMA_VERSION = "X-Schema-Version"

        /** 内部直接调用工厂（跳过路由解析） */
        @JvmStatic
        fun internal(workflowId: String, params: Map<String, Any>) =
            UnifiedRequest("INTERNAL", workflowId, emptyMap(), params)

        /**
         * 从协议无关的 headers Map 中提取 Schema 元数据，构建 [UnifiedRequest]。
         *
         * 各适配器调用此方法，避免每个适配器重复解析 schema header 逻辑。
         *
         * @param protocol   协议标识
         * @param workflowId 工作流 ID（路由模式下可为 null）
         * @param headers    请求头
         * @param params     已解析的参数
         * @param rawBody    原始请求体
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
 * 统一响应模型（协议无关）
 * WorkflowRouter 执行完毕后返回此类型，适配器层再转换为协议原生响应
 *
 * 与 [UnifiedRequest] 对称，使适配器层无需依赖 fluxion-core 的 [EngineResult]
 */
data class UnifiedResponse(
    /** 是否执行成功 */
    val success: Boolean,
    /** 响应数据（工作流最终输出） */
    val data: Any? = null,
    /** 执行 ID（用于链路追踪） */
    val executionId: String? = null,
    /** 错误信息（失败时填充） */
    val errorMsg: String? = null,
    /** 执行轨迹（调试 / gRPC 流式场景使用） */
    val trace: List<NodeExecutionRecord> = emptyList(),
    /**
     * 输出 Schema 内容（由路由器从 WorkflowDefinition.outputSchema 透传）。
     *
     * 适配器层据此选择响应序列化方式：
     * - gRPC：FileDescriptorProto → DynamicMessage → Any
     * - HTTP/其他适配器忽略此字段
     */
    val outputSchema: Any? = null,
    /**
     * 输出 Schema 格式（"json-schema" / "protobuf" / "avro"）。
     * 与 [outputSchema] 配合使用，null 时默认 JSON Schema。
     */
    val outputSchemaFormat: String? = null
) {
    companion object {
        /** 从引擎结果转换 */
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
 * 工作流路由器 — 适配器层的核心协调者
 */
interface WorkflowRouter {
    /** 执行工作流（阻塞式，供非协程调用方使用） */
    fun execute(request: UnifiedRequest): UnifiedResponse

    /**
     * 执行工作流（协程挂起版，不阻塞调用线程）。
     *
     * 默认实现委托给阻塞式 [execute]，支持协程的实现应覆写此方法。
     */
    suspend fun executeSuspend(request: UnifiedRequest): UnifiedResponse = execute(request)
}
