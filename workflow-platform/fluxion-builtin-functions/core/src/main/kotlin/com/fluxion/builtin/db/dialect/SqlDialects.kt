package com.fluxion.builtin.db.dialect

/**
 * MySQL 方言实现。
 *
 * - 标识符转义：反引号 `name`
 * - LIMIT 语法：LIMIT n
 * - 分页：LIMIT n OFFSET m
 */
object MySqlDialect : SqlDialect {

    override fun escapeIdentifier(name: String): String {
        require(name.isNotBlank()) { "identifier must not be blank" }
        require(!name.contains('`')) { "identifier contains invalid character: `$name`" }
        return "`$name`"
    }

    override fun appendLimit(sql: StringBuilder, limit: Int) {
        sql.append(" LIMIT ").append(limit)
    }

    override fun buildPageSql(sql: StringBuilder, offset: Int, limit: Int) {
        sql.append(" LIMIT ").append(limit)
        if (offset > 0) {
            sql.append(" OFFSET ").append(offset)
        }
    }
}

/**
 * PostgreSQL 方言实现。
 *
 * - 标识符转义：双引号 "name"
 * - LIMIT 语法：LIMIT n
 * - 分页：LIMIT n OFFSET m
 */
object PostgreSqlDialect : SqlDialect {

    override fun escapeIdentifier(name: String): String {
        require(name.isNotBlank()) { "identifier must not be blank" }
        require(!name.contains('"')) { "identifier contains invalid character: \"$name\"" }
        return "\"$name\""
    }

    override fun appendLimit(sql: StringBuilder, limit: Int) {
        sql.append(" LIMIT ").append(limit)
    }

    override fun buildPageSql(sql: StringBuilder, offset: Int, limit: Int) {
        sql.append(" LIMIT ").append(limit)
        if (offset > 0) {
            sql.append(" OFFSET ").append(offset)
        }
    }
}

/**
 * Oracle 方言实现（12c+）。
 *
 * - 标识符转义：双引号 "name"
 * - LIMIT 语法：FETCH FIRST n ROWS ONLY
 * - 分页：OFFSET m ROWS FETCH NEXT n ROWS ONLY
 */
object OracleDialect : SqlDialect {

    override fun escapeIdentifier(name: String): String {
        require(name.isNotBlank()) { "identifier must not be blank" }
        require(!name.contains('"')) { "identifier contains invalid character: \"$name\"" }
        return "\"$name\""
    }

    override fun appendLimit(sql: StringBuilder, limit: Int) {
        sql.append(" FETCH FIRST ").append(limit).append(" ROWS ONLY")
    }

    override fun buildPageSql(sql: StringBuilder, offset: Int, limit: Int) {
        if (offset > 0) {
            sql.append(" OFFSET ").append(offset).append(" ROWS")
        }
        sql.append(" FETCH NEXT ").append(limit).append(" ROWS ONLY")
    }
}

/**
 * SQL Server 方言实现。
 *
 * - 标识符转义：方括号 [name]
 * - LIMIT 语法：TOP n（在 SELECT 后）
 * - 分页：OFFSET m ROWS FETCH NEXT n ROWS ONLY（需要 ORDER BY）
 */
object SqlServerDialect : SqlDialect {

    override fun escapeIdentifier(name: String): String {
        require(name.isNotBlank()) { "identifier must not be blank" }
        require(!name.contains(']')) { "identifier contains invalid character: `$name`" }
        return "[$name]"
    }

    override fun appendLimit(sql: StringBuilder, limit: Int) {
        // SQL Server 需要 ORDER BY 才能使用 OFFSET/FETCH
        // 这里假设已有 ORDER BY，如果没有需要调用方处理
        sql.append(" OFFSET 0 ROWS FETCH NEXT ").append(limit).append(" ROWS ONLY")
    }

    override fun buildPageSql(sql: StringBuilder, offset: Int, limit: Int) {
        sql.append(" OFFSET ").append(offset).append(" ROWS FETCH NEXT ").append(limit).append(" ROWS ONLY")
    }
}

/**
 * 方言工厂，根据数据库类型创建对应的方言实例。
 */
object SqlDialectFactory {

    /**
     * 根据数据库产品名称创建方言。
     *
     * @param productName DatabaseMetaData.getDatabaseProductName() 的返回值
     */
    fun fromProductName(productName: String): SqlDialect {
        val lower = productName.lowercase()
        return when {
            lower.contains("mysql") || lower.contains("mariadb") -> MySqlDialect
            lower.contains("postgresql") || lower.contains("postgres") -> PostgreSqlDialect
            lower.contains("oracle") -> OracleDialect
            lower.contains("sql server") || lower.contains("microsoft") -> SqlServerDialect
            else -> PostgreSqlDialect  // 默认使用 PostgreSQL 风格（标准 SQL）
        }
    }

    /**
     * 根据 JDBC URL 创建方言。
     *
     * @param jdbcUrl JDBC 连接 URL
     */
    fun fromJdbcUrl(jdbcUrl: String): SqlDialect {
        val lower = jdbcUrl.lowercase()
        return when {
            lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:") -> MySqlDialect
            lower.startsWith("jdbc:postgresql:") -> PostgreSqlDialect
            lower.startsWith("jdbc:oracle:") -> OracleDialect
            lower.startsWith("jdbc:sqlserver:") -> SqlServerDialect
            else -> PostgreSqlDialect
        }
    }
}
