package com.fluxion.builtin.flow

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.core.engine.ExpressionEvaluator
import com.fluxion.core.engine.RuleEvaluator
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionResult
import org.slf4j.LoggerFactory

/**
 * Built-in conditional branch router (`builtin:conditionBranch`).
 *
 * Evaluates an ordered list of conditions against the current node input and
 * returns the `target` (next-node ID) of the FIRST matching condition. This
 * lets workflow authors implement multi-way if/else-if style routing without
 * stacking individual filter nodes.
 *
 * Three condition shapes are supported in priority order (highest first):
 * 1. **Compound rules** — `{rules, logic, target}`. `rules` is a list of
 *    `{field, operator, value}` rule objects; `logic` is `"and"` or `"or"`.
 * 2. **JEXL expression** — `{condition, target}`. Evaluated via
 *    [ExpressionEvaluator] with `directInput` + `workflowInput` as context.
 * 3. **Legacy single-rule** — `{field, operator, value, target}`. Wrapped
 *    into a compound rule internally for backward compatibility.
 *
 * Every condition entry supports a top-level `negate` flag that flips the
 * final match result (so "not equal to X" reads cleanly without forcing
 * users to contort operators).
 *
 * If no condition matches and `defaultTarget` is set, it is returned;
 * otherwise `null` is returned (engine treats null-target as "fall through
 * to the node's declared default edge").
 */
class ConditionBranchFunction : WorkflowFunction<String?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:conditionBranch"

    override fun apply(input: NodeInput): FunctionResult<String?> {
        val conditions = input.requireParam<Any>("conditions")
        require(conditions is List<*>) { "nodeParams.conditions must be a list" }

        for (item in conditions) {
            val condMap = item as? Map<*, *> ?: continue
            val target = condMap["target"] as? String ?: continue

            val matched = evalConditionItem(condMap, input.directInput, input.workflowInput)
            if (matched) {
                log.debug { "ConditionBranch matched → $target" }
                return FunctionResult.success(target)
            }
        }

        val defaultTarget = input.param<String>("defaultTarget")
        log.debug { "ConditionBranch no match, using default: $defaultTarget" }
        return FunctionResult.success(defaultTarget)
    }

    private fun evalConditionItem(item: Map<*, *>, directInput: Any?, workflowInput: Map<String, Any>): Boolean {
        val globalNegate = item["negate"] as? Boolean ?: false
        val logic = (item["logic"] as? String) ?: "and"
        val rulesGlobalEnabled = item["rulesGlobalLogicEnabled"] as? Boolean ?: false
        val rulesGlobalOp = (item["rulesGlobalLogicOperator"] as? String) ?: logic

        val rawMatched = when {
            // 1. Compound rules — the modern path used by the admin UI.
            item["rules"] is List<*> -> {
                val rules = item["rules"] as List<*>
                if (rules.isNotEmpty()) {
                    RuleEvaluator.evalCompoundRules(
                        rules = rules,
                        defaultLogic = logic,
                        nodeOutput = directInput,
                        globalLogicEnabled = rulesGlobalEnabled,
                        globalLogicOperator = rulesGlobalOp,
                    )
                } else {
                    // Empty rules list: fall back to expression mode (some
                    // users pre-declare the structure and fill only condition).
                    val condition = item["condition"] as? String
                    if (!condition.isNullOrBlank()) {
                        ExpressionEvaluator.evalBoolean(condition, directInput, workflowInput)
                    } else {
                        false
                    }
                }
            }

            // 2. Legacy single-field shape — auto-wrap so old workflows keep working.
            !((item["field"] as? String).isNullOrBlank()) && !((item["operator"] as? String).isNullOrBlank()) -> {
                val singleRule = mapOf(
                    "field" to item["field"],
                    "operator" to item["operator"],
                    "value" to item["value"],
                    "negate" to (item["negate"] as? Boolean ?: false)
                )
                RuleEvaluator.evalCompoundRules(listOf(singleRule), logic, directInput)
            }

            // 3. Raw JEXL expression — power-user escape hatch for complex logic.
            else -> {
                val condition = item["condition"] as? String
                if (!condition.isNullOrBlank()) {
                    ExpressionEvaluator.evalBoolean(condition, directInput, workflowInput)
                } else {
                    false
                }
            }
        }

        return if (globalNegate) !rawMatched else rawMatched
    }
}

