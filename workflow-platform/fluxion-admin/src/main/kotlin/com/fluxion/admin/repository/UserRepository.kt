package com.fluxion.admin.repository

import com.fluxion.admin.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA repository for [User] entities.
 *
 * Primary key type is `String` because the User entity uses business PK `username`.
 * Used by AdminUserDetailsService (authentication lookup) and UserService (CRUD).
 */
@Repository
interface UserRepository : JpaRepository<User, String> {

    /** Look up a user by exact username match (principal used during login). */
    fun findByUsername(username: String): User?
}
