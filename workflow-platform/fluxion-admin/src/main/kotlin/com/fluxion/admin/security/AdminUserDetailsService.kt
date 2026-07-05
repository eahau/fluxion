package com.fluxion.admin.security

import com.fluxion.admin.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

/**
 * 后台用户 UserDetailsService
 *
 * 从 users 表加载用户，并从 user_roles 和 role_permissions 展开角色与权限。
 */
@Service
class AdminUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found: $username")

        val authorities = mutableListOf<SimpleGrantedAuthority>()

        // 添加角色作为 Authority
        user.roles.forEach { role ->
            authorities.add(SimpleGrantedAuthority("ROLE_${role.roleName.uppercase()}"))
            // 添加角色的权限作为 Authority
            role.permissions.forEach { permission ->
                authorities.add(SimpleGrantedAuthority(permission.permission))
            }
        }

        return org.springframework.security.core.userdetails.User
            .withUsername(user.username)
            .password(user.password)
            .authorities(authorities)
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(false)
            .disabled(!user.enabled)
            .build()
    }
}
