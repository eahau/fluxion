package com.fluxion.builtin.db.sql

import com.github.benmanes.caffeine.cache.Caffeine
import java.sql.PreparedStatement
import java.sql.Statement

// ═══════════════════════════════════════════════════════════════════
// DbExecute SQL 预编译器
//
// 把原本每次执行都要做的“安全校验 + 模板渲染 + 命名参数解析”提前到
// 第一次遇到该 SQL 模板时完成，后续直接复用编译结果。
//
// 支持两阶段：
// 1. 模板级预编译（不依赖 schema，用于运行时兜底和 testFunction）；
// 2. 基于上游输出 schema 的预编译（用于工作流发布时），可校验参数存在性
//    与 ${var} 标识符类型。
// ═══════════════════════════════════════════════════════════════════

const val SQL_SEGMENT_LITERAL = "LITERAL"
const val SQL_SEGMENT_VARIABLE = "VARIABLE"
const val SQL_SEGMENT_PARAM = "PARAM"

/**
 * 编译后的 SQL 模板。
 *
 * @param originalSql 原始 SQL 模板（用于日志/兜底）
 * @param segments SQL 分段：字面量、${var} 模板变量、:param 命名参数
 * @param paramNames 命名参数按出现顺序排列（含重复）
 * @param paramTypes 从上游 schema 推导出的各参数类型
 * @param isQuery 是否为查询类语句（SELECT/WITH/EXPLAIN/SHOW/DESC）
 * @param isInsert 是否为 INSERT 语句（决定是否需要返回生成键）
 * @param writeTableName 写操作表名；若表名使用了动态模板变量则为 null，需运行时重新提取
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
     * 渲染为最终可执行的 SQL（问号占位符）及绑定值列表。
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
     * 仅渲染 ${var} 模板变量，保留 :param 命名参数（供副作用表名提取等场景）。
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
 * 可序列化的 SQL 分段（Jackson 友好）。
 */
data class SqlSegment(val kind: String, val value: String)

/**
 * 参数 Schema（用于基于上游输出 schema 的校验）。
 */
data class ParameterSchema(
    val name: String,
    val type: String? = null,
    val required: Boolean = true
)

/**
 * DbExecute SQL 预编译器入口。
 */
object DbExecuteSqlCompiler {

    private const val MAX_CACHE_SIZE = 10_000L

    private val cache = Caffeine.newBuilder()
        .maximumSize(MAX_CACHE_SIZE)
        .build<String, CompiledSql>()

    /**
     * 编译 SQL 模板；相同模板首次编译时会执行安全校验，后续命中缓存。
     * 用于运行时兜底和 testFunction 等无 schema 场景。
     */
    fun compile(sql: String): CompiledSql {
        require(sql.isNotBlank()) { "nodeParams.sql is required for builtin:dbExecute" }
        return cache.get(sql) { doCompile(it, emptyMap()) }
            ?: throw IllegalStateException("Failed to compile SQL: $sql")
    }

