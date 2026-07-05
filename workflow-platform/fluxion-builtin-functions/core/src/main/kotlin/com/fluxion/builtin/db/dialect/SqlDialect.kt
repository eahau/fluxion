package com.fluxion.builtin.db.dialect

/**
 * SQL 方言接口，用于支持多数据库。
 *
 * 不同的数据库有不同的 SQL 语法差异，如标识符转义、LIMIT 语法等。
 * 通过方言接口抽象这些差异，使数据库相关函数可以支持多种数据库。
 */
interface SqlDialect {

    /**
     * 转义标识符（表名、列名）。
     *
     * - MySQL: `table_name`
     * - PostgreSQL: "table_name"
     * - Oracle: "table_name"
     * - SQL Server: [table_name]
     */
    fun escapeIdentifier(name: String): String

    /**
     * 追加 LIMIT 子句。
     *
     * - MySQL/PostgreSQL: LIMIT n
     * - Oracle 12c+: FETCH FIRST n ROWS ONLY
     * - SQL Server: 在 SELECT 后加 TOP n
     */
    fun appendLimit(sql: StringBuilder, limit: Int)

    /**
     * 获取当前分页 SQL 片段（可选，用于复杂分页场景）。
     */
    fun buildPageSql(sql: StringBuilder, offset: Int, limit: Int) {
        // 默认实现：使用 LIMIT + OFFSET
        sql.append(" LIMIT ").append(limit)
        if (offset > 0) {
            sql.append(" OFFSET ").append(offset)
        }
    }
}
