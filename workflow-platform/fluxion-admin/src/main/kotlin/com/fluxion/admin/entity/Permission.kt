package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable

/**
 * 角色权限实体。
 *
 * 对应 Spring Security 标准关联表 `role_permissions`，复合主键为 `(role_name, permission)`。
 */
@Entity
@Table(name = "role_permissions")
@IdClass(Permission.PermissionId::class)
class Permission : Serializable {

    /** 角色名，复合主键之一（列：`role_name`） */
    @Id
    @Column(name = "role_name", nullable = false, length = 64)
    var roleName: String = ""

    /** 权限标识，复合主键之一（列：`permission`） */
    @Id
    @Column(name = "permission", nullable = false, length = 100)
    var permission: String = ""

    /** 关联角色，只读映射，用于级联/查询（列：`role_name`） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_name", referencedColumnName = "role_name", insertable = false, updatable = false)
    var role: Role? = null

    /**
     * 复合主键类。
     */
    data class PermissionId(
        var roleName: String = "",
        var permission: String = ""
    ) : Serializable

    constructor()

    constructor(roleName: String, permission: String) {
        this.roleName = roleName
        this.permission = permission
    }
}
