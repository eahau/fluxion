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
 * Role and permission management service.
 *
 * Handles the full [Role]↔[Permission] aggregate lifecycle. Because Role.permissions is
 * owned with cascade+orphanRemoval this class could technically rely on ORM cascade for
 * permission updates. Instead it uses the explicit delete-then-insert pattern via
 * [PermissionRepository] for predictability in unit tests and to avoid subtle dirty-tracking
 * bugs when the caller passes a fresh list.
 *
 * Collaborates with: RoleRepository, PermissionRepository.
 */
@Service
class RoleService(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository
) {

    /**
     * Paginated list with optional in-memory keyword filter across roleName/description
     * (case-insensitive substring match).
     */
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

    /** Fetch a role by business primary key. */
    fun get(roleName: String): Role? {
        return roleRepository.findByRoleName(roleName)
    }

    /**
     * Create a new role along with its permission set. Runs inside a transaction so
     * any permission-insert failure rolls back the role row too.
     */
    @Transactional
    fun create(roleName: String, description: String?, permissions: List<String>): Role {
        val role = Role(
            roleName = roleName,
            description = description,
            createdAt = LocalDateTime.now()
        )
        val savedRole = roleRepository.save(role)

        permissions.forEach { permission ->
            val perm = Permission(roleName = savedRole.roleName, permission = permission)
            permissionRepository.save(perm)
        }

        return savedRole
    }

    /**
     * Update a role's description and permissions. Permission refresh is delete-then-insert
     * — list order and deduplication are the caller's responsibility.
     */
    @Transactional
    fun update(roleName: String, description: String?, permissions: List<String>): Role {
        val role = roleRepository.findByRoleName(roleName)
            ?: throw IllegalArgumentException("Role not found: `$roleName")

        role.description = description

        // Replace the permission collection atomically
        permissionRepository.deleteByRole(role)
        permissions.forEach { permission ->
            val perm = Permission(role.roleName, permission)
            permissionRepository.save(perm)
        }

        return roleRepository.save(role)
    }

    /** Delete a role by business PK (ORM cascade will also remove role_permissions rows). */
    fun delete(roleName: String) {
        roleRepository.deleteById(roleName)
    }

    /** Pre-check for role-create 409 responses. */
    fun existsByRoleName(roleName: String): Boolean {
        return roleRepository.findByRoleName(roleName) != null
    }
}
