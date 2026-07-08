package com.fluxion.builtin.db.dialect

/**
 * SQL dialect SPI abstracting vendor-specific syntax differences.
 *
 * Databases differ in identifier quoting, row-limiting clauses and pagination
 * syntax. Dialect implementations encapsulate those differences so DB-facing
 * functions (e.g. `builtin:dbExecute`, paginated queries) stay portable.
 *
 * Concrete dialects live in [SqlDialects]; dialect selection is driven by
 * [com.fluxion.builtin.db.DialectResolver] using JDBC URL or metadata.
 */
interface SqlDialect {

    /**
     * Quotes a SQL identifier (table / column name) for the vendor.
     *
     * - MySQL: `` `table_name` ``
     * - PostgreSQL / Oracle: `"table_name"`
     * - SQL Server: `[table_name]`
     *
     * @throws IllegalArgumentException if name is blank or contains the
     *   dialect's quote character (prevents injection via escape smuggling).
     */
    fun escapeIdentifier(name: String): String

    /**
     * Appends a simple row-limiting clause to the SQL builder.
     *
     * - MySQL/PostgreSQL: `LIMIT n`
     * - Oracle 12c+: `FETCH FIRST n ROWS ONLY`
     * - SQL Server: synthetic `OFFSET 0 ROWS FETCH NEXT n ROWS ONLY`
     *   (requires ORDER BY on the calling SQL).
     */
    fun appendLimit(sql: StringBuilder, limit: Int)

    /**
     * Appends an offset + limit pagination clause.
     *
     * Default implementation uses standard `LIMIT n OFFSET m`; dialects with
     * non-standard syntax (Oracle, SQL Server) override it.
     */
    fun buildPageSql(sql: StringBuilder, offset: Int, limit: Int) {
        sql.append(" LIMIT ").append(limit)
        if (offset > 0) {
            sql.append(" OFFSET ").append(offset)
        }
    }
}
