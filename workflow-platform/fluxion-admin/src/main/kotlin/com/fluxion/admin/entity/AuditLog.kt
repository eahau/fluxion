package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 审计日志实体。
 *
 * 源表：`wf_audit_log`
 */
@Entity
@Table(name = "wf_audit_log")
class AuditLog : BaseEntity() {

    /** 操作人账号（列：`operator`） */
    @Column(name = "operator", nullable = false, length = 64)
    var operator: String = ""

    /** 操作类型：CREATE/UPDATE/DELETE/PUBLISH/OFFLINE（列：`action`） */
    @Column(name = "action", nullable = false, length = 64)
    var action: String = ""

    /** 资源类型：WORKFLOW/FUNCTION/SCHEMA/USER/ROLE（列：`resource_type`） */
    @Column(name = "resource_type", nullable = false, length = 64)
    var resourceType: String = ""

    /** 资源业务 ID（列：`resource_id`） */
    @Column(name = "resource_id", nullable = false, length = 128)
    var resourceId: String = ""

    /** 操作详情 JSON（列：`detail`） */
    @Column(name = "detail", length = 1024)
    var detail: String? = null

    /** 操作人 IP（列：`ip`） */
    @Column(name = "ip", length = 64)
    var ip: String? = null
}
