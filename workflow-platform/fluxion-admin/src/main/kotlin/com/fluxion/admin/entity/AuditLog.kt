package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Immutable audit trail record for administrative mutations.
 *
 * One row is written per CREATE/UPDATE/DELETE/PUBLISH/OFFLINE action on a managed
 * resource (WORKFLOW/FUNCTION/SCHEMA/USER/ROLE). Records are append-only and never
 * modified after insertion.
 *
 * Source table: `wf_audit_log`.
 *
 * Collaborates with: AuditLogService (single writer), AuditLogRepository, GlobalExceptionHandler
 * (failed-action audit capture).
 */
@Entity
@Table(name = "wf_audit_log")
class AuditLog : BaseEntity() {

    /** Username that performed the action, maps to column `operator`. */
    @Column(name = "operator", nullable = false, length = 64)
    var operator: String = ""

    /** Action verb: CREATE / UPDATE / DELETE / PUBLISH / OFFLINE, maps to column `action`. */
    @Column(name = "action", nullable = false, length = 64)
    var action: String = ""

    /** Resource type: WORKFLOW / FUNCTION / SCHEMA / USER / ROLE, maps to column `resource_type`. */
    @Column(name = "resource_type", nullable = false, length = 64)
    var resourceType: String = ""

    /** Business identifier of the affected resource, maps to column `resource_id`. */
    @Column(name = "resource_id", nullable = false, length = 128)
    var resourceId: String = ""

    /** Optional JSON payload describing the before/after delta, maps to column `detail`. */
    @Column(name = "detail", length = 1024)
    var detail: String? = null

    /** Originating client IP address, maps to column `ip`. */
    @Column(name = "ip", length = 64)
    var ip: String? = null
}
