package com.fluxion.core.engine

import com.fluxion.core.model.FilterRule
import org.slf4j.*

/**
 * Rule evaluator for conditional node transitions and filter rules.
 *
 * Supports both single field-operator-value rules and nested compound rules
 * with AND/OR logic. Used by [WorkflowEngine.resolveNextNode] to evaluate
 * [WorkflowNode.conditionalNexts] and by admin-console filter UIs.
 *
 * Operators cover equality, comparison, collection membership, string
 * matching, null/empty checks, size checks and regex. Unknown operators
 * log a warning and evaluate to `false`.
 */
object RuleEvaluator {

    private val log = LoggerFactory.getLogger(javaClass)

    private data class ExtractedNode(
        val index: Int,
        val logic: String?,
        val evaluate: () -> Boolean
    )

    /**
     * Evaluate a single atomic rule.
     *
     * @param field dot-separated path into [nodeOutput] (or blank to use the whole output)
     * @param operator one of eq/neq/gt/gte/lt/lte/in/not_in/contains/starts_with/ends_with/is_null/...
     * @param ruleValue right-hand side value configured on the rule
     * @param nodeOutput output produced by the preceding node (Map or raw value)
     * @param negate when true, invert the final boolean result
     * @return true if the rule matches (after optional negation)
     */
    @JvmStatic
    @JvmOverloads
    fun evalRule(field: String, operator: String, ruleValue: Any?, nodeOutput: Any?, negate: Boolean = false): Boolean {
        val fieldValue = resolveFieldValue(field, nodeOutput)
        val rawResult = matchOperator(operator, fieldValue, ruleValue)
        val result = if (negate) !rawResult else rawResult
        log.debug { "RuleEvaluator: $field $operator $ruleValue (negate=$negate) -> $result (fieldValue=$fieldValue)" }
        return result
    }

    /**
     * Evaluate compound rules expressed as a list of raw maps (JSON tree form).
     *
     * Each map is either a leaf rule (`field`+`operator`+`value`) or a nested
     * group (`rules` sub-list + optional `logic`). Nodes are combined with the
     * per-node `logic` field, falling back to `defaultLogic`, or overridden
     * by `globalLogicOperator` when `globalLogicEnabled` is true.
     */
    @JvmStatic
    @JvmOverloads
    fun evalCompoundRules(
        rules: List<*>,
        defaultLogic: String,
        nodeOutput: Any?,
        globalLogicEnabled: Boolean? = null,
        globalLogicOperator: String? = null
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
                val nestedLogic = m["logic"] as? String
                val nestedGlobalEnabled = m["globalLogicEnabled"] as? Boolean ?: false
                val nestedGlobalOp = m["globalLogicOperator"] as? String
                ExtractedNode(
                    index = idx,
                    logic = nestedLogic,
                    evaluate = {
                        evalCompoundRules(
                            rules = nestedRules,
                            defaultLogic = nestedLogic ?: defaultLogic,
                            nodeOutput = nodeOutput,
                            globalLogicEnabled = nestedGlobalEnabled,
                            globalLogicOperator = nestedGlobalOp
                        )
                    }
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
                            negate = m["negate"] as? Boolean ?: false
                        )
                    }
                )
            }
        }
    )

    /**
     * Evaluate compound rules using the typed [FilterRule] model.
     *
     * Mirrors [evalCompoundRules] but operates on the domain class used in
     * the Kotlin API instead of untyped maps.
     */
    @JvmStatic
    @JvmOverloads
    fun evalFilterRules(
        rules: List<FilterRule>,
        defaultLogic: String,
        nodeOutput: Any?,
        globalLogicEnabled: Boolean? = null,
        globalLogicOperator: String? = null
    ): Boolean = evalRulesInternal(
        rules = rules,
        defaultLogic = defaultLogic,
        nodeOutput = nodeOutput,
        globalLogicEnabled = globalLogicEnabled ?: false,
        globalLogicOperator = globalLogicOperator,
        extract = { idx, rule ->
            val nested = rule.rules
            ExtractedNode(
                index = idx,
                logic = rule.logic,
                evaluate = {
                    if (nested != null) {
                        evalFilterRules(
                            rules = nested,
                            defaultLogic = rule.logic ?: defaultLogic,
                            nodeOutput = nodeOutput,
                            globalLogicEnabled = rule.globalLogicEnabled ?: false,
                            globalLogicOperator = rule.globalLogicOperator
                        )
                    } else {
                        evalRule(
                            field = rule.field ?: "",
                            operator = rule.operator ?: "",
                            ruleValue = rule.value,
                            nodeOutput = nodeOutput,
                            negate = rule.negate
                        )
                    }
                }
            )
        }
    )

    /**
     * Generic rule-evaluation loop shared by map-based and FilterRule-based entry points.
     *
     * Walks the rule list once, lazily evaluating each extracted node and
     * combining booleans through the resolved AND/OR operator. Empty rule
     * lists return false (nothing matches).
     */
    private inline fun <T> evalRulesInternal(
        rules: List<T>,
        defaultLogic: String,
        nodeOutput: Any?,
        globalLogicEnabled: Boolean,
        globalLogicOperator: String?,
        extract: (Int, T) -> ExtractedNode?
    ): Boolean {
        if (rules.isEmpty()) return false

        val resolvedGlobalOp = (globalLogicOperator ?: defaultLogic).lowercase()
        val useGlobal = globalLogicEnabled && (resolvedGlobalOp == "and" || resolvedGlobalOp == "or")

        var accumulated: Boolean? = null

        for ((i, raw) in rules.withIndex()) {
            val node = extract(i, raw) ?: continue

            val matched = node.evaluate()

            if (accumulated == null) {
                accumulated = matched
                continue
            }

            val rawRuleOp = node.logic
            val op = when {
                useGlobal -> resolvedGlobalOp
                rawRuleOp != null && rawRuleOp.lowercase().let { it == "and" || it == "or" } -> rawRuleOp.lowercase()
                else -> defaultLogic.lowercase().let { if (it == "or") "or" else "and" }
            }

            accumulated = when (op) {
                "or" -> accumulated || matched
                else -> accumulated && matched
            }
        }

        return accumulated ?: false
    }

    /**
     * Resolve a dot-separated field path against the node output.
     *
     * - blank `field` returns the whole [nodeOutput]
     * - Map outputs are walked by key parts
     * - the literal `"output"` field also returns the whole output (legacy alias)
     */
    fun resolveFieldValue(field: String, nodeOutput: Any?): Any? {
        if (field.isBlank()) return nodeOutput

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

        return if (field == "output") nodeOutput else null
    }

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
                log.warn { "RuleEvaluator: unknown operator '$operator'" }
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
            log.warn(e) { "RuleEvaluator: invalid regex '${ruleValue}'" }
            false
        }
    }

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

    private fun evalIn(fieldValue: Any?, ruleValue: Any?): Boolean {
        val list = when (ruleValue) {
            is List<*> -> ruleValue
            is String -> ruleValue.split(",").map { it.trim() }
            else -> listOf(ruleValue)
        }
        return list.any { it == fieldValue || it?.toString() == fieldValue?.toString() }
    }

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
