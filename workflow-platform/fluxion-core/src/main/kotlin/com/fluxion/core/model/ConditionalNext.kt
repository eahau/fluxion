package com.fluxion.core.model

/**
 * 结构化规则项 — 用于 [ConditionalNext.rules] 与 [ConditionBranchFunction]/[FilterFunction]
 *
 * 支持两种形态：
 * - 叶子规则：field + operator 必填，用于单字段条件判断；
 * - 嵌套规则组：rules 非空，此时 field/operator 被忽略，规则组可递归包含子规则或子规则组。
 */
data class FilterRule(
    /** 字段名（支持点号嵌套路径，如 "user.address.city"）；叶子规则必填 */
    val field: String? = null,
    /** 操作符（eq / gt / gte / lt / lte / in / contains / isNull / startsWith / endsWith / regex）；旧数据兼容 neq / notIn / isNotNull；叶子规则必填 */
    val operator: String? = null,
    /** 规则值（与字段值比较的目标）；isNull 操作符无需填写 */
    val value: Any? = null,
    /** 是否对单条规则结果取反，默认 false */
    val negate: Boolean = false,
    /**
     * 本条规则/规则组与上一条规则之间的 AND/OR 逻辑（第 1 条规则忽略此字段）。
     * 当容器级 rulesGlobalLogicEnabled=true 时此字段被忽略，强制使用统一逻辑。
     * 为 null 时回退为容器级顶层 logic。
     */
    val logic: String? = null,
    /** 嵌套子规则列表；非空时表示本条为规则组而非叶子规则 */
    val rules: List<FilterRule>? = null,
    /** 规则组内部是否启用统一强制逻辑；仅当 rules 非空时生效 */
    val globalLogicEnabled: Boolean? = null,
    /** 规则组内部统一强制逻辑操作符；仅当 rules 非空且 globalLogicEnabled=true 时生效 */
    val globalLogicOperator: String? = null,
)

/**
 * 条件分支定义 — WorkflowNode.conditionalNexts 的元素
 *
 * 支持两种互斥模式：
 * - 表达式模式：[condition] 使用 JEXL/SpEL 表达式求值
 * - 结构化规则模式：[rules] 使用字段 + 操作符 + 值组合求值，[logic] 控制 and/or
 */
data class ConditionalNext(
    /** 条件为 true 时跳转的目标节点 ID */
    val target: String,
    /**
     * JEXL/SpEL 条件表达式
     * 可用变量：#output（当前节点输出）、#input（工作流原始入参）
     * 示例："#output['status'] == 'APPROVED'"
     */
    val condition: String? = null,
    /** 多字段组合逻辑："and"（默认）或 "or"；当启用 [rulesGlobalLogicEnabled] 时覆盖单条规则的 logic */
    val logic: String = "and",
    /** 是否对整体条件结果取反（表达式模式或规则组合模式均生效），默认 false */
    val negate: Boolean = false,
    /** 结构化规则列表（与 [condition] 二选一） */
    val rules: List<FilterRule>? = null,
    /** 是否启用规则级全局统一逻辑，启用后单条规则的 logic 被忽略，使用 [rulesGlobalLogicOperator] */
    val rulesGlobalLogicEnabled: Boolean = false,
    /** 规则级全局统一逻辑运算符（and/or），仅当 [rulesGlobalLogicEnabled]=true 时生效 */
    val rulesGlobalLogicOperator: String = "and",
)
