package com.fluxion.admin.repository

import com.fluxion.admin.entity.WfSchema
import com.fluxion.admin.entity.WfSchemaSummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface WfSchemaRepository : JpaRepository<WfSchema, Long> {

    fun findBySchemaName(schemaName: String): Optional<WfSchema>

    fun existsBySchemaName(schemaName: String): Boolean

    /** 按 scope 查询 */
    fun findByScope(scope: String): List<WfSchema>

    /** 按 scope + app_group 查询（PRIVATE Schema） */
    fun findByScopeAndAppGroup(scope: String, appGroup: String): List<WfSchema>

    /**
     * 列表页专用的「轻量全量查询」。
     *
     * - 直接 JPQL SELECT NEW 构造 [WfSchemaSummary]，避免加载大 TEXT 字段 schemaJson
     * - keyword 下推 SQL（schema_name/description 模糊匹配）
     * - scope 过滤下推 SQL（PLATFORM + 指定 appGroup 集合的 PRIVATE）
     * - 不做 DB 层分页：在 Service 层做 schemaType 精确过滤（逗号分隔多值，JPQL 无法准确表达）
     *   后再内存分页；轻量 DTO 即使 1000 条也仅约 200KB，无性能压力。
     *
     * @param keyword     空字符串或 null 表示不过滤
     * @param scopeFilter 0=仅 PLATFORM；1=PLATFORM + 指定 appGroup；2=scope 完全不过滤（local 免鉴权 / ADMIN）
     * @param appGroups   scopeFilter=1 时允许的 appGroup 集合（空集 → 仅 PLATFORM）
     */
    @Query(
        """
        SELECT NEW com.fluxion.admin.entity.WfSchemaSummary(
            s.id, s.schemaName, s.schemaType, s.schemaFormat, s.description,
            s.frozen, s.scope, s.appGroup, s.createdAt, s.updatedAt
        )
        FROM WfSchema s
        WHERE (
            (:scopeFilter = 2)
            OR (:scopeFilter = 0 AND s.scope = 'PLATFORM')
            OR (:scopeFilter = 1 AND (s.scope = 'PLATFORM' OR (s.scope = 'PRIVATE' AND s.appGroup IN :appGroups)))
        )
          AND (:keyword IS NULL OR :keyword = ''
               OR LOWER(s.schemaName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(COALESCE(s.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY s.updatedAt DESC
        """
    )
    fun listSummaries(
        @Param("keyword") keyword: String?,
        @Param("scopeFilter") scopeFilter: Int,
        @Param("appGroups") appGroups: Collection<String>
    ): List<WfSchemaSummary>
}
