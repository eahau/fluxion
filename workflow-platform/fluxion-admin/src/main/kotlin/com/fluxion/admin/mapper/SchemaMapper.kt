package com.fluxion.admin.mapper

import com.fluxion.admin.entity.WfSchema
import com.fluxion.admin.entity.WfSchemaSummary
import com.fluxion.admin.generated.model.SchemaDefinition
import org.springframework.stereotype.Service
import java.time.ZoneId

/**
 * Manual mapper between schema entities and the OpenAPI-generated `SchemaDefinition` DTO.
 *
 * `schemaType` is stored as a comma-separated tag string (e.g. `"INPUT,OUTPUT"`) so a
 * single schema can serve multiple roles without introducing a join table.
 */
@Service
class SchemaMapper {

    /**
     * Full entity → DTO projection used for detail pages. Includes the potentially
     * multi-KB `schemaJson` TEXT column.
     */
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
        createdAt = entity.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        updatedAt = entity.updatedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * Lightweight list-projection → DTO mapping that intentionally omits the large
     * `schemaJson` field, cutting ~90% of the response payload on listing pages.
     */
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
        createdAt = summary.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        updatedAt = summary.updatedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * Create a new `WfSchema` entity from an incoming create DTO.
     */
    fun toEntity(dto: SchemaDefinition): WfSchema = WfSchema().apply {
        schemaName = dto.schemaName ?: ""
        schemaType = dto.schemaType ?: "INPUT"
        schemaFormat = dto.schemaFormat ?: "json-schema"
        schemaJson = dto.schemaJson ?: ""
        description = dto.description
        frozen = dto.frozen ?: false
        scope = dto.scope ?: "PLATFORM"
        appGroup = dto.appGroup
        dto.createdAt?.let { createdAt = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
        dto.updatedAt?.let { updatedAt = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
    }
}
