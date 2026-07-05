package com.fluxion.core.engine

import com.fluxion.core.model.FilterRule
import org.slf4j.*

/**
 * 结构化规则求值器 — 支持 字段 + 操作符 + 值 的条件匹配，以及嵌套规则组。
 *
 * 与 [ExpressionEvaluator] 互补：
 * - [RuleEvaluator] 面向低代码场景，用户只需选字段、操作符、填值；
 * - [ExpressionEvaluator] 面向高级用户，直接写 JEXL 表达式。
 *
 * 字段解析规则：
 * - 若 nodeOutput 是 Map，field 直接作为 key 取值（支持嵌套路径 "a.b.c"）；
 * - 若 nodeOutput 非 Map 且 field 为 "output" 时取 nodeOutput 本身。
 *
 * 逻辑组合规则（2026-07 增强嵌套规则组，向后兼容旧数据）：
 * 1. 每条规则/规则组可独立声明 logic（与上一条规则之间的 AND/OR），第 1 条规则忽略；
 * 2. 若容器级 rulesGlobalLogicEnabled=true，则所有规则间强制使用 rulesGlobalLogicOperator；
 * 3. 规则项可声明嵌套 rules，此时该项整体被当作子规则组递归求值；
 * 4. 以上三者均未设置时，回退使用容器顶层 logic（默认 AND）。
 */
object RuleEvaluator {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 求值单条规则
     *
     * @param field       字段名（支持点号嵌套路径，如 "user.age"、"items"）
     * @param operator    操作符（eq / gt / gte / lt / lte / in / contains / isNull / startsWith / endsWith / regex；旧数据兼容 neq / notIn / isNotNull）
     * @param ruleValue   规则值（与字段值比较的目标；isNull 操作符无需填写）
     * @param nodeOutput  当前节点输出（通常为 Map）
     * @param negate      是否对结果取反，默认 false
     * @return 规则匹配时返回 true；若 negate 为 true 则返回原结果的逻辑非
     */
    @JvmStatic
    @JvmOverloads
    fun evalRule(field: String, operator: String, ruleValue: Any?, nodeOutput: Any?, negate: Boolean = false): Boolean {
        val fieldValue = resolveFieldValue(field, nodeOutput)
        val rawResult = matchOperator(operator, fieldValue, ruleValue)
        val result = if (negate) !rawResult else rawResult
        log.debug { "RuleEvaluator: $field $operator $ruleValue (negate=$negate) → $result (fieldValue=$fieldValue)" }
        return result
    }

    // ─── Map 形式（兼容历史 / 函数节点参数）────────────────────────

    /**
     * 求值多字段组合规则（原始 Map 形式，兼容历史数据与函数节点参数）。
     *
     * 支持嵌套规则组：当某条规则项包含非空 `rules` 字段时，会递归对该子规则组求值。
     *
     * 按以下优先级确定规则间的 AND/OR：
     *  1. 若 [globalLogicEnabled] = true，使用 [globalLogicOperator]（全局统一逻辑）；
     *  2. 否则使用每条规则自身的 logic 字段（第 1 条规则忽略，第 2+ 条生效）；
     *  3. 若某条规则未声明 logic，回退为 [defaultLogic]（即容器级顶层 logic）。
     */
    @JvmStatic
    @JvmOverloads
    fun evalCompoundRules(
        rules: List<*>,
        defaultLogic: String,
        nodeOutput: Any?,
        globalLogicEnabled: Boolean? = null,
        globalLogicOperator: String? = null,
    ): Boolean = evalRulesInternal(
        rules = rules,
        defaultLogic = defaultLogic,
        nodeOutput = nodeOutput,
        globalLogicEnabled = globalLogicEnabled ?: false,
        globalLogicOperator = globalLogicOperator,
        extract = { idx, rule ->
            val m = rule as? Map<*, *> ?: return@evalRulesInternal null
            val nestedRules = m["rules"] as? List<*>
            if (nestedRules != null) {
                ExtractedNode(
                    index = idx,
                    logic = m["logic"] as? String,
                    evaluate = {
                        evalCompoundRules(
                            rules = nestedRules,
                            defaultLogic = (m["logic"] as? String) ?: defaultLogic,
                            nodeOutput = nodeOutput,
                            globalLogicEnabled = m["globalLogicEnabled"] as? Boolean ?: false,
                            globalLogicOperator = m["globalLogicOperator"] as? String,
                        )
                    },
                )
            } else {
                val field = m["field"] as? String ?: return@evalRulesInternal null
                val operator = m["operator"] as? String ?: return@evalRulesInternal null
                ExtractedNode(
                    index = idx,
                    logic = m["logic"] as? String,
                    evaluate = {
                        evalRule(
                            field = field,
                            operator = operator,
                            ruleValue = m["value"],
                            nodeOutput = nodeOutput,
                            negate = m["negate"] as? Boolean ?: false,
                        )
                    },
                )
            }
        },
    )

