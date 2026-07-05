package com.fluxion.core.value

import com.fluxion.core.sql.DynamicSqlGenerator

/**
 * SQL 条件参数 — DynamicSqlGenerator 的输入类型
 * 配置在 WorkflowNode.params.conditions 数组中（JSON 反序列化）
 */
data class SqlCondition(
    /** 字段名（经过 validateIdentifier 校验，防 SQL 注入） */
    val field: String,
    /** 操作符 */
    val op: DynamicSqlGenerator.Operator,
    /**
     * 比较值
     * EQ/NEQ/GT 等：任意值
     * IN/NOT_IN：List<?>
     * BETWEEN：Object[2]（范围的 lower/upper）
     * IS_NULL/IS_NOT_NULL：忽略此字段
     */
    val value: Any?
)

/**
 * SQL 语句 + 绑定参数（PreparedStatement 风格）
 * DynamicSqlGenerator 的输出，传给 JdbcTemplate
 */
data class SqlWithParams(
    /** 带 ? 占位符的 SQL 片段或完整 SQL */
    val sql: String,
    /** 对应 ? 的绑定参数（与 JdbcTemplate.query(sql, params) 顺序一致） */
    val params: Array<Any?>
) {
    companion object {
        /** 可变参数构造（避免每次 arrayOf(...)） */
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

/** JSON Schema 校验结果 — 已迁移至 [com.fluxion.schema.model.ValidationResult] */
@Deprecated("Use com.fluxion.schema.model.ValidationResult", ReplaceWith("com.fluxion.schema.model.ValidationResult"))
typealias ValidationResult = com.fluxion.schema.model.ValidationResult

/** 重放结果 */
data class RerunResult(
    val finalOutput: Any?,
    val finalState: com.fluxion.core.model.ImmutableExecutionState,
    val trace: List<NodeExecutionRecord>
)
