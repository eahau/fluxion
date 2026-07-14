package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable

/**
 * Role-to-permission join entity for the `role_permissions` intersection table.
 *
 * Uses a composite primary key `(roleName, permission)` via JPA `@IdClass`. The `role`
 * many-to-one is read-only (insertable=false, updatable=false) because `roleName` is already
 * managed as part of the composite PK.
 *
 * Collaborates with: Role (parent aggregate, owns cascade+orphan lifecycle), PermissionRepository.
 */
@Entity
@Table(name = "role_permissions")
@IdClass(Permission.PermissionId::class)
class Permission : Serializable {

    /** Role name — part of composite PK, maps to column `role_name`. */
    @Id
    @Column(name = "role_name", nullable = false, length = 64)
    var roleName: String = ""

    /** Permission identifier string — part of composite PK, maps to column `permission`. */
    @Id
    @Column(name = "permission", nullable = false, length = 100)
    var permission: String = ""

    /** Read-only parent association for JPQL joins. Column `role_name` is owned by the PK field. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_name", referencedColumnName = "role_name", insertable = false, updatable = false)
    var role: Role? = null

    /** JPA composite primary key class for Permission. */
    data class PermissionId(
        var roleName: String = "",
        var permission: String = ""
    ) : Serializable

    constructor()

    constructor(roleName: String, permission: String) {
        this.roleName = roleName
        this.permission = permission
    }
}
