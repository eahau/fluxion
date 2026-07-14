package com.fluxion.admin.controller

import com.fluxion.admin.generated.api.RoleApi
import com.fluxion.admin.generated.model.PageResponseRole
import com.fluxion.admin.generated.model.RoleRequest
import com.fluxion.admin.generated.model.Role as RoleDto
import com.fluxion.admin.service.RoleService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId

/**
 * Role & permission administration REST controller (implements OpenAPI-generated [RoleApi]).
 *
 * Thin wrapper over [RoleService] that handles HTTP DTO ↔ entity mapping and the
 * code/id → roleName translation required by the generated OpenAPI contract (the
 * model uses both `id` and `code` fields; we normalise them to the business primary
 * key `roleName` internally).
 *
 * Collaborates with: RoleService (transactional role + permission persistence).
 */
@RestController
class RoleController(
    private val roleService: RoleService
) : RoleApi {

    /**
     * Paginated role list with optional keyword filter (roleName / description).
     * Page size is clamped at 100 to protect against massive list pages.
     */
    override fun listRoles(
        keyword: String?,
        page: Int,
        pageSize: Int
    ): ResponseEntity<PageResponseRole> {
        val size = pageSize.coerceAtMost(100)
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = roleService.list(keyword, pageable)
        return ResponseEntity.ok(PageResponseRole().apply {
            total = result.totalElements
            this.page = result.number
            this.pageSize = size
            list = result.content.map { toDto(it) }.toMutableList()
        })
    }

    /** Fetch a single role DTO by business PK (roleName). */
    override fun getRole(roleId: String): ResponseEntity<RoleDto> {
        val entity = roleService.get(roleId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toDto(entity))
    }

    /**
     * Create a new role with a set of permission strings.
     * Returns 400 if a role with the same code already exists (pre-check).
     */
    override fun createRole(roleRequest: RoleRequest): ResponseEntity<RoleDto> {
        if (roleService.existsByRoleName(roleRequest.code)) {
            return ResponseEntity.badRequest().build()
        }

        val entity = roleService.create(
            roleName = roleRequest.code,
            description = roleRequest.description,
            permissions = roleRequest.permissions ?: emptyList()
        )
        return ResponseEntity.ok(toDto(entity))
    }

    /** Update description + permission list for an existing role (full replacement). */
    override fun updateRole(roleId: String, roleRequest: RoleRequest): ResponseEntity<RoleDto> {
        val entity = roleService.update(
            roleName = roleId,
            description = roleRequest.description,
            permissions = roleRequest.permissions ?: emptyList()
        )
        return ResponseEntity.ok(toDto(entity))
    }

    /** Delete a role by business PK. 204 No Content on success or missing role. */
    override fun deleteRole(roleId: String): ResponseEntity<Unit> {
        roleService.delete(roleId)
        return ResponseEntity.noContent().build()
    }

    /** Entity → OpenAPI DTO mapper. Note: id/code both mirror roleName per API contract. */
    private fun toDto(entity: com.fluxion.admin.entity.Role): RoleDto = RoleDto(name = entity.roleName).apply {
        id = entity.roleName
        code = entity.roleName
        description = entity.description
        permissions = entity.permissions.map { it.permission }.toMutableList()
        createdAt = entity.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        updatedAt = entity.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
