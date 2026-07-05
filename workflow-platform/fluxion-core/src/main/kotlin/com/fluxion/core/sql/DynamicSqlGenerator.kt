package com.fluxion.core.sql

import com.fluxion.core.value.SqlCondition
import com.fluxion.core.value.SqlWithParams
import java.util.Collections

/**
 * SQL 条件构建器。
 *
 * 完整操作符集合，防 SQL 注入：所有值通过 JDBC PreparedStatement 参数化，
 * 表名/字段名经 [validateIdentifier] 白名单校验。
 *
 * 配合 [SqlCondition] / [SqlWithParams] 使用，支持单条件、组合条件、IN/NOT IN/BETWEEN 等。
 */
class DynamicSqlGenerator {

    /** SQL 操作符枚举 */
    enum class Operator {
        EQ,          // field = ?
        NEQ,         // field != ?
        GT,          // field > ?
        GTE,         // field >= ?
        LT,          // field < ?
        LTE,         // field <= ?
        IN,          // field IN (?, ?, ?)
        NOT_IN,      // field NOT IN (?, ?, ?)
        LIKE,        // field LIKE ?
        LIKE_PREFIX, // field LIKE '?%'
        LIKE_SUFFIX, // field LIKE '%?'
        IS_NULL,     // field IS NULL
        IS_NOT_NULL, // field IS NOT NULL
        BETWEEN,     // field BETWEEN ? AND ?
    }

    /**
     * 根据单条条件构建 SQL 片段 + 绑定参数。
     *
     * @param condition SQL 条件（字段 + 操作符 + 值）
     * @return SQL 片段与对应的 PreparedStatement 参数
     */
    fun buildCondition(condition: SqlCondition): SqlWithParams = when (condition.op) {
        Operator.EQ          -> sql("${condition.field} = ?", condition.value)
        Operator.NEQ         -> sql("${condition.field} != ?", condition.value)
        Operator.GT          -> sql("${condition.field} > ?", condition.value)
        Operator.GTE         -> sql("${condition.field} >= ?", condition.value)
        Operator.LT          -> sql("${condition.field} < ?", condition.value)
        Operator.LTE         -> sql("${condition.field} <= ?", condition.value)
        Operator.LIKE        -> sql("${condition.field} LIKE ?", condition.value)
        Operator.LIKE_PREFIX -> sql("${condition.field} LIKE ?", "${condition.value}%")
        Operator.LIKE_SUFFIX -> sql("${condition.field} LIKE ?", "%${condition.value}")
        Operator.IS_NULL     -> sqlNoParam("${condition.field} IS NULL")
        Operator.IS_NOT_NULL -> sqlNoParam("${condition.field} IS NOT NULL")
        Operator.IN          -> buildIn(condition, false)
        Operator.NOT_IN      -> buildIn(condition, true)
        Operator.BETWEEN     -> buildBetween(condition)
    }

    private fun buildIn(cond: SqlCondition, not: Boolean): SqlWithParams {
        val values = cond.value as List<*>
        val placeholders = Collections.nCopies(values.size, "?").joinToString(", ")
        val keyword = if (not) " NOT IN (" else " IN ("
        return SqlWithParams("${cond.field}$keyword$placeholders)", values.toTypedArray())
    }

    private fun buildBetween(cond: SqlCondition): SqlWithParams {
        val range = cond.value as Array<*>
        return SqlWithParams("${cond.field} BETWEEN ? AND ?", arrayOf(range[0], range[1]))
    }

    /**
     * 生成完整 SELECT 语句。
     *
     * 多条件通过 AND 连接，表名/字段名经白名单校验。
     *
     * @param table      表名（经 validateIdentifier 校验）
     * @param columns    查询列（空则 SELECT *）
     * @param conditions WHERE 条件列表
     * @return 完整 SQL + 绑定参数
     */
    fun generateSelect(table: String, columns: List<String>, conditions: List<SqlCondition>): SqlWithParams {
        validateIdentifier(table)
        columns.forEach(::validateIdentifier)

        val sql = buildString {
            append("SELECT ")
            append(if (columns.isEmpty()) "*" else columns.joinToString(", "))
            append(" FROM ").append(table)

            if (conditions.isNotEmpty()) {
                append(" WHERE ")
                val params = mutableListOf<Any?>()
                val clauses = conditions.map { cond ->
                    val built = buildCondition(cond)
                    params.addAll(built.params)
                    built.sql
                }
                append(clauses.joinToString(" AND "))
                return SqlWithParams(this.toString(), params.toTypedArray())
            }
        }
        return SqlWithParams(sql, emptyArray())
    }

    /** 防止表名/字段名注入（白名单：字母数字下划线） */
    private fun validateIdentifier(identifier: String) {
        if (!identifier.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
            throw InvalidSqlIdentifierException(identifier)
        }
    }

    private fun sql(sql: String, param: Any?): SqlWithParams = SqlWithParams(sql, arrayOf(param))

    private fun sqlNoParam(sql: String): SqlWithParams = SqlWithParams(sql, emptyArray())

    class InvalidSqlIdentifierException(identifier: String) :
        RuntimeException("Invalid SQL identifier: $identifier. Only alphanumeric and underscore allowed.")
}
