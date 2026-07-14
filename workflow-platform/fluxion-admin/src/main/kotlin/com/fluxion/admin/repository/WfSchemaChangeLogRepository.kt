package com.fluxion.admin.repository

import com.fluxion.admin.entity.WfSchemaChangeLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA repository for granular field-level [WfSchemaChangeLog] entries.
 *
 * Change logs are append-only; the only read paths are the per-schema audit timeline and
 * SchemaCompatibilityValidator which scans for a recent FREEZE / MODIFY event.
 */
@Repository
interface WfSchemaChangeLogRepository : JpaRepository<WfSchemaChangeLog, Long> {

    /** Full timeline of every change recorded against a given schema name. */
    fun findBySchemaNameOrderByCreatedAtDesc(schemaName: String): List<WfSchemaChangeLog>

    /** Filtered timeline restricted to one change category (ADD / MODIFY / FREEZE / ...). */
    fun findBySchemaNameAndChangeTypeOrderByCreatedAtDesc(
        schemaName: String,
        changeType: String
    ): List<WfSchemaChangeLog>
}
