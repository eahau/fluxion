package com.fluxion.builtin.db.function

import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.uncheckedCast
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

/** DB 查询默认返回行数上限；<=0 表示不限制。 */
const val DEFAULT_DB_QUERY_LIMIT = 1000

/**
 * 从 NodeInput 中提取绑定参数，移除所有控制型字段（这些字段不应作为 :paramName 绑定值）。
 */
fun bindingParams(input: NodeInput): Map<String, Any?> = mergeParams(input).apply {
    remove("sql")
    remove("dataSource")
    remove("resultType")
    remove("compensateFunctionRef")
    remove("compensateSql")
    remove("operation")
    // 展开嵌套的 params 到顶层，支持 { params: { id: 1 } } → { id: 1 }
    remove("params").uncheckedCast<Map<String, Any?>>()?.let { putAll(it) }
}

/**
 * 合并 nodeParams 与 directInput 中的参数。
 */
fun mergeParams(input: NodeInput): MutableMap<String, Any?> {
    val params = mutableMapOf<String, Any?>()
    input.nodeParams.let { params.putAll(it) }
    input.directInput.uncheckedCast<Map<String, Any?>>()?.let { params.putAll(it) }
    return params
}

/**
 * 将 ResultSet 当前行提取为 Map（列标签 → 值）。
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
 * 根据 resultType 提取查询结果。
 */
fun ResultSet.fetchResult(resultType: String): Any? = when (resultType) {
    "one" -> if (next()) extractRow(metaData.columnCount) else null
    "count" -> if (next()) getLong(1) else 0L
    else -> mutableListOf<Map<String, Any?>>().also { rows ->
        while (next()) rows.add(extractRow(metaData.columnCount))
    }
}

/**
 * 读取自增主键；无自增键时返回影响行数。
 */
fun PreparedStatement.generatedKeyOrAffected(affected: Int): Any {
    val key = generatedKeys.use { rs -> if (rs.next()) rs.getObject(1) else null }
    return if (key is Number) key.toLong() else affected
}
