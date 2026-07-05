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
 * 用户实体。
 *
 * 对应 Spring Security 标准表 `users`，主键为业务用户名 `username`。
 */
@Entity
@Table(name = "users")
class User(

    /** 用户名，主键（列：`username`） */
    @Id
    @Column(name = "username", nullable = false, length = 64)
    var username: String = "",

    /** 密码，BCrypt 加密（列：`password`） */
    @Column(name = "password", nullable = false, length = 256)
    var password: String = "",

    /** 是否启用（列：`enabled`） */
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    /** 昵称（列：`nickname`） */
    @Column(name = "nickname", length = 64)
    var nickname: String? = null,

    /** 邮箱（列：`email`） */
    @Column(name = "email", length = 128)
    var email: String? = null,

    /** 手机号（列：`phone`） */
    @Column(name = "phone", length = 32)
    var phone: String? = null,

    /** 创建时间（列：`created_at`） */
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    /** 更新时间（列：`updated_at`） */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {

    /** 关联角色，通过 `user_roles` 中间表映射 */
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
