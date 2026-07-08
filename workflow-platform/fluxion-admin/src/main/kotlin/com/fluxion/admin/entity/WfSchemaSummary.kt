package com.fluxion.admin.entity

import java.time.LocalDateTime

/**
 * Lightweight projection DTO for the schema list page.
 *
 * Deliberately excludes `schemaJson` (a large TEXT column averaging KB~tens of KB
 * per row). JPQL `SELECT NEW` in WfSchemaRepository constructs instances directly
 * from the result set, bypassing Hibernate's EAGER fetch of the TEXT column and
 * reducing list-page payload from hundreds of KB to a few KB for 50 rows.
 *
 * Collaborates with: WfSchemaRepository (JPQL SELECT NEW producer), WfSchemaService
 * (list endpoint consumer), SchemaMapper (to DTO transformation).
 */
data class WfSchemaSummary(
    val id: Long,
    val schemaName: String,
    val schemaType: String,
    val schemaFormat: String,
    val description: String?,
    val frozen: Boolean,
    val scope: String,
    val appGroup: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
