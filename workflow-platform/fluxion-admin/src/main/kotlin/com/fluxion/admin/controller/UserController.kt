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
 * User administration REST controller (implements OpenAPI-generated [UserApi]).
 *
 * Exposes endpoints for user CRUD, role association, and /me style current-user
 * resolution. In local / dev Spring profiles the unauthenticated case falls back
 * to a synthetic ADMIN mock so the UI can start without an OIDC/ACL server.
 *
 * Collaborates with: UserService (persistence + password encoding), RoleRepository
 * (role collection hydration for create/update DTO mapping), Environment (profile
 * probe for dev bypass).
 */
@RestController
class UserController(
    private val userService: UserService,
    private val roleRepository: RoleRepository,
    private val environment: Environment
) : UserApi {

    // True when any of the active profiles is local OR dev (case insensitive).
    private val isLocalOrDev: Boolean
        get() = environment.activeProfiles.any { it.equals("local", ignoreCase = true) || it.equals("dev", ignoreCase = true) }

    /**
     * Paginated user list with optional keyword filter (username / nickname / email).
     * Page size is capped at 100 regardless of client input to avoid runaway queries.
     */
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

    /** Fetch a single user DTO by business primary key (username). */
    override fun getUser(userId: String): ResponseEntity<UserDto> {
        val entity = userService.get(userId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toDto(entity))
    }

    /**
     * Create a new user.
     *
     * Guards:
     *   - 400 if the username already exists (pre-check rather than UK-sqlexception)
     *   - if request password is empty the username is used as default initial password
     *   - roleIds are resolved via RoleRepository, missing role names are silently skipped
     */
    override fun createUser(userRequest: UserRequest): ResponseEntity<UserDto> {
        if (userService.existsByUsername(userRequest.username)) {
            return ResponseEntity.badRequest().build()
        }

        val entity = User().apply {
            username = userRequest.username
            nickname = userRequest.nickname
            email = userRequest.email
            phone = userRequest.phone
            enabled = userRequest.status?.let { it.name == "ACTIVE" } ?: true
            password = userRequest.username
            userRequest.roleIds?.forEach { roleName ->
                roleRepository.findByRoleName(roleName)?.let { role ->
                    this.roles.add(role)
                }
            }
        }
        return ResponseEntity.ok(toDto(userService.create(entity)))
    }

    /**
     * Update mutable user fields (nickname / email / phone / enabled / roles) by id.
     * Roles list is fully replaced (clear + re-add) rather than merged.
     */
    override fun updateUser(userId: String, userRequest: UserRequest): ResponseEntity<UserDto> {
        val entity = userService.get(userId)
            ?: return ResponseEntity.notFound().build()
        entity.apply {
            nickname = userRequest.nickname
            email = userRequest.email
            phone = userRequest.phone
            enabled = userRequest.status?.let { it.name == "ACTIVE" } ?: enabled
            roles.clear()
            userRequest.roleIds?.forEach { roleName ->
                roleRepository.findByRoleName(roleName)?.let { role ->
                    this.roles.add(role)
                }
            }
        }
        return ResponseEntity.ok(toDto(userService.update(entity)))
    }

    /** Delete a user by business PK. No-op 204 if the user does not exist. */
    override fun deleteUser(userId: String): ResponseEntity<Unit> {
        userService.delete(userId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Return the currently authenticated user + their derived coarse-grained access flags.
     *
     * Anonymous / missing authentication either:
     *   - returns 401 in prod profiles
     *   - returns a mock ADMIN user (see [mockAdminUser]) in local/dev so the frontend
     *     can run locally without an identity provider.
     *
     * Role/permission extraction follows the Spring Security convention:
     * authorities starting with `ROLE_` are roles, everything else is a fine-grained
     * permission string.
     */
    override fun getCurrentUser(): ResponseEntity<CurrentUser> {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth == null || auth is AnonymousAuthenticationToken || !auth.isAuthenticated) {
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

    /** Synthetic local-dev user — full ADMIN + every fine-grained permission string. */
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

    /** Entity → OpenAPI DTO mapper. Times are converted to epoch millis using the system TZ. */
    private fun toDto(entity: User): UserDto = UserDto(username = entity.username).apply {
        id = entity.username
        nickname = entity.nickname
        email = entity.email
        phone = entity.phone
        status = if (entity.enabled) UserDto.Status.ACTIVE else UserDto.Status.INACTIVE
        roles = entity.roles.map { it.roleName }.toMutableList()
        createdAt = entity.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        updatedAt = entity.updatedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
