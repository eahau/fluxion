package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * User account entity matching Spring Security standard `users` table layout.
 *
 * Primary key is the business `username` string (not a numeric id) to align with
 * `JdbcUserDetailsManager` defaults. Roles are loaded eagerly via the `user_roles` join table
 * so that `UserDetails.getAuthorities()` is populated after a single fetch.
 *
 * Collaborates with: Role (many-to-many via user_roles), AdminUserDetailsService (Spring Security
 * principal loading), UserRepository.
 */
@Entity
@Table(name = "users")
class User(

    /** Unique username / business primary key, maps to column `username`. */
    @Id
    @Column(name = "username", nullable = false, length = 64)
    var username: String = "",

    /** BCrypt-hashed password, maps to column `password`. */
    @Column(name = "password", nullable = false, length = 256)
    var password: String = "",

    /** Whether this account is enabled (Spring Security contract), maps to column `enabled`. */
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    /** Human-readable display name, maps to column `nickname`. */
    @Column(name = "nickname", length = 64)
    var nickname: String? = null,

    /** Contact email address, maps to column `email`. */
    @Column(name = "email", length = 128)
    var email: String? = null,

    /** Contact phone number, maps to column `phone`. */
    @Column(name = "phone", length = 32)
    var phone: String? = null,

    /** Record creation timestamp, maps to column `created_at`. */
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    /** Record last-update timestamp, maps to column `updated_at`. */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {

    /** Associated roles, joined via `user_roles` intersection table. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "username")],
        inverseJoinColumns = [JoinColumn(name = "role_name")]
    )
    var roles: MutableSet<Role> = mutableSetOf()

    constructor() : this(username = "")

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
