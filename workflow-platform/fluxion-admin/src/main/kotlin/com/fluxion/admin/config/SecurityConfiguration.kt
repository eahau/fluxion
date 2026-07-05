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

/*
 * 生产环境安全配置
 *
 * - 关闭 Session，采用 JWT Stateless 认证
 * - /api/admin/auth/login 放行
 * - 其余 /api/admin/ 下接口需认证
 * - local profile 不启用（由 LocalSecurityConfiguration 覆盖）
 *
 * 认证提供者：
 * - 默认：DaoAuthenticationProvider（本地数据库，使用 AdminUserDetailsService）
 * - 可选：LdapAuthenticationProvider（LDAP，配置 fluxion.auth.adapter=ldap）
 * - 可选：其他 AuthenticationProvider（OAuth2、CAS 等，可扩展）
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
        // 优先添加额外的提供者（如 LDAP）
        additionalProviders?.let { providers.addAll(it) }
        // 始终添加本地提供者作为后备
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