    // ─── 类型安全形式（FilterRule）───────────────────────────────

    /**
     * 求值多字段组合规则（类型安全形式，用于 [com.fluxion.core.model.ConditionalNext.rules]）。
     *
     * 支持嵌套规则组，逻辑优先级同 [evalCompoundRules]。
     */
    @JvmStatic
    @JvmOverloads
    fun evalFilterRules(
        rules: List<FilterRule>,
        defaultLogic: String,
        nodeOutput: Any?,
        globalLogicEnabled: Boolean? = null,
        globalLogicOperator: String? = null,
    ): Boolean = evalRulesInternal(
        rules = rules,
        defaultLogic = defaultLogic,
        nodeOutput = nodeOutput,
        globalLogicEnabled = globalLogicEnabled ?: false,
        globalLogicOperator = globalLogicOperator,
        extract = { idx, rule ->
            ExtractedNode(
                index = idx,
                logic = rule.logic,
                evaluate = {
                    if (rule.rules != null) {
                        evalFilterRules(
                            rules = rule.rules,
                            defaultLogic = rule.logic ?: defaultLogic,
                            nodeOutput = nodeOutput,
                            globalLogicEnabled = rule.globalLogicEnabled ?: false,
                            globalLogicOperator = rule.globalLogicOperator,
                        )
                    } else {
                        evalRule(
                            field = rule.field ?: "",
                            operator = rule.operator ?: "",
                            ruleValue = rule.value,
                            nodeOutput = nodeOutput,
                            negate = rule.negate,
                        )
                    }
                },
            )
        },
    )

    // ─── 核心求值（通用）───────────────────────────────────────

    /**
     * 内部数据类：从不同来源（Map / FilterRule）提取出的统一规则节点。
     * [evaluate] 在真正需要时才执行，支持递归规则组的短路求值。
     */
    private data class ExtractedNode(
        val index: Int,
        val logic: String?,        // 本条规则/规则组与上一条之间的 AND/OR（第 1 条忽略）
        val evaluate: () -> Boolean,
    )

