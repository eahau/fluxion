package com.fluxion.core.value

/**
 * Return values and shared value types used across the Fluxion workflow engine.
 *
 * This file aggregates four related concepts:
 *  - `SqlCondition` / `SqlWithParams` for safe dynamic SQL generation.
 *  - `RerunResult` for the idempotent re-execution query API.
 */

import com.fluxion.core.sql.DynamicSqlGenerator

/**
 * A single WHERE-clause predicate consumed by [DynamicSqlGenerator].
 *
 * The generator validates [field] against a strict identifier regex to prevent
 * SQL injection; values are always passed as JDBC prepared-statement parameters
 * and never interpolated into the SQL string.
 *
 * @property field  column name (validated identifier).
 * @property op     comparison operator.
 * @property value  right-hand side.  The exact shape depends on [op]:
 *                  - EQ/NEQ/GT/... : single scalar
 *                  - IN/NOT_IN        : `List<?>`
 *                  - BETWEEN          : two-element array `[lower, upper]`
 *                  - IS_NULL/IS_NOT_NULL : ignored (pass `null`)
 */
data class SqlCondition(
    val field: String,
    val op: DynamicSqlGenerator.Operator,
    val value: Any?
)

/**
 * A compiled SQL fragment paired with its ordered bind-parameter array.
 *
 * The layout is intentional: callers can destructure `(sql, params)` and pass
 * them directly to `JdbcTemplate.query(sql, params)` or any equivalent API.
 *
 * @property sql    final SQL text; placeholders are always `?`.
 * @property params ordered array of bind values; typed as nullable because
 *                  JDBC accepts null parameters.
 */
data class SqlWithParams(
    val sql: String,
    val params: Array<Any?>
) {
    companion object {
        /** Vararg factory (hides the array allocation at call-site). */
        @JvmStatic
        fun of(sql: String, vararg params: Any?): SqlWithParams =
            SqlWithParams(sql, arrayOf(*params))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SqlWithParams) return false
        return sql == other.sql && params.contentEquals(other.params)
    }

    override fun hashCode(): Int = 31 * sql.hashCode() + params.contentHashCode()
}

/**
 * Result of a rerun / replay query against the idempotency store.
 *
 * Produced by [com.fluxion.core.engine.WorkflowEngine.rerun] for manual
 * troubleshooting and audit.
 */
data class RerunResult(
    /** The terminal value that was returned to the caller on the original run. */
    val finalOutput: Any?,
    /** The final execution state (inputs + node outputs). */
    val finalState: com.fluxion.core.model.ImmutableExecutionState,
    /** Chronological list of node records captured during the original run. */
    val trace: List<NodeExecutionRecord>
)
