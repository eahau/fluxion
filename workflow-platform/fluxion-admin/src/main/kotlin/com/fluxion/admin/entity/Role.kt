package com.fluxion.admin.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Security role entity aligning with Spring Security `roles` table convention.
 *
 * Primary key is the business `roleName` string. Permissions are owned (orphan-removal +
 * cascade ALL) so that persisting a role automatically flushes its permission set.
 *
 * Collaborates with: Permission (one-to-many owned collection), User (many-to-many join),
 * RoleRepository, RoleService.
 */
@Entity
@Table(name = "roles")
class Role(

    /** Unique role name / business primary key, maps to column `role_name`. */
    @Id
    @Column(name = "role_name", nullable = false, length = 64)
    var roleName: String = "",

    /** Human-readable role description, maps to column `description`. */
    @Column(name = "description", length = 256)
    var description: String? = null,

    /** Record creation timestamp, maps to column `created_at`. */
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
) {

    /** Permissions owned by this role. Cascade ALL + orphan removal keeps role_permissions in sync. */
    @OneToMany(mappedBy = "role", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    var permissions: MutableSet<Permission> = mutableSetOf()

    constructor() : this(roleName = "")

    @PreUpdate
    fun preUpdate() {
    }
}
