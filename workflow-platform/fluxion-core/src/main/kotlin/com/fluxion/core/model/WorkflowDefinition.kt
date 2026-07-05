package com.fluxion.core.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fluxion.core.enums.Protocol
import com.fluxion.core.util.DagTopology

/**
 * 工作流定义 — 对应 wf_definition 表，序列化后存储 nodes_config
 */
data class WorkflowDefinition(
    var id: String = "",
    var name: String = "",
    /** 工作流类别：META（元工作流）/ BUSINESS */
    var category: String = "BUSINESS",
    /** 1=核心元工作流，禁止删除 */
    var isProtectedFlag: Int = 0,
    /** 适配器协议 */
    var protocol: Protocol = Protocol.HTTP,
    /** 路由标识（HTTP=path, Dubbo=serviceMethod, gRPC=method, Kafka=topic） */
    var method: String? = null,
    var path: String? = null,
    /** 协议扩展配置（Dubbo/gRPC/Kafka 特有字段） */
    var protocolConfig: Map<String, Any>? = null,
    /** 入参 JSON Schema（对应 wf_definition.input_schema） */
    var inputSchema: Any? = null,
    /**
     * 入参 Schema 格式标识（"json-schema" / "protobuf" / "avro"）。
     *
     * 为 null 时默认使用 JSON Schema（向后兼容）。
     * 对应 wf_definition.input_schema_format 列。
     */
    var inputSchemaFormat: String? = null,
    /** 出参 JSON Schema */
    var outputSchema: Any? = null,
    /**
     * 出参 Schema 格式标识（"json-schema" / "protobuf" / "avro"）。
     *
     * 为 null 时默认使用 JSON Schema（向后兼容）。
     */
    var outputSchemaFormat: String? = null,
    /** 节点列表（顺序执行 / DAG 执行） */
    var nodes: List<WorkflowNode> = emptyList(),
    /** 工作流级全局参数（所有节点可通过 nodeParams 引用） */
    var globalParams: Map<String, Any>? = null,
    /** 事务配置（null=不开启事务） */
    var transactionConfig: TransactionConfig? = null,
    /**
     * 工作流级装饰器列表（按顺序包裹整段工作流执行）。
     *
     * 与 [WorkflowNode.decorators] 相互独立：前者作用于工作流整体，后者作用于单个节点。
     * 示例：["workflow:transaction", "workflow:lock", "workflow:ratelimit"]
     */
    var workflowDecorators: List<String>? = null,
    /**
     * 工作流级装饰器参数（key=装饰器名，value=该装饰器的参数 Map）。
     *
     * 参数读取复用 [WorkflowNode.decoratorParams] 同一套扩展函数。
     */
    var workflowDecoratorParams: Map<String, Map<String, Any>>? = null,
    /** 1=使用 SagaExecutor 执行 */
    var sagaEnabled: Int = 0,
    /** 工作流状态：DRAFT / PUBLISHED / DEPRECATED */
    var status: String = "DRAFT",
    var version: Int = 0,
    var createdBy: String? = null,
    /** 作用域：PLATFORM / PRIVATE / MARKETPLACE */
    var scope: String = "PRIVATE",
    /** 所属应用分组（scope=PRIVATE 时有效） */
    var appGroup: String? = null,
    /**
     * 错误处理函数引用（可选）
     * 示例：errorHandlerRef = "wf:errorWrapper" → 管理后台配置的错误封装工作流
     */
    var errorHandlerRef: String? = null,
    /**
     * 触发器列表（定义工作流的启动方式，支持多种触发方式）
     */
    var triggers: List<WorkflowTrigger> = emptyList()
) {
    /**
     * 拓扑排序后的节点 ID 列表。
     *
     * 懒加载：每个 WorkflowDefinition 实例只计算一次；
     * 不参与 JSON 序列化，避免污染对外存储/传输格式。
     */
    @get:JsonIgnore
    val sortedNodeIds: List<String> by lazy { DagTopology.topologicalSort(nodes) ?: emptyList() }

    /** 是否开启 Saga */
    fun isSagaEnabled(): Boolean = sagaEnabled == 1
    /** 是否是受保护的元工作流 */
    fun isProtected(): Boolean = isProtectedFlag == 1
}
