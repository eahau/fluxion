package com.fluxion.admin.mapper

import com.fluxion.admin.entity.WfDefinition
import com.fluxion.admin.generated.model.WorkflowDefinition
import com.fluxion.core.util.JsonUtil
import org.springframework.stereotype.Service
import java.time.ZoneId

/**
 * Manual mapper between workflow-definition entities and the OpenAPI-generated
 * `WorkflowDefinition` DTO.
 *
 * JSON parsing, enum coercion and time conversions are delegated to `JsonMapperHelper`.
 */
@Service
class WorkflowMapper(
    private val jsonMapperHelper: JsonMapperHelper
) {

    /**
     * Project a persisted `WfDefinition` into the detail/list DTO.
     *
     * Decomposes the stored `dag_json`, decorator JSONs and legacy columns back into
     * strongly-typed DTO fields. The `publishTarget` field prefers the new dedicated
     * column but falls back to the deprecated `targetGroups` JSON for pre-migration rows.
     */
    fun toDto(entity: WfDefinition): WorkflowDefinition = WorkflowDefinition(
        name = entity.workflowName,
        category = jsonMapperHelper.categoryToEnum(entity.category),
        protocol = entity.protocol,
        nodes = jsonMapperHelper.dagJsonToNodes(entity.dagJson).toMutableList()
    ).apply {
        id = entity.id
        workflowId = entity.workflowId
        scope = jsonMapperHelper.scopeToEnum(entity.scope)
        appGroup = entity.appGroup
        sourceRef = entity.sourceRef
        method = entity.method
        path = entity.bindKey
        status = jsonMapperHelper.wfStatusToEnum(entity.status)
        inputSchema = jsonMapperHelper.jsonToMap(entity.inputSchema)?.toMutableMap()
        inputSchemaFormat = entity.inputSchemaFormat.takeIf { it.isNotBlank() }
        outputSchema = jsonMapperHelper.jsonToMap(entity.outputSchema)?.toMutableMap()
        outputSchemaFormat = entity.outputSchemaFormat.takeIf { it.isNotBlank() }
        transactionConfig = jsonMapperHelper.transactionModeToConfig(entity.transactionMode)?.toMutableMap()
        workflowDecorators = jsonMapperHelper.workflowDecoratorsJsonToList(entity.workflowDecorators)
        workflowDecoratorParams = jsonMapperHelper.workflowDecoratorParamsJsonToMap(entity.workflowDecoratorParams)
        createdAt = entity.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        updatedAt = entity.updatedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        publishTarget = jsonMapperHelper.stringToPublishTarget(entity.publishTarget)
            ?: jsonMapperHelper.fallbackPublishTarget(entity.targetGroups)
        version = entity.version
        isProtected = entity.isProtected
        isPublishedSet = entity.isPublishedSet
        setRefName = entity.setRefName
    }

    /**
     * Create a new `WfDefinition` entity from an incoming create DTO.
     */
    fun toEntity(dto: WorkflowDefinition): WfDefinition = WfDefinition().apply {
        workflowId = dto.workflowId ?: ""
        workflowName = dto.name ?: ""
        protocol = dto.protocol ?: "HTTP"
        method = dto.method
        bindKey = dto.path
        scope = jsonMapperHelper.scopeToString(dto.scope)
        appGroup = dto.appGroup
        status = jsonMapperHelper.wfStatusToString(dto.status)
        category = jsonMapperHelper.categoryToString(dto.category)
        inputSchema = jsonMapperHelper.mapToJson(dto.inputSchema)
        inputSchemaFormat = dto.inputSchemaFormat ?: "json-schema"
        outputSchema = jsonMapperHelper.mapToJson(dto.outputSchema)
        outputSchemaFormat = dto.outputSchemaFormat ?: "json-schema"
        transactionMode = jsonMapperHelper.transactionConfigToMode(dto.transactionConfig)
        workflowDecorators = jsonMapperHelper.workflowDecoratorsListToJson(dto.workflowDecorators)
        workflowDecoratorParams = jsonMapperHelper.workflowDecoratorParamsMapToJson(
            dto.workflowDecoratorParams?.mapValues { it.value.toMap() }
        )
        dagJson = jsonMapperHelper.nodesToDagJson(dto.nodes)
        publishTarget = jsonMapperHelper.publishTargetToString(dto.publishTarget)
        triggersConfig = dto.triggers?.takeIf { it.isNotEmpty() }?.let { JsonUtil.serialize(it) }
    }

    /**
     * Apply update DTO fields onto an existing managed entity.
     */
    fun updateEntity(dto: WorkflowDefinition, entity: WfDefinition) {
        entity.workflowId = dto.workflowId ?: entity.workflowId
        entity.workflowName = dto.name ?: entity.workflowName
        entity.protocol = dto.protocol ?: entity.protocol
        entity.method = dto.method ?: entity.method
        entity.bindKey = dto.path
        entity.scope = jsonMapperHelper.scopeToString(dto.scope)
        entity.appGroup = dto.appGroup
        entity.status = jsonMapperHelper.wfStatusToString(dto.status)
        entity.category = jsonMapperHelper.categoryToString(dto.category)
        entity.inputSchema = jsonMapperHelper.mapToJson(dto.inputSchema)
        entity.inputSchemaFormat = dto.inputSchemaFormat ?: entity.inputSchemaFormat
        entity.outputSchema = jsonMapperHelper.mapToJson(dto.outputSchema)
        entity.outputSchemaFormat = dto.outputSchemaFormat ?: entity.outputSchemaFormat
        entity.transactionMode = jsonMapperHelper.transactionConfigToMode(dto.transactionConfig)
        entity.workflowDecorators = jsonMapperHelper.workflowDecoratorsListToJson(dto.workflowDecorators)
        entity.workflowDecoratorParams = jsonMapperHelper.workflowDecoratorParamsMapToJson(
            dto.workflowDecoratorParams?.mapValues { it.value.toMap() }
        )
        entity.dagJson = jsonMapperHelper.nodesToDagJson(dto.nodes)
        entity.publishTarget = jsonMapperHelper.publishTargetToString(dto.publishTarget)
        entity.triggersConfig = dto.triggers?.takeIf { it.isNotEmpty() }?.let { JsonUtil.serialize(it) }
    }
}
