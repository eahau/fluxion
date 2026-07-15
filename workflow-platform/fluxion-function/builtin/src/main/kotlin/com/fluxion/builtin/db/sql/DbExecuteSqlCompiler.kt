package com.fluxion.builtin.db.sql

import com.fluxion.cache.FluxionCacheFactory
import com.github.benmanes.caffeine.cache.Cache

const val SQL_SEGMENT_LITERAL = "LITERAL"
const val SQL_SEGMENT_VARIABLE = "VARIABLE"
const val SQL_SEGMENT_PARAM = "PARAM"

data class CompiledSql(
    val originalSql: String,
    val segments: List<SqlSegment>,
    val paramNames: List<String>,
    val paramTypes: Map<String, String?> = emptyMap(),
    val isQuery: Boolean,
    val isInsert: Boolean,
    val writeTableName: String?
) {
    fun render(params: Map<String, Any?>): Pair<String, List<Any?>> {
        val sql = StringBuilder()
        val values = mutableListOf<Any?>()
        segments.forEach { segment ->
            when (segment.kind) {
                SQL_SEGMENT_LITERAL -> sql.append(segment.value)
                SQL_SEGMENT_VARIABLE -> {
                    val value = params[segment.value]
                        ?: throw IllegalArgumentException("Template variable not found: ${segment.value}")
                    val rendered = value.toString()
                    require(TEMPLATE_VALUE_PATTERN.matches(rendered)) {
                        "Template value must be a valid identifier, got: $rendered"
                    }
                    sql.append(rendered)
                }
                SQL_SEGMENT_PARAM -> {
                    sql.append("?")
                    values.add(params[segment.value])
                }
                else -> throw IllegalStateException("Unknown SQL segment kind: ${segment.kind}")
            }
        }
        return sql.toString() to values
    }

    fun renderTemplate(params: Map<String, Any?>): String {
        val sql = StringBuilder()
        segments.forEach { segment ->
            when (segment.kind) {
                SQL_SEGMENT_LITERAL -> sql.append(segment.value)
                SQL_SEGMENT_VARIABLE -> {
                    val value = params[segment.value]
                        ?: throw IllegalArgumentException("Template variable not found: ${segment.value}")
                    val rendered = value.toString()
                    require(TEMPLATE_VALUE_PATTERN.matches(rendered)) {
                        "Template value must be a valid identifier, got: $rendered"
                    }
                    sql.append(rendered)
                }
                SQL_SEGMENT_PARAM -> sql.append(":").append(segment.value)
                else -> throw IllegalStateException("Unknown SQL segment kind: ${segment.kind}")
            }
        }
        return sql.toString()
    }
}

data class SqlSegment(val kind: String, val value: String)

data class ParameterSchema(
    val name: String,
    val type: String? = null,
    val required: Boolean = true
)

object DbExecuteSqlCompiler {

    private val cache: Cache<String, CompiledSql> = FluxionCacheFactory.get("sql-compile")

    fun compile(sql: String): CompiledSql {
        require(sql.isNotBlank()) { "nodeParams.sql is required for builtin:dbExecute" }
        return cache.get(sql) { doCompile(it, emptyMap()) }
            ?: throw IllegalStateException("Failed to compile SQL: `$sql")
    }

    fun compile(sql: String, availableParameters: Map<String, ParameterSchema>): CompiledSql {
        require(sql.isNotBlank()) { "nodeParams.sql is required for builtin:dbExecute" }
        return doCompile(sql, availableParameters)
    }

    private fun doCompile(sql: String, availableParameters: Map<String, ParameterSchema>): CompiledSql {
        validateSafeSql(sql)

        val segments = parseSegments(sql)
        val paramNames = segments.filter { it.kind == SQL_SEGMENT_PARAM }.map { it.value }
        val paramTypes = mutableMapOf<String, String?>()

        if (availableParameters.isNotEmpty()) {
            segments.forEach { segment ->
                when (segment.kind) {
                    SQL_SEGMENT_VARIABLE -> {
                        val schema = availableParameters[segment.value]
                            ?: throw IllegalArgumentException(
                                "SQL template variable '\${${segment.value}}' not found in available node parameters"
                            )
                        if (schema.type != null && schema.type != "string") {
                            throw IllegalArgumentException(
                                "SQL template variable '\${${segment.value}}' must be of type 'string' to be used as identifier, actual type: ${schema.type}"
                            )
                        }
                    }
                    SQL_SEGMENT_PARAM -> {
                        val schema = availableParameters[segment.value]
                            ?: throw IllegalArgumentException(
                                "SQL named parameter ':${segment.value}' not found in available node parameters"
                            )
                        paramTypes[segment.value] = schema.type
                    }
                }
            }
        }

        val isQuery = isQuerySql(sql)
        val isInsert = !isQuery && sql.uppercase().contains("INSERT")

        val rawTableName = if (isQuery) null else extractWriteTableName(sql)
        val writeTableName = rawTableName?.takeIf { !it.contains("${'$'}{") && !it.contains("}") }

        return CompiledSql(
            originalSql = sql,
            segments = segments,
            paramNames = paramNames,
            paramTypes = paramTypes,
            isQuery = isQuery,
            isInsert = isInsert,
            writeTableName = writeTableName
        )
    }
}

private val TEMPLATE_VALUE_PATTERN = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")

private val FORBIDDEN_DDL_KEYWORDS = Regex(
    "\\b(DROP|TRUNCATE|ALTER|CREATE|RENAME|GRANT|REVOKE|CALL|EXEC|EXECUTE|MERGE|UPSERT|LOAD\\s+DATA)\\b",
    RegexOption.IGNORE_CASE
)