    /**
     * 基于可用参数 schema 编译 SQL 模板。
     * 会校验每个 :param / ${var} 是否能在当前节点找到，并记录参数类型。
     * 用于工作流发布时的 schema 感知预编译（不缓存，因为不同节点可用参数不同）。
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
                                "SQL 模板变量 '\${${segment.value}}' 在当前节点可用参数中未找到"
                            )
                        if (schema.type != null && schema.type != "string") {
                            throw IllegalArgumentException(
                                "SQL 模板变量 '\${${segment.value}}' 必须映射 string 类型才能作为标识符，当前类型: ${schema.type}"
                            )
                        }
                    }
                    SQL_SEGMENT_PARAM -> {
                        val schema = availableParameters[segment.value]
                            ?: throw IllegalArgumentException(
                                "SQL 命名参数 ':${segment.value}' 在当前节点可用参数中未找到"
                            )
                        paramTypes[segment.value] = schema.type
                    }
                }
            }
        }

        val isQuery = isQuerySql(sql)
        val isInsert = !isQuery && sql.uppercase().contains("INSERT")

        val rawTableName = if (isQuery) null else extractWriteTableName(sql)
        // 若表名来自动态模板变量，则无法静态确定，留到运行时重新提取
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

// ═══════════════════════════════════════════════════════════════════
// SQL 解析与校验工具
// ═══════════════════════════════════════════════════════════════════

/**
 * 模板变量（${var}）渲染后的合法标识符正则。
 */
private val TEMPLATE_VALUE_PATTERN = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")

/**
 * 禁止通过 dbExecute 执行的 DDL / 管控类关键字。
 */
private val FORBIDDEN_DDL_KEYWORDS = Regex(
    "\\b(DROP|TRUNCATE|ALTER|CREATE|RENAME|GRANT|REVOKE|CALL|EXEC|EXECUTE|MERGE|UPSERT|LOAD\\s+DATA)\\b",
    RegexOption.IGNORE_CASE
)

/**
 * SQL 注释正则，用于安全校验时清洗注释，防止通过注释绕过关键字检测。
 */
private val SQL_COMMENT_REGEX = Regex("--[^\\n]*|/\\*[\\s\\S]*?\\*/")

/**
 * 判断 SQL 是否为查询语句。
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
 * 校验单条 SQL 是否安全（不含 DDL / 管控关键字、不含多语句）。
 * 在模板变量展开之前校验原始 SQL 文本。
 */
internal fun validateSafeSql(sql: String) {
    // 1. 去除注释后检查，防止通过注释绕过关键字检测
    val withoutComments = sql.replace(SQL_COMMENT_REGEX, " ")

    // 2. 禁止多语句（按分号分割后只允许一条非空语句）
    val statements = withoutComments.split(';').map { it.trim() }.filter { it.isNotBlank() }
    require(statements.size <= 1) {
        "dbExecute 禁止多语句执行，检测到 ${statements.size} 条语句"
    }

    // 3. 禁止 DDL / 管控关键字
    val match = FORBIDDEN_DDL_KEYWORDS.find(withoutComments)
    require(match == null) {
        "dbExecute 禁止执行 ${match!!.value.uppercase()} 操作，仅允许 SELECT/INSERT/UPDATE/DELETE"
    }
}

/**
 * 解析 SQL 模板为分段结构，识别 :param 命名参数与 ${var} 模板变量。
 */
internal fun parseSegments(sql: String): List<SqlSegment> {
    val segments = mutableListOf<SqlSegment>()
    val literal = StringBuilder()
    var i = 0
    while (i < sql.length) {
        when {
            // :param 命名参数
            sql[i] == ':' && i + 1 < sql.length && isIdentifierStart(sql[i + 1]) -> {
                if (literal.isNotEmpty()) {
                    segments.add(SqlSegment(SQL_SEGMENT_LITERAL, literal.toString()))
                    literal.clear()
                }
                val name = parseIdentifier(sql, i + 1)
                segments.add(SqlSegment(SQL_SEGMENT_PARAM, name))
                i += 1 + name.length
            }
            // ${var} 模板变量
            sql[i] == '$' && i + 1 < sql.length && sql[i + 1] == '{' -> {
                if (literal.isNotEmpty()) {
                    segments.add(SqlSegment(SQL_SEGMENT_LITERAL, literal.toString()))
                    literal.clear()
                }
                val closing = sql.indexOf('}', i + 2)
                require(closing > i + 2) { "SQL 模板变量未闭合: ${sql.substring(i)}" }
                val name = sql.substring(i + 2, closing)
                require(TEMPLATE_VALUE_PATTERN.matches(name)) {
                    "SQL 模板变量名必须是合法标识符，got: $name"
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
 * 提取写操作的目标表名。
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
 * 使用编译结果绑定参数并执行 PreparedStatement。
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
