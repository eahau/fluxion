package com.fluxion.admin.repository

import com.fluxion.admin.entity.Permission
import com.fluxion.admin.entity.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 权限 Repository
 */
@Repository
interface PermissionRepository : JpaRepository<Permission, Permission.PermissionId> {
    fun deleteByRole(role: Role)
}