private val SQL_COMMENT_REGEX = Regex("""--[^\n]*|/\*[\s\S]*?\*/""")

internal fun isQuerySql(sql: String): Boolean {
    val upper = sql.trim().uppercase()
    return upper.startsWith("SELECT") ||
        upper.startsWith("WITH") ||
        upper.startsWith("EXPLAIN") ||
        upper.startsWith("SHOW") ||
        upper.startsWith("DESCRIBE") ||
        upper.startsWith("DESC ")
}

internal fun validateSafeSql(sql: String) {
    val withoutComments = sql.replace(SQL_COMMENT_REGEX, " ")

    val statements = withoutComments.split(';').map { it.trim() }.filter { it.isNotBlank() }
    require(statements.size <= 1) {
        "dbExecute prohibits multi-statement execution: found ${statements.size} statements"
    }

    val match = FORBIDDEN_DDL_KEYWORDS.find(withoutComments)
    require(match == null) {
        "dbExecute prohibits ${match!!.value.uppercase()} operations — only SELECT/INSERT/UPDATE/DELETE are allowed"
    }
}

internal fun parseSegments(sql: String): List<SqlSegment> {
    val segments = mutableListOf<SqlSegment>()
    val literal = StringBuilder()
    var i = 0
    while (i < sql.length) {
        when {
            sql[i] == ':' && i + 1 < sql.length && isIdentifierStart(sql[i + 1]) -> {
                if (literal.isNotEmpty()) {
                    segments.add(SqlSegment(SQL_SEGMENT_LITERAL, literal.toString()))
                    literal.clear()
                }
                val name = parseIdentifier(sql, i + 1)
                segments.add(SqlSegment(SQL_SEGMENT_PARAM, name))
                i += 1 + name.length
            }
            sql[i] == '$' && i + 1 < sql.length && sql[i + 1] == '{' -> {
                if (literal.isNotEmpty()) {
                    segments.add(SqlSegment(SQL_SEGMENT_LITERAL, literal.toString()))
                    literal.clear()
                }
                val closing = sql.indexOf('}', i + 2)
                require(closing > i + 2) { "SQL template variable not closed: ${sql.substring(i)}" }
                val name = sql.substring(i + 2, closing)
                require(TEMPLATE_VALUE_PATTERN.matches(name)) {
                    "SQL template variable name must be a valid identifier, got: $name"
                }
                segments.add(SqlSegment(SQL_SEGMENT_VARIABLE, name))
                i = closing + 1
            }
            else -> {
                literal.append(sql[i])
                i++
            }
        }
    }
    if (literal.isNotEmpty()) {
        segments.add(SqlSegment(SQL_SEGMENT_LITERAL, literal.toString()))
    }
    return segments
}

private fun isIdentifierStart(ch: Char): Boolean = ch.isLetter() || ch == '_'

private fun isIdentifierPart(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_'

private fun parseIdentifier(sql: String, start: Int): String {
    var i = start
    while (i < sql.length && isIdentifierPart(sql[i])) {
        i++
    }
    return sql.substring(start, i)
}

internal fun extractWriteTableName(sql: String): String {
    val upper = sql.uppercase().trim()
    return when {
        upper.contains("INSERT") -> extractTableName(sql, "INTO")
        upper.contains("UPDATE") -> extractUpdateTableName(sql)
        upper.contains("DELETE") -> extractTableName(sql, "FROM")
        else -> "unknown"
    }
}

private fun extractTableName(sql: String, keyword: String): String {
    val upper = sql.uppercase().trim()
    val idx = upper.indexOf(keyword)
    if (idx >= 0) {
        val after = sql.substring(idx + keyword.length).trim()
        val whereIdx = after.uppercase().indexOf("WHERE")
        val spaceIdx = after.indexOf(' ')
        val parenIdx = after.indexOf('(')
        var endIdx = after.length
        if (whereIdx > 0) endIdx = minOf(endIdx, whereIdx)
        if (spaceIdx > 0) endIdx = minOf(endIdx, spaceIdx)
        if (parenIdx > 0) endIdx = minOf(endIdx, parenIdx)
        return after.substring(0, endIdx).trim()
    }
    return "unknown"
}

private fun extractUpdateTableName(sql: String): String {
    val upper = sql.uppercase().trim()
    val updateIdx = upper.indexOf("UPDATE")
    if (updateIdx >= 0) {
        val afterUpdate = sql.substring(updateIdx + 6).trim()
        val setIdx = afterUpdate.uppercase().indexOf("SET")
        val endIdx = if (setIdx > 0) setIdx else afterUpdate.length
        return afterUpdate.substring(0, endIdx).trim()
    }
    return "unknown"
}

internal inline fun <R> javax.sql.DataSource.executeCompiled(
    compiled: CompiledSql,
    params: Map<String, Any?>,
    generatedKeys: Boolean = false,
    block: (java.sql.PreparedStatement) -> R
): R {
    val (positionalSql, values) = compiled.render(params)
    val flag = if (generatedKeys) java.sql.Statement.RETURN_GENERATED_KEYS else java.sql.Statement.NO_GENERATED_KEYS
    connection.use { conn ->
        conn.prepareStatement(positionalSql, flag).use { stmt ->
            values.forEachIndexed { idx, value -> NamedParameterSql.setParameter(stmt, idx + 1, value) }
            return block(stmt)
        }
    }
}