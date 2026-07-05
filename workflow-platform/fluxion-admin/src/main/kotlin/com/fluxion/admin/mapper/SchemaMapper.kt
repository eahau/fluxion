package com.fluxion.admin.mapper

import com.fluxion.admin.entity.WfSchema
import com.fluxion.admin.entity.WfSchemaSummary
import com.fluxion.admin.generated.model.SchemaDefinition
import org.springframework.stereotype.Service
import java.time.ZoneId

/**
 * Schema 实体与 DTO 之间的手写映射器
 * schemaType 以逗号分隔字符串存储，支持多类型标签（如 "INPUT,OUTPUT"）
 */
@Service
class SchemaMapper {

    /** 详情页用的完整映射：包含 schemaJson（可能几十 KB 大 TEXT） */
    fun toDto(entity: WfSchema): SchemaDefinition = SchemaDefinition(
        schemaName = entity.schemaName,
        schemaType = entity.schemaType,
        schemaJson = entity.schemaJson
    ).apply {
        schemaFormat = entity.schemaFormat
        description = entity.description
        frozen = entity.frozen
        scope = entity.scope
        appGroup = entity.appGroup
        createdAt = entity.createdAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
        updatedAt = entity.updatedAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
    }

    /** 列表页用的轻量映射：不包含 schemaJson 大字段，减少 99% 的响应 payload */
    fun toDto(summary: WfSchemaSummary): SchemaDefinition = SchemaDefinition(
        schemaName = summary.schemaName,
        schemaType = summary.schemaType,
        schemaJson = ""
    ).apply {
        id = summary.id
        schemaFormat = summary.schemaFormat
        description = summary.description
        frozen = summary.frozen
        scope = summary.scope
        appGroup = summary.appGroup
        createdAt = summary.createdAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
        updatedAt = summary.updatedAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
    }

    fun toEntity(dto: SchemaDefinition): WfSchema = WfSchema().apply {
        schemaName = dto.schemaName ?: ""
        schemaType = dto.schemaType ?: "INPUT"
        schemaFormat = dto.schemaFormat ?: "json-schema"
        schemaJson = dto.schemaJson ?: ""
        description = dto.description
        frozen = dto.frozen ?: false
        scope = dto.scope ?: "PLATFORM"
        appGroup = dto.appGroup
        dto.createdAt?.let { createdAt = it.toLocalDateTime() }
        dto.updatedAt?.let { updatedAt = it.toLocalDateTime() }
    }
}
