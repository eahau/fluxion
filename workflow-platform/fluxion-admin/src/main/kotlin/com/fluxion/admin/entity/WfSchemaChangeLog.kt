package com.fluxion.admin.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Granular field-level change log for schema evolution audits.
 *
 * Records each ADD / MODIFY / DEPRECATE / FREEZE / UNFREEZE mutation on a schema field
 * (identified by JSON path like `$.schemaJson.properties.age`). Enables diff viewing,
 * compliance reports, and compatibility impact analysis without requiring the user
 * to compare full schema payloads. Separate from WfSchema because CRUD-level audit is
 * captured by AuditLog; this is a domain-specific deep-change log.
 *
 * Source table: `wf_schema_change_log`.
 *
 * Collaborates with: WfSchemaChangeLogRepository, WfSchemaService (writes on UPDATE),
 * SchemaCompatibilityValidator (reads history for additive-only checks).
 */
@Entity
@Table(name = "wf_schema_change_log")
class WfSchemaChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    /** Schema name this entry belongs to (denormalized for direct queries), maps to column `schema_name`. */
    @Column(name = "schema_name", nullable = false, length = 128)
    var schemaName: String = ""

    /** JSON pointer / path of the changed field, e.g. `$.properties.age`, maps to column `field_path`. */
    @Column(name = "field_path", nullable = false, length = 256)
    var fieldPath: String = ""

    /** Change category: ADD / MODIFY / DEPRECATE / UNFREEZE / FREEZE, maps to column `change_type`. */
    @Column(name = "change_type", nullable = false, length = 32)
    var changeType: String = ""

    /** Previous value (JSON encoded) before the change, maps to column `old_value`. */
    @Column(name = "old_value", columnDefinition = "TEXT")
    var oldValue: String? = null

    /** New value (JSON encoded) after the change, maps to column `new_value`. */
    @Column(name = "new_value", columnDefinition = "TEXT")
    var newValue: String? = null

    /** Username who performed the change, maps to column `operator`. */
    @Column(name = "operator", length = 128)
    var operator: String? = null

    /** Human-readable reason for breaking/unfreeze changes, maps to column `reason`. */
    @Column(name = "reason", length = 512)
    var reason: String? = null

    /** Timestamp of the log entry, maps to column `created_at`. */
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
}