    /**
     * 通用规则组合求值：以短路方式遍历规则，按 AND/OR 语义逐步聚合。
     *
     * 实现说明：
     * - 使用累积变量 [accumulated] 而非简单“统一 AND / 统一 OR” 全局判断，
     *   这样能精确支持 a AND b OR c 的逐规则独立逻辑。
     * - 第 1 条规则/规则组直接作为初始累积值；第 2 条起按该规则的 logic（或全局 / 默认值）与累积值组合。
     * - 规则组通过 [ExtractedNode.evaluate] 递归求值，并在短路时避免不必要的深层计算。
     */
    private inline fun <T> evalRulesInternal(
        rules: List<T>,
        defaultLogic: String,
        nodeOutput: Any?,
        globalLogicEnabled: Boolean,
        globalLogicOperator: String?,
        extract: (Int, T) -> ExtractedNode?,
    ): Boolean {
        if (rules.isEmpty()) return false

        val resolvedGlobalOp = (globalLogicOperator ?: defaultLogic).lowercase()
        val useGlobal = globalLogicEnabled && (resolvedGlobalOp == "and" || resolvedGlobalOp == "or")

        var accumulated: Boolean? = null

        for ((i, raw) in rules.withIndex()) {
            val node = extract(i, raw) ?: continue

            val matched = node.evaluate()

            if (accumulated == null) {
                // 第 1 条有效规则/规则组：初始化累积值
                accumulated = matched
                continue
            }

            // 第 2+ 条：确定 logic 运算符
            val rawRuleOp = node.logic
            val op = when {
                useGlobal -> resolvedGlobalOp
                rawRuleOp != null && rawRuleOp.lowercase().let { it == "and" || it == "or" } -> rawRuleOp.lowercase()
                else -> defaultLogic.lowercase().let { if (it == "or") "or" else "and" }
            }

            // 短路：AND 且当前累积已为 false 时，无论 matched 如何结果都是 false；
            // OR 且当前累积已为 true 时，无论 matched 如何结果都是 true。
            // 但为了不跳过可能改变后续逻辑的项，这里仍要计算当前组合结果并继续遍历，
            // 因为下一条可能使用不同的 logic（例如当前 false AND，下一条 OR true）。
            accumulated = when (op) {
                "or" -> accumulated || matched
                else -> accumulated && matched
            }
        }

        return accumulated ?: false
    }

    // ─── 字段解析 ─────────────────────────────────────────────────

    /**
     * 按字段路径解析值。
     *
     * - 若 [nodeOutput] 是 Map，支持点号嵌套路径，如 `user.address.city`
     * - 若路径为空，返回 [nodeOutput] 本身
     * - 若 [nodeOutput] 非 Map 且 field 为 `"output"`，返回 [nodeOutput] 本身
     */
    fun resolveFieldValue(field: String, nodeOutput: Any?): Any? {
        if (field.isBlank()) return nodeOutput

        // Map 类型：支持嵌套路径
        if (nodeOutput is Map<*, *>) {
            val parts = field.split('.')
            var current: Any? = nodeOutput
            for (part in parts) {
                current = when (current) {
                    is Map<*, *> -> current[part]
                    else -> return null
                }
            }
            return current
        }

        // 非 Map：field 为 "output" 时返回本身
        return if (field == "output") nodeOutput else null
    }

    // ─── 操作符匹配 ─────────────────────────────────────────────────

    /**
     * 操作符匹配入口。
     *
     * 设计约定：所有"否定型"操作符（neq / notIn / isNotNull）在内部均归一化为
     * 对应基础操作符（eq / in / isNull）后再取反，避免求值逻辑重复，并保证
     * 与显式设置 negate=true 时的行为完全一致。
     */
    private fun matchOperator(operator: String, fieldValue: Any?, ruleValue: Any?): Boolean {
        return when (operator.lowercase()) {
            "eq", "==" -> matchEq(fieldValue, ruleValue)
            "neq", "!=" -> !matchEq(fieldValue, ruleValue)

            "gt", ">" -> compareNumeric(fieldValue, ruleValue) > 0
            "gte", ">=" -> compareNumeric(fieldValue, ruleValue) >= 0
            "lt", "<" -> compareNumeric(fieldValue, ruleValue) < 0
            "lte", "<=" -> compareNumeric(fieldValue, ruleValue) <= 0

            "in" -> evalIn(fieldValue, ruleValue)
            "notin", "not_in" -> !evalIn(fieldValue, ruleValue)

            "contains" -> matchContains(fieldValue, ruleValue)
            "startswith", "starts_with" -> matchStartsWith(fieldValue, ruleValue)
            "endswith", "ends_with" -> matchEndsWith(fieldValue, ruleValue)

            "isnull", "is_null" -> fieldValue == null
            "isnotnull", "is_not_null" -> fieldValue != null

            "isempty", "is_empty" -> evalIsEmpty(fieldValue)

            "sizeeq", "size_eq" -> sizeOf(fieldValue).let { it != null && compareNumeric(it, ruleValue) == 0 }
            "sizegt", "size_gt" -> sizeOf(fieldValue).let { it != null && compareNumeric(it, ruleValue) > 0 }
            "sizegte", "size_gte" -> sizeOf(fieldValue).let { it != null && compareNumeric(it, ruleValue) >= 0 }
            "sizelt", "size_lt" -> sizeOf(fieldValue).let { it != null && compareNumeric(it, ruleValue) < 0 }
            "sizelte", "size_lte" -> sizeOf(fieldValue).let { it != null && compareNumeric(it, ruleValue) <= 0 }

            "regex" -> matchRegex(fieldValue, ruleValue)

            else -> {
                log.warn("RuleEvaluator: unknown operator '$operator'")
                false
            }
        }
    }

