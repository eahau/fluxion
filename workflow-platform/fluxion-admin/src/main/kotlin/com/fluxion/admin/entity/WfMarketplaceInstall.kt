package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 市场安装记录实体。
 *
 * 源表：`wf_marketplace_install`
 */
@Entity
@Table(
    name = "wf_marketplace_install",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_listing_app", columnNames = ["listing_id", "app_group"])
    ]
)
class WfMarketplaceInstall : BaseEntity() {

    /** 市场 listing ID（列：`listing_id`） */
    @Column(name = "listing_id", nullable = false, length = 128)
    var listingId: String = ""

    /** 安装者应用分组（列：`app_group`） */
    @Column(name = "app_group", nullable = false, length = 128)
    var appGroup: String = ""

    /** 安装者用户名（列：`installed_by`） */
    @Column(name = "installed_by", length = 64)
    var installedBy: String? = null
}
