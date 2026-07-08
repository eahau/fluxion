package com.fluxion.builtin.db.dialect

/**
 * MySQL (and MariaDB) dialect.
 *
 * - Identifier escape: backticks `` `name` ``
 * - Limit syntax: `LIMIT n`
 * - Pagination: `LIMIT n OFFSET m`
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
 * PostgreSQL dialect.
 *
 * - Identifier escape: double quotes `"name"`
 * - Limit syntax: `LIMIT n`
 * - Pagination: `LIMIT n OFFSET m`
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
 * Oracle 12c+ dialect (ANSI row-limiting syntax).
 *
 * - Identifier escape: double quotes `"name"`
 * - Limit syntax: `FETCH FIRST n ROWS ONLY`
 * - Pagination: `OFFSET m ROWS FETCH NEXT n ROWS ONLY`
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
 * Microsoft SQL Server dialect.
 *
 * - Identifier escape: square brackets `[name]`
 * - Limit syntax: synthetic `OFFSET 0 ROWS FETCH NEXT n ROWS ONLY`
 *   (calling SQL MUST contain an ORDER BY clause otherwise MS SQL throws)
 * - Pagination: `OFFSET m ROWS FETCH NEXT n ROWS ONLY`
 */
object SqlServerDialect : SqlDialect {

    override fun escapeIdentifier(name: String): String {
        require(name.isNotBlank()) { "identifier must not be blank" }
        require(!name.contains(']')) { "identifier contains invalid character: `$name`" }
        return "[$name]"
    }

    override fun appendLimit(sql: StringBuilder, limit: Int) {
        // OFFSET/FETCH is mandatory instead of TOP so the same code path works
        // for both simple limits and pagination; callers must supply ORDER BY.
        sql.append(" OFFSET 0 ROWS FETCH NEXT ").append(limit).append(" ROWS ONLY")
    }

    override fun buildPageSql(sql: StringBuilder, offset: Int, limit: Int) {
        sql.append(" OFFSET ").append(offset).append(" ROWS FETCH NEXT ").append(limit).append(" ROWS ONLY")
    }
}

/**
 * Factory for creating [SqlDialect] instances from JDBC signals.
 *
 * Falls back to [PostgreSqlDialect] when the vendor cannot be identified,
 * because its LIMIT/OFFSET and quoting is closest to standard SQL.
 */
object SqlDialectFactory {

    /**
     * Creates a dialect from `DatabaseMetaData.getDatabaseProductName()`.
     */
    fun fromProductName(productName: String): SqlDialect {
        val lower = productName.lowercase()
        return when {
            lower.contains("mysql") || lower.contains("mariadb") -> MySqlDialect
            lower.contains("postgresql") || lower.contains("postgres") -> PostgreSqlDialect
            lower.contains("oracle") -> OracleDialect
            lower.contains("sql server") || lower.contains("microsoft") -> SqlServerDialect
            else -> PostgreSqlDialect
        }
    }

    /**
     * Creates a dialect from the raw JDBC connection URL.
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
