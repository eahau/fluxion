package com.fluxion.admin.security

import com.fluxion.admin.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

/**
 * Spring Security `UserDetailsService` implementation backed by the admin `users` table.
 *
 * Loads a user by username and flattens the user → roles → permissions graph into a flat
 * list of `GrantedAuthority` entries: roles get a `ROLE_` prefix while permissions are
 * stored as their raw permission string.
 */
@Service
class AdminUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found: `$username")

        val authorities = mutableListOf<SimpleGrantedAuthority>()

        user.roles.forEach { role ->
            authorities.add(SimpleGrantedAuthority("ROLE_${role.roleName.uppercase()}"))
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
