package com.fluxion.admin.entity

import com.fluxion.admin.generated.model.FunctionStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * Registered workflow-function metadata entity.
 *
 * Represents a callable function reference that workflows can invoke by `functionName`.
 * Supports multiple function types (BUILTIN / SCRIPT / JAVA_CLASS / EXTERNAL) with a
 * unified `config` JSON column for per-type parameters. Uniqueness is scoped to
 * `(scope, app_group, function_name)` so that PLATFORM and private PRIVATE namespaces
 * do not collide.
 *
 * Source table: `wf_function`.
 *
 * Collaborates with: WfFunctionRepository, WfFunctionService, AdminFunctionComponent
 * (publishes changes to Worker instances), FunctionRegistry (runtime in-memory lookup).
 */
@Entity
@Table(
    name = "wf_function",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_scope_app_function", columnNames = ["scope", "app_group", "function_name"])
    ]
)
class WfFunction : BaseEntity() {

    /** Invocation reference name used by workflow nodes, maps to column `function_name`. */
    @Column(name = "function_name", nullable = false, length = 128)
    var functionName: String = ""

    /** Visibility scope: PLATFORM / PRIVATE / MARKETPLACE, maps to column `scope`. */
    @Column(name = "scope", nullable = false, length = 16)
    var scope: String = "PRIVATE"

    /** Owning application group when scope=PRIVATE, maps to column `app_group`. */
    @Column(name = "app_group", length = 128)
    var appGroup: String? = null

    /** Original listing reference when installed from Marketplace, maps to column `source_ref`. */
    @Column(name = "source_ref", length = 128)
    var sourceRef: String? = null

    /** Execution dispatch type: BUILTIN / SCRIPT_GROOVY / SCRIPT_JS / JAVA_CLASS / EXTERNAL / CUSTOM, maps to column `function_type`. */
    @Column(name = "function_type", nullable = false, length = 32)
    var functionType: String = "BUILTIN"

    /** Optional domain classifier for UI grouping, maps to column `domain`. */
    @Column(name = "domain", length = 32)
    var domain: String? = null

    /** Unified per-function-type configuration JSON, maps to column `config`. */
    @Column(name = "config", columnDefinition = "JSON")
    var config: String? = null

    /** Runtime lifecycle status (ACTIVE = invocable, INACTIVE = hidden/disabled). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: FunctionStatus = FunctionStatus.ACTIVE

    /** Owning app, maps to column `app_id`. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id")
    var app: App? = null
}
