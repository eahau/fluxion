package com.fluxion.admin.repository

import com.fluxion.admin.entity.WfSchema
import com.fluxion.admin.entity.WfSchemaSummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

/**
 * Spring Data JPA repository for [WfSchema] entities.
 *
 * Distinguishes itself from a vanilla CRUD repository by exposing `listSummaries`,
 * a carefully hand-written JPQL `SELECT NEW` query that projects a [WfSchemaSummary]
 * without loading the heavy `schemaJson` TEXT column — critical for list-page TTFB.
 */
interface WfSchemaRepository : JpaRepository<WfSchema, Long> {

    /** Load a schema by the exact reference name used in `$ref: "schema:<name>"`. */
    fun findBySchemaName(schemaName: String): Optional<WfSchema>

    /** Existence check used before CREATE to guarantee unique schema names. */
    fun existsBySchemaName(schemaName: String): Boolean

    /** Return all schemas scoped to a specific visibility (PLATFORM / PRIVATE). */
    fun findByScope(scope: String): List<WfSchema>

    /** Return PRIVATE schemas for a given tenant app group. */
    fun findByScopeAndAppGroup(scope: String, appGroup: String): List<WfSchema>

    /**
     * Lightweight list query used by the schema list page.
     *
     * Three-stage scope filter encoded as `scopeFilter` int to avoid branching in JPQL:
     *   0 → PLATFORM only (no group grants)
     *   1 → PLATFORM ∪ PRIVATE where appGroup ∈ :appGroups
     *   2 → every schema (admin / local-auth bypass)
     *
     * Keyword filters by schema name and description (case-insensitive LIKE).
     * Pagination/schemaType split are applied in WfSchemaService because the comma-separated
     * schemaType column cannot be correctly matched with standard JPQL.
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
