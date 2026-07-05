package com.fluxion.admin.repository

import com.fluxion.admin.entity.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 角色 Repository
 */
@Repository
interface RoleRepository : JpaRepository<Role, String> {
    fun findByRoleName(roleName: String): Role?
}