    private fun matchEq(fieldValue: Any?, ruleValue: Any?): Boolean =
        fieldValue == ruleValue || fieldValue?.toString() == ruleValue?.toString()

    private fun matchContains(fieldValue: Any?, ruleValue: Any?): Boolean =
        fieldValue?.toString()?.contains(ruleValue?.toString() ?: "") == true

    private fun matchStartsWith(fieldValue: Any?, ruleValue: Any?): Boolean =
        fieldValue?.toString()?.startsWith(ruleValue?.toString() ?: "") == true

    private fun matchEndsWith(fieldValue: Any?, ruleValue: Any?): Boolean =
        fieldValue?.toString()?.endsWith(ruleValue?.toString() ?: "") == true

    private fun matchRegex(fieldValue: Any?, ruleValue: Any?): Boolean {
        return try {
            Regex(ruleValue?.toString() ?: "").containsMatchIn(fieldValue?.toString() ?: "")
        } catch (e: Exception) {
            log.warn("RuleEvaluator: invalid regex '${ruleValue}': ${e.message}")
            false
        }
    }

    // ─── 数值比较 ─────────────────────────────────────────────────

    private fun compareNumeric(a: Any?, b: Any?): Int {
        val da = toDouble(a) ?: return -1
        val db = toDouble(b) ?: return 1
        return da.compareTo(db)
    }

    private fun toDouble(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

    // ─── in 操作符 ─────────────────────────────────────────────────

    private fun evalIn(fieldValue: Any?, ruleValue: Any?): Boolean {
        val list = when (ruleValue) {
            is List<*> -> ruleValue
            is String -> ruleValue.split(",").map { it.trim() }
            else -> listOf(ruleValue)
        }
        return list.any { it == fieldValue || it?.toString() == fieldValue?.toString() }
    }

    // ─── isEmpty / 长度（size）操作符 ─────────────────────────────

    /**
     * 判断一个值是否"空"：
     *  - null
     *  - 字符串（trim 后）长度为 0
     *  - List / Array / Sequence / Collection 长度为 0
     *  - Map 长度为 0
     */
    private fun evalIsEmpty(fieldValue: Any?): Boolean {
        if (fieldValue == null) return true
        return when (fieldValue) {
            is String -> fieldValue.trim().isEmpty()
            is Collection<*> -> fieldValue.isEmpty()
            is Map<*, *> -> fieldValue.isEmpty()
            is Array<*> -> fieldValue.isEmpty()
            else -> false
        }
    }

    /**
     * 获取一个值的"长度/大小"：
     *  - String：字符数
     *  - Collection / Array / Sequence：元素数
     *  - Map：条目数
     *  其他类型返回 null（不可比较）
     */
    private fun sizeOf(fieldValue: Any?): Int? {
        if (fieldValue == null) return null
        return when (fieldValue) {
            is String -> fieldValue.length
            is Collection<*> -> fieldValue.size
            is Map<*, *> -> fieldValue.size
            is Array<*> -> fieldValue.size
            else -> null
        }
    }
}
