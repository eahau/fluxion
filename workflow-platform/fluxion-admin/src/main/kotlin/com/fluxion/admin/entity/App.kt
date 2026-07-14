package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * Application (tenant) root entity — every workflow / schema / function /
 * sandbox / resource-binding hangs off an App.
 *
 * Replaces the legacy free-form `app_group` string tag with a stable entity
 * carrying owner, display-name, lifecycle status, and an immutable `appKey`
 * used for cross-service routing (worker instance registration, debug-session
 * scoping, MQ topic-prefix generation).
 *
 * Source table: `app` (created by Flyway V14).
 *
 * Collaborates with: AppRepository, AppResourceBinding (resources),
 * SandboxInstance (debug sandboxes), WfDefinition / WfSchema / WfFunction
 * (workflow assets with `app_id` foreign key, added in V18).
 */
@Entity
@Table(
    name = "app",
    uniqueConstraints = [UniqueConstraint(name = "uk_app_key", columnNames = ["app_key"])],
    indexes = [Index(name = "idx_status", columnList = "status"), Index(name = "idx_owner", columnList = "owner")]
)
class App : BaseEntity() {

    /** Immutable stable identifier — maps 1:1 to the legacy `app_group` string. */
    @Column(name = "app_key", nullable = false, length = 64)
    var appKey: String = ""

    /** Human-readable display name shown in the admin console. */
    @Column(name = "app_name", nullable = false, length = 128)
    var appName: String = ""

    /** Free-form description text. */
    @Column(name = "description", length = 512)
    var description: String? = null

    /** Owner username (grants full edit rights beyond standard role-based ACL). */
    @Column(name = "owner", length = 64)
    var owner: String? = null

    /** ACTIVE / INACTIVE / DISABLED — inactive apps are hidden from new-workflow picker. */
    @Column(name = "status", nullable = false, length = 16)
    var status: String = "ACTIVE"
}
