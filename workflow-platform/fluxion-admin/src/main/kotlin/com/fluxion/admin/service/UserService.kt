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
 * 用户服务
 *
 * 负责用户 CRUD 和业务逻辑
 */
@Service
class UserService(
    private val repository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

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

    fun get(username: String): User? {
        return repository.findByUsername(username)
    }

    fun create(user: User): User {
        // 加密密码
        user.password = passwordEncoder.encode(user.password)
        user.createdAt = LocalDateTime.now()
        user.updatedAt = LocalDateTime.now()
        return repository.save(user)
    }

    fun update(user: User): User {
        user.updatedAt = LocalDateTime.now()
        return repository.save(user)
    }

    fun updatePassword(username: String, newPassword: String) {
        val user = repository.findByUsername(username)
            ?: throw IllegalArgumentException("User not found: $username")
        user.password = passwordEncoder.encode(newPassword)
        user.updatedAt = LocalDateTime.now()
        repository.save(user)
    }

    fun delete(username: String) {
        repository.deleteById(username)
    }

    fun existsByUsername(username: String): Boolean {
        return repository.findByUsername(username) != null
    }
}
