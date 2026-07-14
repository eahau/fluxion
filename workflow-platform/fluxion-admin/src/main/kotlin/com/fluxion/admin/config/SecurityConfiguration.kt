package com.fluxion.admin.config

import com.fluxion.admin.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Production (non-local) Spring Security configuration for the admin console.
 *
 * Key traits:
 *   - **Stateless JWT**: sessions are disabled; every request is authenticated via the
 *     `Authorization: Bearer <token>` header through `JwtAuthenticationFilter`.
 *   - **Allow-list**: `/api/admin/auth/login` and `/actuator/health` are public; all
 *     other routes under `/api/admin/` require authentication.
 *   - **Provider chain**: optional LDAP / OAuth2 / CAS `AuthenticationProvider` beans can
 *     be contributed via Spring's context and will be tried before the default
 *     `DaoAuthenticationProvider` (backed by `AdminUserDetailsService`).
 *
 * Explicitly excluded for the `local` profile where `LocalSecurityConfiguration` applies.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!local")
class SecurityConfiguration(
    @param:Lazy private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val userDetailsService: UserDetailsService,
    @param:Lazy private val additionalProviders: List<AuthenticationProvider>? = null
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/admin/auth/login").permitAll()
                it.requestMatchers("/actuator/health").permitAll()
                it.requestMatchers("/api/admin/**").authenticated()
                it.anyRequest().permitAll()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
        return http.build()
    }

    @Bean
    fun authenticationManager(): AuthenticationManager {
        val providers = mutableListOf<AuthenticationProvider>()
        additionalProviders?.let { providers.addAll(it) }
        providers.add(daoAuthenticationProvider())
        return ProviderManager(providers)
    }

    @Bean
    fun daoAuthenticationProvider(): DaoAuthenticationProvider {
        return DaoAuthenticationProvider().apply {
            this.setUserDetailsService(userDetailsService)
            setPasswordEncoder(passwordEncoder())
        }
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
}
