package com.fluxion.admin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

/**
 * `local`-profile security configuration: disables all authentication and authorization
 * so that developers can call endpoints directly via curl, Apifox or the frontend without
 * logging in.
 *
 * Activated only when the active Spring profiles include `local`. Any other profile is
 * served by `SecurityConfiguration` instead.
 */
@Configuration
@Profile("local")
class LocalSecurityConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun localSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
        return http.build()
    }

    @Bean
    fun authenticationManager(): AuthenticationManager = AuthenticationManager { authentication ->
        val username = authentication.name ?: "local"
        val user = User.withUsername(username)
            .password("{noop}")
            .authorities("ROLE_ADMIN")
            .build()
        UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
}
