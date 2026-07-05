package com.fluxion.admin.service

import com.fluxion.admin.entity.Permission
import com.fluxion.admin.entity.Role
import com.fluxion.admin.repository.PermissionRepository
import com.fluxion.admin.repository.RoleRepository
import com.fluxion.admin.util.toPage
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 角色服务
 *
 * 负责角色 CRUD 和权限管理
 */
@Service
class RoleService(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository
) {

    fun list(keyword: String?, pageable: Pageable): Page<Role> {
        val all = if (keyword.isNullOrBlank()) {
            roleRepository.findAll()
        } else {
            val lower = keyword.lowercase()
            roleRepository.findAll().filter {
                it.roleName.lowercase().contains(lower) ||
                    it.description?.lowercase()?.contains(lower) == true
            }
        }
        return all.toPage(pageable)
    }

    fun get(roleName: String): Role? {
        return roleRepository.findByRoleName(roleName)
    }

    @Transactional
    fun create(roleName: String, description: String?, permissions: List<String>): Role {
        val role = Role(
            roleName = roleName,
            description = description,
            createdAt = LocalDateTime.now()
        )
        val savedRole = roleRepository.save(role)

        // 添加权限
        permissions.forEach { permission ->
            val perm = Permission(roleName = savedRole.roleName, permission = permission)
            permissionRepository.save(perm)
        }

        return savedRole
    }

    @Transactional
    fun update(roleName: String, description: String?, permissions: List<String>): Role {
        val role = roleRepository.findByRoleName(roleName)
            ?: throw IllegalArgumentException("Role not found: $roleName")
        
        role.description = description
        
        // 更新权限：先删除旧的，再添加新的
        permissionRepository.deleteByRole(role)
        permissions.forEach { permission ->
            val perm = Permission(role.roleName, permission)
            permissionRepository.save(perm)
        }

        return roleRepository.save(role)
    }

    fun delete(roleName: String) {
        roleRepository.deleteById(roleName)
    }

    fun existsByRoleName(roleName: String): Boolean {
        return roleRepository.findByRoleName(roleName) != null
    }
}
