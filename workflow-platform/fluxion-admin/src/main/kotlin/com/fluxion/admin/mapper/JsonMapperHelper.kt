package com.fluxion.admin.mapper

import com.fluxion.core.enums.NodeType as CoreNodeType
import com.fluxion.core.model.WorkflowNode as CoreWorkflowNode
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.castOrNull
import com.fluxion.core.util.uncheckedCast
import com.fluxion.admin.entity.WfFunction
import org.slf4j.LoggerFactory
import com.fluxion.admin.generated.model.FunctionCategory
import com.fluxion.admin.generated.model.FunctionNodeType
import com.fluxion.admin.generated.model.FunctionStatus
import com.fluxion.admin.generated.model.DebugExecutionRecord
import com.fluxion.admin.generated.model.DebugStatus
import com.fluxion.admin.generated.model.FunctionDefinition
import com.fluxion.admin.generated.model.NodeTraceStatus
import com.fluxion.admin.generated.model.NodeType
import com.fluxion.admin.generated.model.PublishTarget
import com.fluxion.admin.generated.model.PublishType
import com.fluxion.admin.generated.model.WorkflowCategory
import com.fluxion.admin.generated.model.WorkflowNode
import com.fluxion.admin.generated.model.WorkflowScope
import com.fluxion.admin.generated.model.WorkflowStatus
import com.fluxion.core.enums.NodeStatus
import com.fluxion.core.value.NodeExecutionRecord
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * JSON / 枚举 / 复杂字段转换辅助类
 *
 * 以 Spring Bean 形式提供，供手写 Mapper 注入使用。
 */
@Component
class JsonMapperHelper {

    private val log = LoggerFactory.getLogger(javaClass)

    // ─── Function 相关 ───────────────────────────────────────────

    /**
     * 从实体读取统一配置 JSON。
     *
     * `wf_function.config` 已聚合 description、paramSchema、outputSchema、
     * scriptBody、className、publishTarget 等字段，新增配置项无需 DDL。
     */
    fun functionToConfig(fn: WfFunction): Map<String, Any>? =
        fn.config?.let { jsonToMap(it) }

