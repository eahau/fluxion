package com.fluxion.admin.controller

import com.fluxion.admin.entity.Role
import com.fluxion.admin.entity.User
import com.fluxion.admin.generated.api.UserApi
import com.fluxion.admin.generated.model.CurrentUser
import com.fluxion.admin.generated.model.PageResponseUser
import com.fluxion.admin.generated.model.UserAccess
import com.fluxion.admin.generated.model.UserRequest
import com.fluxion.admin.generated.model.User as UserDto
import com.fluxion.admin.repository.RoleRepository
import com.fluxion.admin.service.UserService
import org.springframework.core.env.Environment
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId

/**
 * 用户管理 REST API
 */
@RestController
class UserController(
    private val userService: UserService,
    private val roleRepository: RoleRepository,
    private val environment: Environment
) : UserApi {

    private val isLocalOrDev: Boolean
        get() = environment.activeProfiles.any { it.equals("local", ignoreCase = true) || it.equals("dev", ignoreCase = true) }

    override fun listUsers(
        keyword: String?,
        page: Int,
        pageSize: Int
    ): ResponseEntity<PageResponseUser> {
        val size = pageSize.coerceAtMost(100)
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        val result = userService.list(keyword, pageable)
        return ResponseEntity.ok(PageResponseUser().apply {
            total = result.totalElements
            this.page = result.number
            this.pageSize = size
            list = result.content.map { toDto(it) }.toMutableList()
        })
    }

    override fun getUser(userId: String): ResponseEntity<UserDto> {
        val entity = userService.get(userId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toDto(entity))
    }

    override fun createUser(userRequest: UserRequest): ResponseEntity<UserDto> {
        // 检查用户名是否已存在
        if (userService.existsByUsername(userRequest.username)) {
            return ResponseEntity.badRequest().build()
        }

        val entity = User().apply {
            username = userRequest.username
            nickname = userRequest.nickname
            email = userRequest.email
            phone = userRequest.phone
            enabled = userRequest.status?.let { it.name == "ACTIVE" } ?: true
            // 创建时若前端未传密码，使用用户名作为默认密码
            password = userRequest.username
            // 关联角色
            userRequest.roleIds?.forEach { roleName ->
                roleRepository.findByRoleName(roleName)?.let { role ->
                    this.roles.add(role)
                }
            }
        }
        return ResponseEntity.ok(toDto(userService.create(entity)))
    }

    override fun updateUser(userId: String, userRequest: UserRequest): ResponseEntity<UserDto> {
        val entity = userService.get(userId)
            ?: return ResponseEntity.notFound().build()
        entity.apply {
            nickname = userRequest.nickname
            email = userRequest.email
            phone = userRequest.phone
            enabled = userRequest.status?.let { it.name == "ACTIVE" } ?: enabled
            // 更新角色关联
            roles.clear()
            userRequest.roleIds?.forEach { roleName ->
                roleRepository.findByRoleName(roleName)?.let { role ->
                    this.roles.add(role)
                }
            }
        }
        return ResponseEntity.ok(toDto(userService.update(entity)))
    }

    override fun deleteUser(userId: String): ResponseEntity<Unit> {
        userService.delete(userId)
        return ResponseEntity.noContent().build()
    }

    override fun getCurrentUser(): ResponseEntity<CurrentUser> {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth == null || auth is AnonymousAuthenticationToken || !auth.isAuthenticated) {
            // local / dev 环境下免鉴权，返回 mock admin 用户，方便前端直接调试
            if (isLocalOrDev) {
                return ResponseEntity.ok(mockAdminUser())
            }
            return ResponseEntity.status(401).build()
        }

        val roles = auth.authorities
            .map { it.authority }
            .filter { it.startsWith("ROLE_") }
            .map { it.removePrefix("ROLE_") }

        val permissions = auth.authorities
            .map { it.authority }
            .filter { !it.startsWith("ROLE_") }

        val hasAdmin = roles.contains("ADMIN")
        val hasDeveloper = roles.contains("DEVELOPER")
        val hasOperator = roles.contains("OPERATOR")
        val canEdit = hasAdmin || hasDeveloper
        val canPublish = hasAdmin || hasDeveloper || hasOperator

        return ResponseEntity.ok(CurrentUser().apply {
            name = auth.name
            avatar = ""
            this.roles = roles.toMutableList()
            this.permissions = permissions.toMutableList()
            access = UserAccess().apply {
                canView = true
                this.canEdit = canEdit
                this.canPublish = canPublish
                canAdmin = hasAdmin
            }
        })
    }

    private fun mockAdminUser(): CurrentUser = CurrentUser().apply {
        name = "local"
        avatar = ""
        roles = mutableListOf("ADMIN")
        permissions = mutableListOf(
            "schema:manage", "schema:unlock", "schema:edit",
            "workflow:edit", "function:edit"
        )
        access = UserAccess().apply {
            canView = true
            canEdit = true
            canPublish = true
            canAdmin = true
        }
    }

    private fun toDto(entity: User): UserDto = UserDto(username = entity.username).apply {
        id = entity.username
        nickname = entity.nickname
        email = entity.email
        phone = entity.phone
        status = if (entity.enabled) UserDto.Status.ACTIVE else UserDto.Status.INACTIVE
        roles = entity.roles.map { it.roleName }.toMutableList()
        createdAt = entity.createdAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
        updatedAt = entity.updatedAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
    }
}
