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
 * Function 实体与 DTO 之间的手写映射器
 *
 * 复杂转换（JSON、枚举、时间）通过 JsonMapperHelper 完成。
 */
@Service
class FunctionMapper(
    private val jsonMapperHelper: JsonMapperHelper
) {

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
            createdAt = entity.createdAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
            updatedAt = entity.updatedAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
        }
    }

    /**
     * 将 FunctionRegistry 中的 FunctionMeta 转换为 FunctionDefinition。
     * 仅用于列表/详情展示，因此 script、publishTarget、时间戳等字段留空。
     * Schema 可能是 JSON 字符串（内置函数）或已解析对象，统一解析为对象返回。
     */
    fun registryMetaToDto(meta: FunctionMeta): FunctionDefinition {
        val name = meta.name
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

    fun toEntity(dto: FunctionDefinition): WfFunction = WfFunction().apply {
        functionName = dto.name ?: ""
        functionType = resolveFunctionType(dto)
        scope = jsonMapperHelper.scopeToString(dto.scope)
        appGroup = dto.appGroup
        domain = jsonMapperHelper.functionConfigToDomain(dto.config)
        config = jsonMapperHelper.functionConfigToJsonString(jsonMapperHelper.buildFunctionConfig(dto))
    }

    fun updateEntity(dto: FunctionDefinition, entity: WfFunction) {
        entity.functionName = dto.name ?: entity.functionName
        entity.functionType = resolveFunctionType(dto)
        entity.scope = jsonMapperHelper.scopeToString(dto.scope)
        entity.appGroup = dto.appGroup
        entity.domain = jsonMapperHelper.functionConfigToDomain(dto.config)
        entity.config = jsonMapperHelper.functionConfigToJsonString(jsonMapperHelper.buildFunctionConfig(dto))
    }

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
     * 内置函数名 → 节点类型映射（与前端 BUILTIN_NODE_TYPE_MAP 保持一致）
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