    /**
     * 将 DTO 的独立字段（script / publishTarget）与 config Map 合并，
     * 生成待持久化的统一 config Map。独立字段优先级高于 config 中的同名 key。
     *
     * EXTERNAL 类型兼容旧版 `config.externalService` 嵌套结构：若存在则将其字段提升到顶层，
     * 以适配 Worker 侧 [com.fluxion.core.function.external.ExternalFunctionConfigParser]。
     */
    fun buildFunctionConfig(dto: FunctionDefinition): Map<String, Any>? {
        val config = dto.config?.toMutableMap() ?: mutableMapOf()
        dto.script?.takeIf { it.isNotBlank() }?.let { config["scriptBody"] = it }
        dto.publishTarget?.let { config["publishTarget"] = it }

        if (dto.category == FunctionCategory.EXTERNAL) {
            flattenExternalServiceConfig(config)
        }
        return config.takeIf { it.isNotEmpty() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun flattenExternalServiceConfig(config: MutableMap<String, Any>) {
        val externalService = config.remove("externalService") as? Map<String, Any> ?: return
        externalService.forEach { (key, value) ->
            config.putIfAbsent(key, value)
        }
    }

    /** 将统一 config Map 序列化为 JSON 字符串，供实体 `config` 字段存储。 */
    fun functionConfigToJsonString(config: Map<String, Any>?): String? =
        mapToJson(config)

    fun functionTypeToEngine(functionType: String): String? =
        if (functionType.startsWith("SCRIPT")) functionType.removePrefix("SCRIPT_").lowercase() else null

    fun functionTypeToCategory(functionType: String): FunctionCategory =
        when {
            functionType == "BUILTIN" -> FunctionCategory.BUILTIN
            functionType.startsWith("SCRIPT") -> FunctionCategory.SCRIPT
            functionType == "JAVA_CLASS" || functionType == "EXTERNAL" -> FunctionCategory.EXTERNAL
            else -> FunctionCategory.CUSTOM
        }

    fun categoryToFunctionType(category: FunctionCategory): String = category.value

    fun functionNodeType(): FunctionNodeType = FunctionNodeType.CUSTOM

    fun functionConfigToDescription(config: Map<String, Any>?): String? =
        config?.get("description")?.toString()

    fun functionConfigToClassName(config: Map<String, Any>?): String? =
        config?.get("className")?.toString()

    fun functionConfigToDomain(config: Map<String, Any>?): String? =
        config?.get("domain")?.toString()

    fun functionConfigToParamSchema(config: Map<String, Any>?): String? =
        config?.get("paramSchema")?.let { mapToJson(it.uncheckedCast<Map<String, Any>>()) }

    fun functionConfigToOutputSchema(config: Map<String, Any>?): String? =
        config?.get("outputSchema")?.let { mapToJson(it.uncheckedCast<Map<String, Any>>()) }

    fun functionConfigToScriptBody(config: Map<String, Any>?): String? =
        config?.get("scriptBody")?.toString()

    fun functionConfigToPublishTargetJson(config: Map<String, Any>?): String? =
        config?.get("publishTarget")?.let { mapToJson(it.uncheckedCast<Map<String, Any>>()) }

    // ─── Workflow 相关 ───────────────────────────────────────────

    fun dagJsonToNodes(dagJson: String?): List<WorkflowNode> {
        if (dagJson.isNullOrBlank()) return emptyList()
        return try {
            val dagData = JsonUtil.toMap(dagJson)
            val rawNodes = JsonUtil.convertValueOrNull<List<Map<String, Any>>>(dagData["nodes"]) ?: emptyList()
            rawNodes.map { nodeMap ->
                val rawType = nodeMap["type"]?.toString() ?: "CUSTOM"
                val functionRef = nodeMap["functionRef"]?.toString() ?: ""
                WorkflowNode(
                    id = nodeMap["id"]?.toString() ?: "",
                    name = nodeMap["name"]?.toString() ?: "",
                    type = safeNodeType(rawType, functionRef),
                    functionRef = functionRef
                ).apply {
                    timeoutMs = (nodeMap["timeoutMs"] as? Number)?.toInt() ?: 30000
                    params = nodeMap["params"].uncheckedCast<Map<String, Any>>()?.toMutableMap() ?: mutableMapOf()
                    decorators = nodeMap["decorators"].uncheckedCast<List<String>>()?.toMutableList() ?: mutableListOf()
                    decoratorParams = nodeMap["decoratorParams"].uncheckedCast<Map<String, Any>>()?.toMutableMap() ?: mutableMapOf()
                    dependsOn = nodeMap["dependsOn"].uncheckedCast<List<String>>()?.toMutableList() ?: mutableListOf()
                    remark = nodeMap["remark"]?.toString()
                    asyncExecution = nodeMap["asyncExecution"] as? Boolean ?: false
                }
            }
        } catch (ex: Exception) {
            log.warn("dagJsonToNodes parse failed, returning empty list. dagJson length=${dagJson?.length}, error: ${ex.message}")
            emptyList()
        }
    }

    /**
     * 安全解析 NodeType：兼容核心 NodeType（BUILTIN/EXTERNAL）、FunctionNodeType（DATA_QUERY 等）、以及未知值。
     * 解析顺序：
     *   1. 直接匹配 DTO NodeType 枚举（PARAM_VALIDATE / DATA_QUERY / CUSTOM 等 12 种）
     *   2. 若为核心 NodeType（BUILTIN/EXTERNAL），根据 functionRef 推导 DTO NodeType
     *   3. 兜底：CUSTOM
     */
    private fun safeNodeType(rawType: String, functionRef: String): NodeType {
        // 1) 直接匹配
        DTO_NODE_TYPE_VALUES[rawType]?.let { return it }
        // 2) 核心类型 → 根据 functionRef 推导
        return when {
            rawType == "BUILTIN" -> functionRefToNodeType(functionRef)
            rawType == "EXTERNAL" -> NodeType.CUSTOM
            rawType == "SCRIPT"   -> NodeType.SCRIPT
            else -> {
                log.warn("Unknown node type '{}', functionRef='{}', fallback to CUSTOM", rawType, functionRef)
                NodeType.CUSTOM
            }
        }
    }

    /**
     * 根据 functionRef 前缀推导 DTO NodeType（与前端 flowConverter 保持一致）
     */
    private fun functionRefToNodeType(functionRef: String): NodeType {
        if (functionRef.isBlank()) return NodeType.CUSTOM
        if (functionRef.startsWith("builtin:")) {
            return when (functionRef) {
                "builtin:paramValidate" -> NodeType.PARAM_VALIDATE
                "builtin:conditionBranch" -> NodeType.CONDITION_BRANCH
                "builtin:filter" -> NodeType.FILTER
                "builtin:dbExecute", "builtin:httpCall",
                "builtin:paginate", "builtin:redisCommand" -> NodeType.DATA_QUERY
                "builtin:loopAggregator" -> NodeType.PARALLEL
                "builtin:responseWrapper" -> NodeType.ASSEMBLE_RESPONSE
                "builtin:groovyScript" -> NodeType.SCRIPT
                "builtin:dynamicValidate" -> NodeType.DYNAMIC_VALIDATE
                "builtin:dataTransform" -> NodeType.DATA_TRANSFORM
                else -> NodeType.CUSTOM
            }
        }
        if (functionRef.startsWith("script:")) return NodeType.SCRIPT
        return NodeType.CUSTOM
    }

    fun nodesToDagJson(nodes: List<WorkflowNode>?): String {
        val list = nodes ?: emptyList()
        return JsonUtil.serialize(mapOf("nodes" to list))
    }

    /**
     * 将任意 type 值归一化为合法的 FunctionNodeType 字符串值。
     * 供外部在持久化前清洗 dag_json 使用。
     *
     * 归一化规则：
     *   1. 若 functionRef 不为空，始终从 functionRef 推导（因为 type 可能已被 Jackson 兑底为 CUSTOM）
     *   2. 若推导结果不是 CUSTOM，采用推导结果
     *   3. 若 rawType 已是合法 FunctionNodeType，保持不变
     *   4. 兑底：CUSTOM
     */
    fun normalizeToFunctionNodeType(rawType: String, functionRef: String): String {
        // functionRef 不为空时，始终优先从 functionRef 推导
        // （因为 Jackson 可能已将未知 type 兑底为 CUSTOM，丢失了原始值）
        if (functionRef.isNotBlank()) {
            val derived = functionRefToNodeType(functionRef)
            if (derived != NodeType.CUSTOM) return derived.value
        }
        // functionRef 为空或推导出 CUSTOM，检查 rawType 是否合法
        if (rawType in DTO_NODE_TYPE_VALUES) return rawType
        // 兑底
        return NodeType.CUSTOM.value
    }

    // ─── 核心 WorkflowNode 解析（兼容 FunctionNodeType → NodeType） ────

    /**
     * 核心 NodeType 枚举值集合，用于判断是否为合法的 NodeType
     */
    private val CORE_NODE_TYPE_VALUES = CoreNodeType.entries.map { it.name }.toSet()

    /**
     * DTO NodeType 枚举值快速查找表
     */
    private val DTO_NODE_TYPE_VALUES: Map<String, NodeType> = NodeType.entries.associateBy { it.value }

    /**
     * 将 DAG JSON 中的节点列表解析为核心 WorkflowNode 列表。
     * 兼容前端发送的 FunctionNodeType（如 DATA_QUERY）：自动根据 functionRef 前缀推导核心 NodeType。
     */
    fun parseDagNodes(dagJson: String): List<CoreWorkflowNode> {
        val dagData = JsonUtil.toMap(dagJson)
        val nodesJson = JsonUtil.convertValueOrNull<List<Map<String, Any>>>(dagData["nodes"]) ?: emptyList()
        return parseNodesFromMap(nodesJson)
    }

    /**
     * 将原始节点 Map 列表转换为核心 WorkflowNode 列表，
     * 对 type 字段做 FunctionNodeType → NodeType 容错转换。
     */
    fun parseNodesFromMap(nodesJson: List<Map<String, Any>>): List<CoreWorkflowNode> {
        return nodesJson.map { nodeMap ->
            val normalized = normalizeNodeType(nodeMap)
            JsonUtil.convertValue(normalized, CoreWorkflowNode::class.java)
        }
    }

    /**
     * 归一化节点 Map 的 type 字段：
     * 若 type 不是合法的核心 NodeType 值，根据 functionRef 前缀推导。
     */
    private fun normalizeNodeType(nodeMap: Map<String, Any>): Map<String, Any> {
        val type = nodeMap["type"]?.toString() ?: "CUSTOM"
        if (type in CORE_NODE_TYPE_VALUES) return nodeMap

        val functionRef = nodeMap["functionRef"]?.toString() ?: ""
        val resolvedType = when {
            functionRef.startsWith("builtin:") -> "BUILTIN"
            functionRef.startsWith("script:") -> "SCRIPT"
            functionRef.startsWith("external:") -> "EXTERNAL"
            else -> "CUSTOM"
        }
        return nodeMap + ("type" to resolvedType)
    }

    fun jsonToMap(json: String?): Map<String, Any>? {
        if (json.isNullOrBlank()) return null
        return try {
            JsonUtil.toMap(json)
        } catch (_: Exception) {
            null
        }
    }

    fun mapToJson(map: Map<String, Any>?): String? {
        if (map == null) return null
        return JsonUtil.serialize(map)
    }

    /**
     * 将可能是 JSON 字符串的 Schema 解析为对象；
     * 若已是对象/Map 则原样返回，解析失败也回退原值。
     */
    fun parseSchema(schema: Any?): Any? {
        if (schema is String) {
            return try { JsonUtil.deserialize(schema, Any::class.java) }
            catch (_: Exception) { schema }
        }
        return schema
    }

    fun wfStatusToEnum(status: String): WorkflowStatus =
        when (status) {
            "ACTIVE" -> WorkflowStatus.ACTIVE
            "DEPRECATED" -> WorkflowStatus.DEPRECATED
            else -> WorkflowStatus.DRAFT
        }

    fun wfStatusToString(status: WorkflowStatus?): String =
        when (status) {
            WorkflowStatus.ACTIVE -> "ACTIVE"
            WorkflowStatus.DEPRECATED -> "DEPRECATED"
            else -> "DRAFT"
        }

    fun categoryToEnum(category: String?): WorkflowCategory =
        category?.let { WorkflowCategory.forValue(it) } ?: WorkflowCategory.BUSINESS

    fun categoryToString(category: WorkflowCategory?): String =
        category?.value ?: WorkflowCategory.BUSINESS.value

    fun scopeToEnum(scope: String?): WorkflowScope =
        scope?.let { WorkflowScope.forValue(it) } ?: WorkflowScope.PRIVATE

    fun scopeToString(scope: WorkflowScope?): String =
        scope?.value ?: WorkflowScope.PRIVATE.value

    /**
     * 将数据库 `transaction_mode` 字段转换为 DTO 的 transactionConfig Map。
     *
     * 兼容旧版 "SAGA" 模式：返回 {mode: "SAGA"}。
     * 新版事务配置直接以 transactionConfig JSON 存储，不经过 transaction_mode 字段。
     */
    fun transactionModeToConfig(transactionMode: String?): Map<String, Any>? =
        if (transactionMode == "SAGA") mapOf("mode" to "SAGA") else null

    /**
     * 从 DTO transactionConfig Map 中提取事务模式字符串（用于旧版 transaction_mode 字段）。
     */
    fun transactionConfigToMode(config: Map<String, Any>?): String =
        config?.get("mode")?.toString() ?: "NONE"

    // ─── 工作流级装饰器相关 ───────────────────────────────────────

    fun workflowDecoratorsJsonToList(json: String?): MutableList<String>? {
        if (json.isNullOrBlank()) return null
        return try {
            JsonUtil.deserializeList(json, String::class.java).toMutableList()
        } catch (ex: Exception) {
            log.warn("Failed to parse workflowDecorators JSON: $json, error: ${ex.message}")
            null
        }
    }

    fun workflowDecoratorsListToJson(list: List<String>?): String? {
        if (list.isNullOrEmpty()) return null
        return JsonUtil.serialize(list)
    }

    @Suppress("UNCHECKED_CAST")
    fun workflowDecoratorParamsJsonToMap(json: String?): MutableMap<String, MutableMap<String, Any>>? {
        if (json.isNullOrBlank()) return null
        return try {
            JsonUtil.deserialize(
                json,
                object : com.fasterxml.jackson.core.type.TypeReference<MutableMap<String, MutableMap<String, Any>>>() {}
            )
        } catch (ex: Exception) {
            log.warn("Failed to parse workflowDecoratorParams JSON: $json, error: ${ex.message}")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun workflowDecoratorParamsMapToJson(map: Map<String, Map<String, Any>>?): String? {
        if (map.isNullOrEmpty()) return null
        return JsonUtil.serialize(map)
    }

    // ─── Debug 相关 ──────────────────────────────────────────────

    fun nodeStatusToTraceStatus(status: NodeStatus): NodeTraceStatus =
        when (status) {
            NodeStatus.SUCCESS -> NodeTraceStatus.SUCCESS
            NodeStatus.SKIPPED -> NodeTraceStatus.SKIPPED
            NodeStatus.FALLBACK -> NodeTraceStatus.FALLBACK
            else -> NodeTraceStatus.FAILED
        }

    fun booleanToDebugStatus(success: Boolean): DebugStatus =
        if (success) DebugStatus.COMPLETED else DebugStatus.FAILED

    fun traceToDebugStatus(trace: List<NodeExecutionRecord>): DebugStatus =
        if (trace.any { it.status == NodeStatus.FAILED }) DebugStatus.FAILED else DebugStatus.COMPLETED

    fun booleanToDebugRecordStatus(success: Boolean): DebugExecutionRecord.Status =
        if (success) DebugExecutionRecord.Status.SUCCESS else DebugExecutionRecord.Status.FAILED

    fun nodeRecordToErrorMessage(record: NodeExecutionRecord): String? = record.error?.message

    fun traceToTotalDuration(trace: List<NodeExecutionRecord>): Int =
        trace.sumOf { it.durationMs }.toInt()

    fun traceHasFailure(trace: List<NodeExecutionRecord>): Boolean =
        trace.any { it.status == NodeStatus.FAILED }

    fun localDateTimeToOffsetDateTime(dt: LocalDateTime?): OffsetDateTime? =
        dt?.atZone(ZoneId.systemDefault())?.toOffsetDateTime()

    fun offsetDateTimeToLocalDateTime(dt: OffsetDateTime?): LocalDateTime? =
        dt?.toLocalDateTime()

    fun localDateTimeToTimestamp(dt: LocalDateTime?): Long? =
        dt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    fun anyToStringMap(value: Any?): Map<String, Any>? = value.uncheckedCast<Map<String, Any>>()

    fun longToInt(value: Long): Int = value.toInt()

    // ─── PublishTarget 相关 ─────────────────────────────────────

    fun publishTargetToString(target: PublishTarget?): String? {
        if (target == null) return null
        return try {
            JsonUtil.serialize(target)
        } catch (_: Exception) {
            null
        }
    }

    fun stringToPublishTarget(json: String?): PublishTarget? {
        if (json.isNullOrBlank()) return null
        return try {
            JsonUtil.deserialize(json, PublishTarget::class.java)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 将 OpenAPI 生成的 PublishTarget 转换为 SPI 层的 PublishTarget
     */
    fun toSpiPublishTarget(target: PublishTarget?): com.fluxion.adapter.spi.registry.PublishTarget {
        if (target == null || target.type == null || target.type == PublishType.ALL) {
            return com.fluxion.adapter.spi.registry.PublishTarget.all()
        }
        return when (target.type) {
            PublishType.APP_GROUP -> com.fluxion.adapter.spi.registry.PublishTarget.ofGroups(
                target.groups?.filterNotNull() ?: emptyList()
            )
            PublishType.INSTANCES -> com.fluxion.adapter.spi.registry.PublishTarget.ofInstances(
                target.instanceIds?.filterNotNull() ?: emptyList()
            )
            else -> com.fluxion.adapter.spi.registry.PublishTarget.all()
        }
    }

    /**
     * 兼容旧版 targetGroups JSON：若 publishTarget 为空，尝试从 targetGroups 反推出 APP_GROUP
     */
    fun fallbackPublishTarget(targetGroupsJson: String?): PublishTarget? {
        if (targetGroupsJson.isNullOrBlank()) return null
        return try {
            val groups = JsonUtil.deserializeList(targetGroupsJson, String::class.java)
            if (groups.isEmpty()) null else PublishTarget().apply {
                type = PublishType.APP_GROUP
                this.groups = groups.toMutableList()
            }
        } catch (_: Exception) {
            null
        }
    }
}
