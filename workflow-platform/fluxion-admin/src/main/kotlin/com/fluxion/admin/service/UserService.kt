package com.fluxion.admin.service

import com.fluxion.admin.entity.User
import com.fluxion.admin.repository.UserRepository
import com.fluxion.admin.util.toPage
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * CRUD and business-logic service for [User] account entities.
 *
 * Wraps [UserRepository] with password encoding (BCrypt via [PasswordEncoder]) and
 * in-memory keyword search across username/nickname/email. Used by UserController for
 * admin UI user management and by AdminUserDetailsService during authentication.
 *
 * Collaborates with: UserRepository, PasswordEncoder (Spring Security delegating factory),
 * TenantService (user↔app_group membership lives there).
 */
@Service
class UserService(
    private val repository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    /**
     * Paginated list with optional in-memory keyword filter across username/nickname/email
     * (case-insensitive substring match). Pagination is applied in memory via [toPage].
     */
    fun list(keyword: String?, pageable: Pageable): Page<User> {
        val all = if (keyword.isNullOrBlank()) {
            repository.findAll()
        } else {
            val lower = keyword.lowercase()
            repository.findAll().filter {
                it.username.lowercase().contains(lower) ||
                    it.nickname?.lowercase()?.contains(lower) == true ||
                    it.email?.lowercase()?.contains(lower) == true
            }
        }
        return all.toPage(pageable)
    }

    /** Fetch a user by business primary key (username). */
    fun get(username: String): User? {
        return repository.findByUsername(username)
    }

    /**
     * Persist a new user. Always re-encodes the raw `password` field with BCrypt before
     * writing so the DB never stores plaintext.
     */
    fun create(user: User): User {
        user.password = passwordEncoder.encode(user.password)
        user.createdAt = LocalDateTime.now()
        user.updatedAt = LocalDateTime.now()
        return repository.save(user)
    }

    /** Update mutable fields on an existing user (caller is responsible for PK lookup). */
    fun update(user: User): User {
        user.updatedAt = LocalDateTime.now()
        return repository.save(user)
    }

    /** Re-encode a new plaintext password and persist it for the given user. */
    fun updatePassword(username: String, newPassword: String) {
        val user = repository.findByUsername(username)
            ?: throw IllegalArgumentException("User not found: `$username")
        user.password = passwordEncoder.encode(newPassword)
        user.updatedAt = LocalDateTime.now()
        repository.save(user)
    }

    /** Delete a user by business primary key. Does NOT cascade memberships; callers orchestrate. */
    fun delete(username: String) {
        repository.deleteById(username)
    }

    /** Existence check used before create to return a friendlier 409 response. */
    fun existsByUsername(username: String): Boolean {
        return repository.findByUsername(username) != null
    }
}
