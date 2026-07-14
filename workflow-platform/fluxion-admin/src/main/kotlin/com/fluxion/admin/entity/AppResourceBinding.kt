package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * N:M junction between [App] and [AppResource] with per-binding scope + alias.
 *
 * `resourceScope` is a *soft* hint (UI only — the DB account permissions are
 * the hard enforcement); alias defaults to `resourceName` when null but lets
 * two apps call the same resource "default" vs "readonly-slave" for clarity
 * in workflow node parameter dropdowns.
 *
 * Source table: `app_resource_binding` (Flyway V16).
 */
@Entity
@Table(
    name = "app_resource_binding",
    uniqueConstraints = [UniqueConstraint(name = "uk_app_resource", columnNames = ["app_id", "resource_id"])],
    indexes = [Index(name = "idx_app_id", columnList = "app_id"), Index(name = "idx_resource_id", columnList = "resource_id")]
)
class AppResourceBinding : BaseEntity() {

    /** Owning app — foreign key to `app.id`. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "app_id", nullable = false, foreignKey = ForeignKey(name = "fk_arb_app"))
    lateinit var app: App

    /** Bound resource — foreign key to `app_resource.id`. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "resource_id", nullable = false, foreignKey = ForeignKey(name = "fk_arb_resource"))
    lateinit var resource: AppResource

    /** DEFAULT / READ_ONLY / WRITE_ONLY / CUSTOM — UI-level hint only. */
    @Column(name = "resource_scope", nullable = false, length = 16)
    var resourceScope: String = "DEFAULT"

    /** App-local alias. `null` / blank → fall back to `resource.resourceName`. */
    @Column(name = "alias_in_app", length = 64)
    var aliasInApp: String? = null

    /** Convenience: effective alias used in workflow-node dropdowns. */
    fun effectiveAlias(): String = aliasInApp?.takeIf { it.isNotBlank() } ?: resource.resourceName
}
