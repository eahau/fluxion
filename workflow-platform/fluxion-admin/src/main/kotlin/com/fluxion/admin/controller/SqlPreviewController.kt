package com.fluxion.admin.controller

import com.fluxion.builtin.db.DataSourceProvider
import com.fluxion.builtin.db.sql.NamedParameterSql
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * SQL 预览执行接口。
 *
 * 仅允许执行 SELECT 语句，用于工作流设计器中手写 SQL 节点的前端预览，
 * 不触发任何 SideEffect 或工作流执行。
 */
@RestController
@RequestMapping("/api/admin/schemas")
class SqlPreviewController(private val dataSourceProvider: DataSourceProvider) {

    data class SqlPreviewRequest(
        val sql: String,
        val params: Map<String, Any?>? = emptyMap(),
        val dataSource: String? = null
    )

    data class SqlPreviewResult(
        val columns: List<String>,
        val rows: List<Map<String, Any?>>
    )

    @PostMapping("/sql-preview")
    fun preview(@RequestBody request: SqlPreviewRequest): SqlPreviewResult {
        val sql = request.sql.trim()
        validateReadOnly(sql)
        val previewSql = ensureLimit(sql, PREVIEW_LIMIT)

        val renderedSql = NamedParameterSql.renderTemplate(previewSql, request.params ?: emptyMap())
        val parsed = NamedParameterSql.parse(renderedSql)
        val bindParams = request.params ?: emptyMap()
        val values = NamedParameterSql.bindValues(parsed.paramNames, bindParams)

        val dataSource = dataSourceProvider.getDataSource(request.dataSource ?: "default")
        return dataSource.connection.use { conn ->
            conn.prepareStatement(parsed.positionalSql).use { stmt ->
                values.forEachIndexed { idx, value ->
                    NamedParameterSql.setParameter(stmt, idx + 1, value)
                }
                stmt.maxRows = PREVIEW_LIMIT
                stmt.executeQuery().use { rs ->
                    val meta = rs.metaData
                    val columns = (1..meta.columnCount).map { meta.getColumnLabel(it) }
                    val rows = mutableListOf<Map<String, Any?>>()
                    while (rs.next() && rows.size < PREVIEW_LIMIT) {
                        val row = LinkedHashMap<String, Any?>()
                        for (i in 1..meta.columnCount) {
                            row[columns[i - 1]] = rs.getObject(i)
                        }
                        rows.add(row)
                    }
                    SqlPreviewResult(columns, rows)
                }
            }
        }
    }

    /**
     * 安全检查：仅允许 SELECT 语句，禁止 DML/DDL 以及多语句。
     */
    private fun validateReadOnly(sql: String) {
        val withoutComments = sql.replace(Regex("--[^\\n]*|/\\*[\\s\\S]*?\\*/"), " ")
        val statements = withoutComments.split(';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        check(statements.isNotEmpty()) { "SQL 不能为空" }
        statements.forEach { stmt ->
            val upper = stmt.uppercase()
            require(upper.startsWith("SELECT")) { "SQL 预览仅允许 SELECT 语句: $stmt" }
            require(!FORBIDDEN_KEYWORDS.containsMatchIn(upper)) {
                "SQL 预览禁止 DML/DDL 关键字: $stmt"
            }
        }
    }

    /**
     * 如果原始 SQL 没有 LIMIT，则追加一个安全上限。
     * 注意：此实现较简单，复杂子查询中的 LIMIT 不做改写。
     */
    private fun ensureLimit(sql: String, maxRows: Int): String {
        val upper = sql.uppercase()
        return if (!upper.contains(" LIMIT ")) {
            "$sql LIMIT $maxRows"
        } else sql
    }

    companion object {
        private const val PREVIEW_LIMIT = 100
        private val FORBIDDEN_KEYWORDS = Regex(
            "\\b(INSERT|UPDATE|DELETE|MERGE|UPSERT|REPLACE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|EXEC|EXECUTE|CALL)\\b"
        )
    }
}
