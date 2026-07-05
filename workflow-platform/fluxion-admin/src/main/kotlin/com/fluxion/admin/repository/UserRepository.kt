package com.fluxion.admin.repository

import com.fluxion.admin.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 用户 Repository
 */
@Repository
interface UserRepository : JpaRepository<User, String> {
    fun findByUsername(username: String): User?
}
