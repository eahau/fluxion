package com.fluxion.admin.mapper

import com.fluxion.core.enums.NodeType as CoreNodeType
import com.fluxion.core.model.WorkflowNode as CoreWorkflowNode
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.castOrNull
import com.fluxion.core.util.uncheckedCast
import com.fluxion.admin.entity.WfFunction
import org.slf4j.*
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
 * Helper bean encapsulating JSON parsing, enum coercion and complex-field conversions
 * shared across manual mapper implementations in the admin module.
 *
 * Registered as a Spring `@Component` so it can be constructor-injected wherever
 * DTO ↔ entity conversions are needed.
 */
@Component
class JsonMapperHelper {

    private val log = LoggerFactory.getLogger(javaClass)

    // === Function-related conversions ===========================================================

    /**
     * Parse the unified `config` JSON stored on `wf_function.config`.
     *
     * This single JSON column aggregates description, paramSchema, outputSchema,
     * scriptBody, className, publishTarget and any future extension fields, so new
     * configuration items never require a DDL migration.
     */
    fun functionToConfig(fn: WfFunction): Map<String, Any>? =
        fn.config?.let { jsonToMap(it) }

    /**
     * Merge DTO top-level fields (script / publishTarget) into the `config` Map,
     * producing the unified config snapshot that will be persisted.
     *
     * DTO top-level fields take precedence over any same-named keys already inside
     * `dto.config`.
     *
     * For EXTERNAL-category functions the legacy nested `config.externalService`
     * envelope is flattened so workers can parse it via
     * `com.fluxion.core.function.external.ExternalFunctionConfigParser`.
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

    /** Serialize the unified config Map back into a JSON string for entity storage. */
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

    // === Workflow-related conversions ===========================================================

