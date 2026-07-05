package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 用户-应用分组关联实体（多租户隔离）。
 *
 * 源表：`user_app_groups`
 */
@Entity
@Table(
    name = "user_app_groups",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_app_group", columnNames = ["username", "app_group"])
    ]
)
class UserAppGroup : BaseEntity() {

    /** 用户名（列：`username`） */
    @Column(name = "username", nullable = false, length = 64)
    var username: String = ""

    /** 应用分组（列：`app_group`） */
    @Column(name = "app_group", nullable = false, length = 128)
    var appGroup: String = ""
}
