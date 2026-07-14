package com.fluxion.builtin.db.sql

/**
 * Minimal implementation of named-parameter SQL parsing — a tiny stand-in for
 * Spring's `NamedParameterJdbcTemplate` so the builtin DB functions work in
 * non-Spring deployments.
 *
 * Two placeholder styles are supported:
 * - `:paramName`   — bound parameter, replaced with `?` at render time and
 *                    safely typed via [setParameter] on the PreparedStatement.
 * - `${varName}`   — template variable, substituted *before* prepare() with a
 *                    regex-validated identifier string. Intended ONLY for table
 *                    names / column names (things JDBC placeholders cannot do).
 *
 * Note: `DbExecuteSqlCompiler` is the modern path and replaces this class for
 * `builtin:dbExecute`. This class is retained for internal callers and for
 * backwards compatibility with lower-level helpers.
 */
object NamedParameterSql {

    private val PARAM_PATTERN = Regex(":([a-zA-Z_][a-zA-Z0-9_]*)")
    private val TEMPLATE_PATTERN = Regex("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}")
    private val SAFE_IDENTIFIER = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")

    /**
     * Result of [parse]: positional (JDBC `?`) SQL plus ordered parameter names
     * so callers can bind values by index.
     */
    data class Parsed(
        val positionalSql: String,
        val paramNames: List<String>
    )

    /**
     * Converts a named-parameter SQL string into positional `?` SQL.
     *
     * Template variables (`${...}`) are NOT substituted here — the caller must
     * run [renderTemplate] first if template substitution is required.
     */
    fun parse(sql: String): Parsed {
        val names = mutableListOf<String>()
        val positionalSql = PARAM_PATTERN.replace(sql) { match ->
            names.add(match.groupValues[1])
            "?"
        }
        return Parsed(positionalSql, names)
    }

    /**
     * Substitutes `${varName}` template variables in a SQL string.
     *
     * Each rendered value is validated against [SAFE_IDENTIFIER] to prevent
     * SQL-injection via identifiers. Use ONLY for table/column names — for
     * WHERE clause values always prefer the `:paramName` binding path.
     */
    fun renderTemplate(sql: String, params: Map<String, Any?>): String {
        return TEMPLATE_PATTERN.replace(sql) { match ->
            val key = match.groupValues[1]
            val value = params[key] ?: throw IllegalArgumentException("Template variable not found: `$key")
            val rendered = value.toString()
            require(SAFE_IDENTIFIER.matches(rendered)) {
                "Template value must be a valid identifier, got: $rendered"
            }
            rendered
        }
    }

    /**
     * Returns a list of values aligned with [Parsed.paramNames] order.
     */
    fun bindValues(paramNames: List<String>, params: Map<String, Any?>): List<Any?> =
        paramNames.map { params[it] }

    /**
     * Sets a single JDBC parameter on a [java.sql.PreparedStatement] with
     * type-aware setXxx calls (avoids `setObject` pathologies on some JDBC
     * drivers, especially around null handling and temporal types).
     */
    fun setParameter(stmt: java.sql.PreparedStatement, index: Int, value: Any?) {
        when (value) {
            null -> stmt.setObject(index, null)
            is String -> stmt.setString(index, value)
            is Int -> stmt.setInt(index, value)
            is Long -> stmt.setLong(index, value)
            is Double -> stmt.setDouble(index, value)
            is Float -> stmt.setFloat(index, value)
            is Boolean -> stmt.setBoolean(index, value)
            is java.sql.Date -> stmt.setDate(index, value)
            is java.sql.Timestamp -> stmt.setTimestamp(index, value)
            is java.time.LocalDate -> stmt.setDate(index, java.sql.Date.valueOf(value))
            is java.time.LocalDateTime -> stmt.setTimestamp(index, java.sql.Timestamp.valueOf(value))
            else -> stmt.setObject(index, value)
        }
    }
}
