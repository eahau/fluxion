package com.fluxion.builtin.db.sql

/**
 * 命名参数 SQL 解析器（零 Spring，替代 NamedParameterJdbcTemplate 的最小实现）。
 *
 * 支持两种占位符：
 * - `:paramName`：命名参数，执行时按顺序替换为 `?` 并绑定值；
 * - `${varName}`：模板变量，执行前渲染为合法 SQL 标识符（如表名/列名）。
 */
object NamedParameterSql {

    private val PARAM_PATTERN = Regex(":([a-zA-Z_][a-zA-Z0-9_]*)")
    private val TEMPLATE_PATTERN = Regex("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}")
    private val SAFE_IDENTIFIER = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")

    data class Parsed(
        val positionalSql: String,
        val paramNames: List<String>
    )

    fun parse(sql: String): Parsed {
        val names = mutableListOf<String>()
        val positionalSql = PARAM_PATTERN.replace(sql) { match ->
            names.add(match.groupValues[1])
            "?"
        }
        return Parsed(positionalSql, names)
    }

    /**
     * 渲染 SQL 模板，替换 `${varName}` 为参数值。
     * 仅用于表名/列名等标识符场景，渲染后的值会被 [escapeIdentifier] 或直接拼接。
     */
    fun renderTemplate(sql: String, params: Map<String, Any?>): String {
        return TEMPLATE_PATTERN.replace(sql) { match ->
            val key = match.groupValues[1]
            val value = params[key] ?: throw IllegalArgumentException("Template variable not found: $key")
            val rendered = value.toString()
            require(SAFE_IDENTIFIER.matches(rendered)) {
                "Template value must be a valid identifier, got: $rendered"
            }
            rendered
        }
    }

    fun bindValues(paramNames: List<String>, params: Map<String, Any?>): List<Any?> =
        paramNames.map { params[it] }

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
