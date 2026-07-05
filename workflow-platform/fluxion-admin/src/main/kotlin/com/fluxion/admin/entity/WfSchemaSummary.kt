package com.fluxion.admin.entity

import java.time.LocalDateTime

/**
 * Schema 列表专用的「轻量投影 DTO」。
 *
 * 对比 [WfSchema]（完整实体）：
 * - 不包含 schemaJson（MySQL TEXT 类型，平均每个 schema 几 KB~几十 KB，列表页完全不需要）
 * - 仅保留列表页展示/筛选所需的小字段（20 条的 payload 从几百 KB 降到几 KB）
 *
 * 由 Repository 层通过 JPQL `SELECT NEW` 直接构造，避免 Hibernate 加载 WfSchema 实体时
 * 对 schemaJson 的 EAGER 加载（JPA 对基本类型 String 默认 FetchType.EAGER）。
 */
data class WfSchemaSummary(
    val id: Long,
    val schemaName: String,
    val schemaType: String,
    val schemaFormat: String,
    val description: String?,
    val frozen: Boolean,
    val scope: String,
    val appGroup: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
