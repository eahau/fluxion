package com.fluxion.builtin.db.sql

import com.github.benmanes.caffeine.cache.Caffeine
import java.sql.PreparedStatement
import java.sql.Statement

/**
 * Ahead-of-time SQL compiler + runtime cache used by `builtin:dbExecute`.
 *
 * The goal is to pay the cost of SQL safety checks, template-token parsing,
 * and parameter-extraction exactly **once** per distinct SQL template; at
 * runtime a node just reads the cached [CompiledSql] and calls [render].
 *
 * Two compilation tiers exist:
 * 1. Template-level compilation (schema-agnostic) — used at runtime as a
 *    fallback, and for `testFunction` style dry-runs. Result is Caffeine
 *    cached by raw SQL string.
 * 2. Schema-aware compilation — done at workflow **publish** time (called via
 *    `DbExecuteWorkflowCompiler`). This validates every `:param` / `${var}`
 *    token against the list of node parameters available at that point in
 *    the DAG, and records the inferred parameter types.
 */

const val SQL_SEGMENT_LITERAL = "LITERAL"
const val SQL_SEGMENT_VARIABLE = "VARIABLE"
const val SQL_SEGMENT_PARAM = "PARAM"

/**
 * Fully compiled SQL template.
 *
 * @property originalSql raw user template (useful for error messages + fallbacks)
 * @property segments parsed tokens — literals, `${var}` template variables, `:param` bindings
 * @property paramNames named-parameter names, in positional-appearance order (with duplicates)
 * @property paramTypes inferred JSON-schema type per parameter (only populated during publish-time compile)
 * @property isQuery `true` for SELECT / WITH / EXPLAIN / SHOW / DESCRIBE
 * @property isInsert `true` for INSERT statements — triggers generated-key capture
 * @property writeTableName statically-extracted write target (INSERT/UPDATE/DELETE).
 *   `null` if the name itself came from a `${...}` template variable — in that
 *   case extraction must be repeated at runtime after template rendering.
 */
