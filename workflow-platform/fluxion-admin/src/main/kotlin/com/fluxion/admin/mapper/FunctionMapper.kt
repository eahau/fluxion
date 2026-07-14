package com.fluxion.admin.mapper

import com.fluxion.admin.entity.WfFunction
import com.fluxion.admin.generated.model.FunctionCategory
import com.fluxion.admin.generated.model.FunctionDefinition
import com.fluxion.admin.generated.model.FunctionNodeType
import com.fluxion.admin.generated.model.FunctionStatus
import com.fluxion.admin.generated.model.WorkflowScope
import com.fluxion.core.value.FunctionMeta
import org.springframework.stereotype.Service

import java.time.ZoneId

/**
 * Manual mapper between `WfFunction` entity and OpenAPI-generated `FunctionDefinition` DTO.
 *
 * JSON parsing, enum coercion and time conversions are delegated to `JsonMapperHelper`.
 * Also handles projection of runtime `FunctionMeta` registry entries into read-only DTOs
 * for listing endpoints.
 */
@Service
class FunctionMapper(
    private val jsonMapperHelper: JsonMapperHelper
) {

    /**
     * Full entity → DTO projection used for detail and list pages.
     *
     * Pulls denormalized fields (scriptBody, publishTarget, schemas) out of the unified
     * `config` JSON blob so the frontend can edit them as dedicated form controls.
     */
    fun toDto(entity: WfFunction): FunctionDefinition {
        val category = jsonMapperHelper.functionTypeToCategory(entity.functionType)
        val nodeType = resolveBuiltinNodeType(entity.functionName, category)
        val config = jsonMapperHelper.functionToConfig(entity) ?: emptyMap()
        return FunctionDefinition(
            name = entity.functionName,
            category = category,
            nodeType = nodeType,
            status = FunctionStatus.ACTIVE
        ).apply {
            id = entity.functionName
            scope = jsonMapperHelper.scopeToEnum(entity.scope)
            appGroup = entity.appGroup
            sourceRef = entity.sourceRef
            engine = jsonMapperHelper.functionTypeToEngine(entity.functionType)
            script = jsonMapperHelper.functionConfigToScriptBody(config)
            this.config = config.toMutableMap()
            publishTarget = jsonMapperHelper.stringToPublishTarget(
                jsonMapperHelper.functionConfigToPublishTargetJson(config)
            )
            createdAt = entity.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            updatedAt = entity.updatedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }

    /**
     * Convert a live `FunctionMeta` entry from the runtime registry into a list DTO.
     *
     * Used by the function browser for built-in/script/external registrations that are
     * not persisted as admin entities. Script, publishTarget and timestamp fields are
     * intentionally omitted because registry entries carry no editable state.
     *
     * The `inputSchema`/`outputSchema` fields may arrive either as raw JSON strings or
     * already-parsed Maps; both branches are normalized to object form for the UI.
     */
    fun registryMetaToDto(meta: FunctionMeta): FunctionDefinition {
        val name = meta.functionName
        val category = when {
            name.startsWith("builtin:") -> FunctionCategory.BUILTIN
            name.startsWith("script:") -> FunctionCategory.SCRIPT
            name.startsWith("external:") -> FunctionCategory.EXTERNAL
            else -> FunctionCategory.CUSTOM
        }
        val nodeType = resolveBuiltinNodeType(name, category)
        return FunctionDefinition(
            name = name,
            category = category,
            nodeType = nodeType,
            status = FunctionStatus.ACTIVE
        ).apply {
            this.id = name
            this.scope = WorkflowScope.PLATFORM
            this.config = mutableMapOf(
                "description" to (meta.description ?: ""),
                "paramSchema" to (jsonMapperHelper.parseSchema(meta.inputSchema) ?: emptyMap<String, Any>()),
                "outputSchema" to (jsonMapperHelper.parseSchema(meta.outputSchema) ?: emptyMap<String, Any>())
            )
            meta.domain?.let { this.config?.put("domain", it) }
            meta.descriptions?.let { this.config?.put("descriptions", it) }
        }
    }

    /**
     * Create a new `WfFunction` entity from an incoming create DTO.
     */
    fun toEntity(dto: FunctionDefinition): WfFunction = WfFunction().apply {
        functionName = dto.name ?: ""
        functionType = resolveFunctionType(dto)
        scope = jsonMapperHelper.scopeToString(dto.scope)
        appGroup = dto.appGroup
        domain = jsonMapperHelper.functionConfigToDomain(dto.config)
        config = jsonMapperHelper.functionConfigToJsonString(jsonMapperHelper.buildFunctionConfig(dto))
    }

    /**
     * Apply update DTO fields onto an existing managed entity.
     */
    fun updateEntity(dto: FunctionDefinition, entity: WfFunction) {
        entity.functionName = dto.name ?: entity.functionName
        entity.functionType = resolveFunctionType(dto)
        entity.scope = jsonMapperHelper.scopeToString(dto.scope)
        entity.appGroup = dto.appGroup
        entity.domain = jsonMapperHelper.functionConfigToDomain(dto.config)
        entity.config = jsonMapperHelper.functionConfigToJsonString(jsonMapperHelper.buildFunctionConfig(dto))
    }

    /**
     * Derive the persisted `functionType` code from DTO category + engine hint.
     */
    private fun resolveFunctionType(dto: FunctionDefinition): String {
        return when (dto.category) {
            FunctionCategory.SCRIPT -> {
                val engine = dto.engine?.trim()?.lowercase()
                when (engine) {
                    "js", "javascript" -> "SCRIPT_JS"
                    else -> "SCRIPT_GROOVY"
                }
            }
            FunctionCategory.EXTERNAL -> "EXTERNAL"
            FunctionCategory.BUILTIN -> "BUILTIN"
            FunctionCategory.CUSTOM -> "CUSTOM"
            else -> dto.category?.value ?: "CUSTOM"
        }
    }

    /**
     * Map built-in function prefix → DTO node type.
     *
     * Mirrors the frontend `BUILTIN_NODE_TYPE_MAP` so the visual editor renders the
     * correct node icon/decorations regardless of which side assigns the type first.
     */
    private fun resolveBuiltinNodeType(name: String?, category: FunctionCategory?): FunctionNodeType {
        if (category != FunctionCategory.BUILTIN) {
            return when (category) {
                FunctionCategory.SCRIPT -> FunctionNodeType.SCRIPT
                else -> FunctionNodeType.CUSTOM
            }
        }
        return when (name) {
            "builtin:paramValidate" -> FunctionNodeType.PARAM_VALIDATE
            "builtin:conditionBranch" -> FunctionNodeType.CONDITION_BRANCH
            "builtin:filter" -> FunctionNodeType.FILTER
            "builtin:dbExecute", "builtin:httpCall",
            "builtin:paginate", "builtin:redisCommand" -> FunctionNodeType.DATA_QUERY
            "builtin:loopAggregator" -> FunctionNodeType.PARALLEL
            "builtin:responseWrapper" -> FunctionNodeType.ASSEMBLE_RESPONSE
            "builtin:groovyScript" -> FunctionNodeType.SCRIPT
            else -> FunctionNodeType.CUSTOM
        }
    }
}