/**
 * Built-in boolean filter (`builtin:filter`).
 *
 * Returns `true` / `false` based on the configured rule/expression so the
 * engine's `conditionalNexts` routing can pick the "yes" or "no" edge.
 *
 * Same three evaluation modes as [ConditionBranchFunction] — compound rules,
 * legacy single-rule, and raw JEXL expression — plus the global `negate`
 * flag for inverting the result.
 *
 * Typical usage pattern in workflows:
 * ```
 *   FilterNode (builtin:filter)
 *     ├─ true  → ApprovalWorkflow
 *     └─ false → AutoRejectNode
 * ```
 */
class FilterFunction : WorkflowFunction<Boolean>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:filter"

    override fun apply(input: NodeInput): FunctionResult<Boolean> {
        val globalNegate = input.param("negate", false)
        val logic = input.param("logic", "and")
        val rulesGlobalEnabled = input.param("rulesGlobalLogicEnabled", false)
        val rulesGlobalOp = input.param("rulesGlobalLogicOperator", logic)

        val rawResult = when {
            // 1. Compound rules — standard path used by the admin UI.
            input.param<Any>("rules") is List<*> -> {
                val rules = input.param<Any>("rules") as List<*>
                if (rules.isNotEmpty()) {
                    RuleEvaluator.evalCompoundRules(
                        rules = rules,
                        defaultLogic = logic,
                        nodeOutput = input.directInput,
                        globalLogicEnabled = rulesGlobalEnabled,
                        globalLogicOperator = rulesGlobalOp,
                    )
                } else {
                    // Empty rules: fall back to expression mode.
                    val condition = input.param<String>("condition")
                    if (!condition.isNullOrBlank()) {
                        ExpressionEvaluator.evalBoolean(condition, input.directInput, input.workflowInput)
                    } else {
                        false
                    }
                }
            }

            // 2. Legacy single-field shape for backward compatibility.
            !input.param<String>("field").isNullOrBlank() && !input.param<String>("operator").isNullOrBlank() -> {
                val singleRule = mapOf(
                    "field" to input.param<String>("field"),
                    "operator" to input.param<String>("operator"),
                    "value" to input.param<Any>("value"),
                    "negate" to input.param("negate", false)
                )
                RuleEvaluator.evalCompoundRules(listOf(singleRule), logic, input.directInput)
            }

            // 3. Raw JEXL expression — fallback.
            else -> {
                val condition = input.param<String>("condition")
                if (!condition.isNullOrBlank()) {
                    ExpressionEvaluator.evalBoolean(condition, input.directInput, input.workflowInput)
                } else {
                    false
                }
            }
        }

        val result = if (globalNegate) !rawResult else rawResult
        log.debug { "Filter: negate=$globalNegate → $result" }
        return FunctionResult.success(result)
    }
}

/**
 * Built-in parallel-result aggregator (`builtin:loopAggregator`).
 *
 * Collects outputs from multiple upstream nodes (or from a direct-input
 * collection) and re-emits them in a chosen shape:
 *
 * Aggregation modes (via node param `mode`, default `"list"`):
 * - `"list"`  — all inputs as a flat `List` (safest default).
 * - `"merge"` — every input is expected to be a Map; keys are merged in
 *               encounter order (later entries overwrite earlier ones).
 * - `"first"` — first non-null input value.
 * - `"last"`  — last non-null input value.
 *
 * Input sources (autodetected):
 * - **DAG merge** — when the node has explicit `declaredDeps`, the
 *   outputs of those dependency nodes are used (the common case for
 *   fan-in after a parallel gateway).
 * - **Linear flow** — when there are no declared deps, `directInput`
 *   is used: if it's already a List it's used as-is, a single value is
 *   wrapped into `[value]`, and `null` becomes `[]`.
 */
class LoopAggregatorFunction : WorkflowFunction<Any?>, BuiltinFunction {

    override val functionName: String = "builtin:loopAggregator"

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val mode = input.param("mode", "list")
        val directInput = input.directInput

        val items: List<*> = if (input.declaredDeps.isNotEmpty()) {
            // DAG scenario: explicit dependencies present, aggregate them.
            input.declaredDeps.values.toList()
        } else when (directInput) {
            is List<*> -> directInput
            null -> emptyList<Any>()
            else -> listOf(directInput)
        }

        val result: Any? = when (mode) {
            "merge" -> {
                val merged = LinkedHashMap<String, Any?>()
                for (item in items) {
                    (item as? Map<*, *>)?.forEach { (k, v) ->
                        if (k is String) merged[k] = v
                    }
                }
                merged
            }
            "first" -> items.firstOrNull { it != null }
            "last" -> items.lastOrNull { it != null }
            else -> ArrayList(items)
        }

        return FunctionResult.success(result)
    }
}