data class CompiledSql(
    val originalSql: String,
    val segments: List<SqlSegment>,
    val paramNames: List<String>,
    val paramTypes: Map<String, String?> = emptyMap(),
    val isQuery: Boolean,
    val isInsert: Boolean,
    val writeTableName: String?
) {

    /**
     * Renders to a JDBC-executable string plus an ordered list of bind values.
     *
     * - `LITERAL`  → copied verbatim.
     * - `VARIABLE` → substituted with the value from [params] and validated
     *   against [TEMPLATE_VALUE_PATTERN] so users can't smuggle raw SQL via
     *   template variables.
     * - `PARAM`    → emits `?` and appends the value to the bind list.
     */
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

    /**
     * Renders only `${var}` template substitutions, leaving `:param`
     * placeholders intact as named parameters (`:name`).
     *
     * Used for write-table extraction at runtime when the table name itself
     * was a template variable.
     */
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

/**
 * One token of a parsed SQL template. Kind is one of the `SQL_SEGMENT_*` constants;
 * value is either the literal SQL fragment, the `${var}` name, or the `:param` name.
 *
 * Uses plain strings instead of a sealed class for simple Jackson serialization
 * (publish-time compilation results are persisted as JSON alongside the workflow).
 */
data class SqlSegment(val kind: String, val value: String)

/**
 * Describes one parameter available for a `dbExecute` node during publish-time
 * schema-aware compilation.
 */
data class ParameterSchema(
    val name: String,
    val type: String? = null,
    val required: Boolean = true
)

/**
 * Entry point for both template-level and schema-aware compilation.
 */
object DbExecuteSqlCompiler {

    private const val MAX_CACHE_SIZE = 10_000L

    private val cache = Caffeine.newBuilder()
        .maximumSize(MAX_CACHE_SIZE)
        .build<String, CompiledSql>()

    /**
     * Compiles a SQL template in schema-agnostic mode (runtime fallback).
     *
     * Results are keyed by raw SQL string and cached indefinitely up to
     * [MAX_CACHE_SIZE] entries — a safe upper bound because each workflow
     * usually only has a handful of distinct dbExecute templates.
     */
    fun compile(sql: String): CompiledSql {
        require(sql.isNotBlank()) { "nodeParams.sql is required for builtin:dbExecute" }
        return cache.get(sql) { doCompile(it, emptyMap()) }
            ?: throw IllegalStateException("Failed to compile SQL: `$sql")
    }

    /**
     * Compiles a SQL template with node-parameter validation (publish-time).
     *
     * Validates that every `:param` and `${var}` reference has a corresponding
     * entry in [availableParameters]; additionally template variables are
     * type-checked to `string` (since they expand to identifiers).
     *
     * Results are NOT cached — different nodes often have identical SQL but
     * different available parameters (the whole point of per-node compilation).
     */
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
                        // Template variables expand to identifiers — only string
                        // values are safe to render. Any other type is likely a
                        // user mistake (should have used :param binding instead).
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
        // If the table name itself comes from a `${var}` template we can't know
        // it statically; leave it null so DbExecuteFunction re-extracts after
        // template rendering at runtime.
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

// ———  SQL parsing / safety helpers  ———

/**
 * Regex for valid rendered values of `${var}` template substitutions — only
 * plain identifiers allowed (prevents SQL injection through identifier holes).
 */
private val TEMPLATE_VALUE_PATTERN = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")

/**
 * DDL / admin keywords that dbExecute is never allowed to execute.
 *
 * Case-insensitive, whole-word match (so `DROPped_table` as an identifier
 * doesn't falsely trigger). LOAD DATA is included because it reads external
 * files server-side. MERGE/UPSERT are excluded from the safe list intentionally
 * because they behave wildly differently across dialects.
 */
private val FORBIDDEN_DDL_KEYWORDS = Regex(
    "\\b(DROP|TRUNCATE|ALTER|CREATE|RENAME|GRANT|REVOKE|CALL|EXEC|EXECUTE|MERGE|UPSERT|LOAD\\s+DATA)\\b",
    RegexOption.IGNORE_CASE
)

/**
 * Matches `-- line` and block comments so they can be stripped before
 * safety scanning — otherwise a user could hide DROP inside a comment to
 * bypass the keyword check and then smuggle it in via variable substitution.
 */
private val SQL_COMMENT_REGEX = Regex("""--[^\n]*|/\*[\s\S]*?\*/""")

/**
 * Returns true for read-only-style SQL prefixes.
 */
internal fun isQuerySql(sql: String): Boolean {
    val upper = sql.trim().uppercase()
    return upper.startsWith("SELECT") ||
        upper.startsWith("WITH") ||
        upper.startsWith("EXPLAIN") ||
        upper.startsWith("SHOW") ||
        upper.startsWith("DESCRIBE") ||
        upper.startsWith("DESC ")
}

/**
 * Runs the static safety scans against a raw SQL template (before any
 * variable expansion):
 * 1. Strip comments so keyword scans can't be bypassed.
 * 2. Reject multi-statement SQL (semicolon splitting).
 * 3. Reject any DDL/admin keywords.
 *
 * This is a defense-in-depth layer; parameterization and identifier
 * validation are the real security mechanisms.
 */
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

/**
 * Tokenizes a SQL template into [SqlSegment] entries — literal text,
 * `:param` named bindings, or `${var}` template variables.
 */
internal fun parseSegments(sql: String): List<SqlSegment> {
    val segments = mutableListOf<SqlSegment>()
    val literal = StringBuilder()
    var i = 0
    while (i < sql.length) {
        when {
            // `:param` — colon followed by a valid identifier start char
            sql[i] == ':' && i + 1 < sql.length && isIdentifierStart(sql[i + 1]) -> {
                if (literal.isNotEmpty()) {
                    segments.add(SqlSegment(SQL_SEGMENT_LITERAL, literal.toString()))
                    literal.clear()
                }
                val name = parseIdentifier(sql, i + 1)
                segments.add(SqlSegment(SQL_SEGMENT_PARAM, name))
                i += 1 + name.length
            }
            // `${var}` — template variable block
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

/**
 * Heuristically extracts the write-target table name for INSERT/UPDATE/DELETE.
 *
 * Returns `"unknown"` when parsing is defeated by syntax complexity (CTEs,
 * subqueries, etc.) so SideEffect recording still has *some* label rather
 * than null — operators and auditors use this for readability, not security.
 */
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

/**
 * Extension on [javax.sql.DataSource] that renders a [CompiledSql] into a
 * positional PreparedStatement, binds parameters, and executes [block]
 * within the `use` scope (ensures connection + statement are released).
 */
internal inline fun <R> javax.sql.DataSource.executeCompiled(
    compiled: CompiledSql,
    params: Map<String, Any?>,
    generatedKeys: Boolean = false,
    block: (PreparedStatement) -> R
): R {
    val (positionalSql, values) = compiled.render(params)
    val flag = if (generatedKeys) Statement.RETURN_GENERATED_KEYS else Statement.NO_GENERATED_KEYS
    connection.use { conn ->
        conn.prepareStatement(positionalSql, flag).use { stmt ->
            values.forEachIndexed { idx, value -> NamedParameterSql.setParameter(stmt, idx + 1, value) }
            return block(stmt)
        }
    }
}
