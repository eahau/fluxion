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
 * 角色管理 REST API
 */
@RestController
class RoleController(
    private val roleService: RoleService
) : RoleApi {

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

    override fun getRole(roleId: String): ResponseEntity<RoleDto> {
        val entity = roleService.get(roleId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toDto(entity))
    }

    override fun createRole(roleRequest: RoleRequest): ResponseEntity<RoleDto> {
        // 检查角色名是否已存在
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

    override fun updateRole(roleId: String, roleRequest: RoleRequest): ResponseEntity<RoleDto> {
        val entity = roleService.update(
            roleName = roleId,
            description = roleRequest.description,
            permissions = roleRequest.permissions ?: emptyList()
        )
        return ResponseEntity.ok(toDto(entity))
    }

    override fun deleteRole(roleId: String): ResponseEntity<Unit> {
        roleService.delete(roleId)
        return ResponseEntity.noContent().build()
    }

    private fun toDto(entity: com.fluxion.admin.entity.Role): RoleDto = RoleDto(name = entity.roleName).apply {
        id = entity.roleName
        code = entity.roleName
        description = entity.description
        permissions = entity.permissions.map { it.permission }.toMutableList()
        createdAt = entity.createdAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
        updatedAt = entity.createdAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
    }
}
