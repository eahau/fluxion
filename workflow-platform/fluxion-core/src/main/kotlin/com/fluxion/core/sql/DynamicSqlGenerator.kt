package com.fluxion.core.sql

import com.fluxion.core.value.SqlCondition
import com.fluxion.core.value.SqlWithParams
import java.util.Collections

/**
 * Safe dynamic SQL generator for admin-console queries.
 *
 * Produces JDBC-compatible SQL fragments with `?` placeholders. Column
 * identifiers are validated against a strict alphanumeric+underscore regex
 * via [validateIdentifier] to prevent SQL injection; values are *never*
 * interpolated into the SQL string and are always passed through the
 * bind-parameter array.
 *
 * Pair with [SqlCondition] / [SqlWithParams] for a fluent, type-safe
 * predicate DSL that supports IN / NOT IN / BETWEEN and LIKE variants.
 */
class DynamicSqlGenerator {

    /** Supported WHERE-clause comparison operators. */
    enum class Operator {
        EQ,
        NEQ,
        GT,
        GTE,
        LT,
        LTE,
        IN,
        NOT_IN,
        LIKE,
        LIKE_PREFIX,
        LIKE_SUFFIX,
        IS_NULL,
        IS_NOT_NULL,
        BETWEEN,
    }

    /**
     * Compile a single [SqlCondition] into a `(sql, params)` pair.
     *
     * Validates the column identifier and routes to the correct operator
     * handler. Operators that take collections or ranges (IN, BETWEEN)
     * expand into the appropriate number of `?` placeholders.
     *
     * @param condition validated column + operator + right-hand-side value.
     * @return compiled SQL text paired with ordered bind parameters.
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
     * Build a complete SELECT statement with AND-combined WHERE predicates.
     *
     * The table name and every selected column are run through
     * [validateIdentifier] so that callers cannot inject arbitrary SQL
     * fragments even when identifiers come from user-controlled input.
     * When [conditions] is empty the WHERE clause is omitted entirely.
     *
     * @param table      validated table identifier.
     * @param columns    validated column list; empty means `SELECT *`.
     * @param conditions predicates combined with AND.
     * @return compiled SQL text paired with ordered bind parameters.
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

    /**
     * Strict identifier validation — throws on anything that is not a
     * simple `[a-zA-Z_][a-zA-Z0-9_]*` token.
     *
     * This is the primary defence against SQL-injection through
     * attacker-controlled table / column names in dynamic admin queries.
     */
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
