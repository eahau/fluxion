package com.fluxion.admin.repository

import com.fluxion.admin.entity.Permission
import com.fluxion.admin.entity.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA repository for [Permission] join entities.
 *
 * Uses composite PK `Permission.PermissionId`. In most cases mutations flow through the
 * Role aggregate (cascade + orphan-removal) rather than direct calls here. The direct
 * delete is used when a role is replaced without triggering the ORM cascade path.
 */
@Repository
interface PermissionRepository : JpaRepository<Permission, Permission.PermissionId> {

    /** Delete every permission row belonging to the given role. */
    fun deleteByRole(role: Role)
}
