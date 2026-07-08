package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Data-schema definition entity — reusable input/output/standalone schemas.
 *
 * Schemas are referenced by workflow input/output via `$ref: "schema:<name>"` and are
 * versioned implicitly via the numeric `id`. A frozen schema cannot be modified or deleted
 * unless the caller holds `schema:unlock` permission. Supports multiple formats
 * (json-schema / protobuf / avro) stored in `schemaJson`.
 *
 * Source table: `wf_schema`.
 *
 * Collaborates with: WfSchemaRepository, WfSchemaService, SchemaCompatibilityValidator,
 * SchemaConfigPublisher (pushes changes to Worker instances), WfDefinition (input/output
 * references).
 */
@Entity
@Table(name = "wf_schema")
class WfSchema : BaseEntity() {

    /** Unique schema reference name, maps to column `schema_name`. */
    @Column(name = "schema_name", nullable = false, unique = true, length = 128)
    var schemaName: String = ""

    /** Comma-separated type tags (INPUT, OUTPUT, etc.) for multi-use classification, maps to column `schema_type`. */
    @Column(name = "schema_type", nullable = false, length = 64)
    var schemaType: String = "INPUT"

    /** Schema format identifier: json-schema / protobuf / avro, maps to column `schema_format`. */
    @Column(name = "schema_format", nullable = false, length = 32)
    var schemaFormat: String = "json-schema"

    /** Raw schema content (format-specific), maps to column `schema_json`. */
    @Column(name = "schema_json", nullable = false, columnDefinition = "TEXT")
    var schemaJson: String = ""

    /** Human-readable description, maps to column `description`. */
    @Column(name = "description", length = 512)
    var description: String? = null

    /** If true the schema is locked and requires `schema:unlock` permission to mutate, maps to column `frozen`. */
    @Column(name = "frozen", nullable = false)
    var frozen: Boolean = false

    /** Visibility: PLATFORM / PRIVATE, maps to column `scope`. */
    @Column(name = "scope", nullable = false, length = 16)
    var scope: String = "PLATFORM"

    /** Owning app group when scope=PRIVATE, maps to column `app_group`. */
    @Column(name = "app_group", length = 128)
    var appGroup: String? = null
}