    /**
     * Parse the `dag_json` TEXT column into a list of editor `WorkflowNode` DTOs.
     *
     * Malformed JSON is swallowed with a warning and the caller receives an empty
     * list so that detail pages still render (albeit without graph data).
     */
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
            log.warn { "dagJsonToNodes parse failed, returning empty list. dagJson length=${dagJson.length}, error: ${ex.message}" }
            emptyList()
        }
    }

    /**
     * Safely resolve a raw `type` string into the DTO `NodeType` enum.
     *
     * Resolution order:
     *   1. Direct match against the 12 DTO `NodeType` values (PARAM_VALIDATE, DATA_QUERY, ...).
     *   2. If the value is a core engine `NodeType` (BUILTIN / EXTERNAL / SCRIPT), derive the
     *      DTO-side value from `functionRef`.
     *   3. Fall back to `CUSTOM` and warn.
     */
    private fun safeNodeType(rawType: String, functionRef: String): NodeType {
        DTO_NODE_TYPE_VALUES[rawType]?.let { return it }
        return when {
            rawType == "BUILTIN" -> functionRefToNodeType(functionRef)
            rawType == "EXTERNAL" -> NodeType.CUSTOM
            rawType == "SCRIPT"   -> NodeType.SCRIPT
            else -> {
                log.warn { "Unknown node type '$rawType', functionRef='$functionRef', fallback to CUSTOM" }
                NodeType.CUSTOM
            }
        }
    }

    /**
     * Derive DTO `NodeType` from a `functionRef` prefix.
     *
     * Kept in sync with the frontend `flowConverter` heuristic so the editor highlights
     * nodes consistently regardless of which side authored the DAG.
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
     * Normalize a user-provided node `type` + `functionRef` pair to a valid DTO
     * `FunctionNodeType`-compatible string before writing the DAG back to storage.
     *
     * Rules:
     *   1. If `functionRef` is non-blank it always wins (Jackson may have already
     *      collapsed an unknown enum to CUSTOM, destroying the original value).
     *   2. If the derived value is not CUSTOM, use it.
     *   3. If `rawType` is still a valid `NodeType` string, keep it.
     *   4. Otherwise fall back to CUSTOM.
     */
    fun normalizeToFunctionNodeType(rawType: String, functionRef: String): String {
        if (functionRef.isNotBlank()) {
            val derived = functionRefToNodeType(functionRef)
            if (derived != NodeType.CUSTOM) return derived.value
        }
        if (rawType in DTO_NODE_TYPE_VALUES) return rawType
        return NodeType.CUSTOM.value
    }

    // === Core WorkflowNode parsing (with FunctionNodeType → NodeType compatibility) ============

    /** Set of legal core engine `NodeType` enum names. */
    private val CORE_NODE_TYPE_VALUES = CoreNodeType.entries.map { it.name }.toSet()

    /** Fast lookup from DTO NodeType value string → enum instance. */
    private val DTO_NODE_TYPE_VALUES: Map<String, NodeType> = NodeType.entries.associateBy { it.value }

    /**
     * Parse DAG JSON nodes into core-engine `CoreWorkflowNode` objects.
     *
     * Frontend-generated DAGs use `FunctionNodeType` values (DATA_QUERY, etc.); these are
     * automatically normalized back to the four core NodeType categories based on the
     * `functionRef` prefix so the DAG executor sees a well-typed graph.
     */
    fun parseDagNodes(dagJson: String): List<CoreWorkflowNode> {
        val dagData = JsonUtil.toMap(dagJson)
        val nodesJson = JsonUtil.convertValueOrNull<List<Map<String, Any>>>(dagData["nodes"]) ?: emptyList()
        return parseNodesFromMap(nodesJson)
    }

    /**
     * Convert raw node Map entries to core `CoreWorkflowNode` instances, repairing the
     * `type` field along the way.
     */
    fun parseNodesFromMap(nodesJson: List<Map<String, Any>>): List<CoreWorkflowNode> {
        return nodesJson.map { nodeMap ->
            val normalized = normalizeNodeType(nodeMap)
            JsonUtil.convertValue(normalized, CoreWorkflowNode::class.java)
        }
    }

    /**
     * Fix up the `type` entry of a raw node Map so it is a valid core-engine `NodeType`.
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
     * Parse a schema payload that may be either a raw JSON string or an already-deserialized
     * Map. Parse errors fall back to returning the input unchanged.
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
     * Convert the legacy `transaction_mode` DB column into the newer `transactionConfig`
     * Map returned to callers.
     *
     * Only the legacy SAGA mode is translated to `{mode: "SAGA"}`. Newer transaction
     * configuration is stored directly in `transactionConfig` JSON and bypasses this
     * column entirely.
     */
    fun transactionModeToConfig(transactionMode: String?): Map<String, Any>? =
        if (transactionMode == "SAGA") mapOf("mode" to "SAGA") else null

    /** Extract the mode string (for the legacy column) from the new config Map. */
    fun transactionConfigToMode(config: Map<String, Any>?): String =
        config?.get("mode")?.toString() ?: "NONE"

    // === Workflow-level decorator conversions ===================================================

    fun workflowDecoratorsJsonToList(json: String?): MutableList<String>? {
        if (json.isNullOrBlank()) return null
        return try {
            JsonUtil.deserializeList(json, String::class.java).toMutableList()
        } catch (ex: Exception) {
            log.warn { "Failed to parse workflowDecorators JSON: $json, error: ${ex.message}" }
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
            log.warn { "Failed to parse workflowDecoratorParams JSON: $json, error: ${ex.message}" }
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun workflowDecoratorParamsMapToJson(map: Map<String, Map<String, Any>>?): String? {
        if (map.isNullOrEmpty()) return null
        return JsonUtil.serialize(map)
    }

    // === Debug-related conversions ==============================================================

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

    // === PublishTarget conversions ==============================================================

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
     * Translate the OpenAPI-generated `PublishTarget` DTO into its SPI-layer counterpart
     * used by the registry push mechanism.
     */
    fun toSpiPublishTarget(target: PublishTarget?): com.fluxion.config.core.PublishTarget {
        if (target == null || target.type == null || target.type == PublishType.ALL) {
            return com.fluxion.config.core.PublishTarget.all()
        }
        return when (target.type) {
            PublishType.APP_GROUP -> com.fluxion.config.core.PublishTarget.ofGroups(
                target.groups?.filterNotNull() ?: emptyList()
            )
            PublishType.INSTANCES -> com.fluxion.config.core.PublishTarget.ofInstances(
                target.instanceIds?.filterNotNull() ?: emptyList()
            )
            else -> com.fluxion.config.core.PublishTarget.all()
        }
    }

    /**
     * Legacy compatibility shim: if `publishTarget` is empty, try to reconstruct an
     * APP_GROUP target from the deprecated `targetGroups` JSON column.
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
