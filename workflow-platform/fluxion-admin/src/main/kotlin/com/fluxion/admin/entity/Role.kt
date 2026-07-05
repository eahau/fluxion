package com.fluxion.admin.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 角色实体。
 *
 * 对应 Spring Security 标准表 `roles`，主键为业务角色名 `role_name`。
 */
@Entity
@Table(name = "roles")
class Role(

    /** 角色名，主键（列：`role_name`） */
    @Id
    @Column(name = "role_name", nullable = false, length = 64)
    var roleName: String = "",

    /** 角色描述（列：`description`） */
    @Column(name = "description", length = 256)
    var description: String? = null,

    /** 创建时间（列：`created_at`） */
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
) {

    /** 关联权限集合，通过 `role_permissions` 表映射 */
    @OneToMany(mappedBy = "role", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    var permissions: MutableSet<Permission> = mutableSetOf()

    constructor() : this(roleName = "")

    @PreUpdate
    fun preUpdate() {
        // `roles` 表无 `updated_at` 列，此处仅保持实体一致性，实际由数据库维护时间戳
    }
}
