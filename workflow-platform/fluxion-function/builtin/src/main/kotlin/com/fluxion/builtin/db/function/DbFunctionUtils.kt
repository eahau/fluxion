package com.fluxion.builtin.db.function

import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.uncheckedCast
import java.sql.PreparedStatement
import java.sql.ResultSet

/** Default row-cap for unbounded SELECT queries — 0 disables the limit entirely. */
const val DEFAULT_DB_QUERY_LIMIT = 1000

/**
 * Extracts just the user's bind-parameters from a [NodeInput], stripping out
 * all control-plane keys (sql, dataSource, resultType, operation, compensation
 * metadata) that must NOT be passed as JDBC bind values.
 *
 * Additionally flattens the nested `params` map (if present) so users can
 * write either `{ id: 1 }` or `{ params: { id: 1 } }` in node config.
 */
fun bindingParams(input: NodeInput): Map<String, Any?> = mergeParams(input).apply {
    remove("sql")
    remove("dataSource")
    remove("resultType")
    remove("compensateFunctionRef")
    remove("compensateSql")
    remove("operation")
    // Flatten nested params to top level so users can group them in UI
    remove("params").uncheckedCast<Map<String, Any?>>()?.let { putAll(it) }
}

/**
 * Merges static node-level params with direct-input (dynamic) params, with
 * direct-input taking precedence on key collisions — upstream node output
 * overrides static config.
 */
fun mergeParams(input: NodeInput): MutableMap<String, Any?> {
    val params = mutableMapOf<String, Any?>()
    input.nodeParams.let { params.putAll(it) }
    input.directInput.uncheckedCast<Map<String, Any?>>()?.let { params.putAll(it) }
    return params
}

/**
 * Extracts the current row of a ResultSet as a column-label → value Map.
 *
 * Uses `getColumnLabel` instead of `getColumnName` so aliased columns
 * (e.g. `SELECT count(*) AS total`) come back with the expected key.
 */
fun ResultSet.extractRow(cols: Int): Map<String, Any?> {
    val row = LinkedHashMap<String, Any?>()
    val meta = metaData
    for (i in 1..cols) {
        row[meta.getColumnLabel(i)] = getObject(i)
    }
    return row
}

/**
 * Materializes a [ResultSet] into the caller-chosen shape based on
 * `resultType` ("one" | "count" | default=list).
 *
 * Callers are responsible for closing the ResultSet after this call
 * (typically via `.use { }` in the surrounding code).
 */
fun ResultSet.fetchResult(resultType: String): Any? = when (resultType) {
    "one" -> if (next()) extractRow(metaData.columnCount) else null
    "count" -> if (next()) getLong(1) else 0L
    else -> mutableListOf<Map<String, Any?>>().also { rows ->
        while (next()) rows.add(extractRow(metaData.columnCount))
    }
}

/**
 * Returns the auto-generated key of an INSERT statement, or falls back to the
 * number of affected rows when the driver reports no generated keys.
 *
 * The result type is unified to [Long] so downstream code has a consistent
 * numeric return for both `INSERT ... RETURNING id` and plain `UPDATE` counts.
 */
fun PreparedStatement.generatedKeyOrAffected(affected: Int): Any {
    val key = generatedKeys.use { rs -> if (rs.next()) rs.getObject(1) else null }
    return if (key is Number) key.toLong() else affected
}
