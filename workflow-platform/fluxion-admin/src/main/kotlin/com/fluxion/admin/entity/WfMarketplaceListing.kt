package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 市场发布记录实体。
 *
 * 源表：`wf_marketplace_listing`
 */
@Entity
@Table(name = "wf_marketplace_listing")
class WfMarketplaceListing : BaseEntity() {

    /** 市场 ID（列：`listing_id`） */
    @Column(name = "listing_id", nullable = false, unique = true, length = 128)
    var listingId: String = ""

    /** 来源类型：WORKFLOW/FUNCTION/WORKFLOW_TEMPLATE（列：`source_type`） */
    @Column(name = "source_type", nullable = false, length = 16)
    var sourceType: String = "WORKFLOW"

    /** 源工作流/函数 ID（列：`source_id`） */
    @Column(name = "source_id", nullable = false, length = 128)
    var sourceId: String = ""

    /** 发布时锁定的版本（列：`source_version`） */
    @Column(name = "source_version", nullable = false)
    var sourceVersion: Int = 1

    /** 市场展示标题（列：`title`） */
    @Column(name = "title", nullable = false, length = 256)
    var title: String = ""

    /** 描述（列：`description`） */
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null

    /** 标签列表（JSON 数组）（列：`tags`） */
    @Column(name = "tags", columnDefinition = "JSON")
    var tags: String? = null

    /** 发布者（列：`author`） */
    @Column(name = "author", nullable = false, length = 64)
    var author: String = ""

    /** 状态：ACTIVE/DEPRECATED（列：`status`） */
    @Column(name = "status", nullable = false, length = 16)
    var status: String = "ACTIVE"

    /** 安装次数（列：`install_count`） */
    @Column(name = "install_count", nullable = false)
    var installCount: Int = 0

    /** 模版配置（仅 WORKFLOW_TEMPLATE 时使用，JSON）（列：`template_config`） */
    @Column(name = "template_config", columnDefinition = "TEXT")
    var templateConfig: String? = null
}
