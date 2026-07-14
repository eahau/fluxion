package com.fluxion.admin.security

import com.fluxion.acl.spi.AuthProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Servlet filter that extracts and verifies a JWT from the `Authorization: Bearer <token>`
 * header, then populates the Spring `SecurityContext` for downstream method/URL security.
 *
 * Delegates actual token verification to the pluggable `AuthProvider` SPI so a deployment can
 * transparently switch between local JWT and a remote OAuth/OIDC IdP.
 */
@Component
class JwtAuthenticationFilter(
    private val authProvider: AuthProvider
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)
        if (token != null) {
            val claims = authProvider.validateToken(token)
            if (claims != null && SecurityContextHolder.getContext().authentication == null) {
                val authorities = buildAuthorities(claims.roles, claims.permissions)
                val auth = UsernamePasswordAuthenticationToken(
                    claims.username, null, authorities
                )
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun buildAuthorities(roles: List<String>, permissions: List<String>): List<SimpleGrantedAuthority> {
        val authorities = mutableListOf<SimpleGrantedAuthority>()
        roles.forEach { authorities.add(SimpleGrantedAuthority("ROLE_$it")) }
        permissions.forEach { authorities.add(SimpleGrantedAuthority(it)) }
        return authorities
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearer = request.getHeader("Authorization") ?: return null
        return if (bearer.startsWith("Bearer ", ignoreCase = true)) {
            bearer.substring(7)
        } else null
    }
}
