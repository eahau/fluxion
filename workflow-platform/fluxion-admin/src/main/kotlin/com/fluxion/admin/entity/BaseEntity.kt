package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PreUpdate
import java.time.LocalDateTime

/**
 * JPA 实体基类。
 *
 * 提供自增主键与标准时间戳字段，所有以数值型 `id` 为主键的业务表实体均应继承此类。
 */
@MappedSuperclass
abstract class BaseEntity {

    /** 自增主键（列：`id`） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    /** 创建时间（列：`created_at`） */
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    /** 更新时间（列：`updated_at`） */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
