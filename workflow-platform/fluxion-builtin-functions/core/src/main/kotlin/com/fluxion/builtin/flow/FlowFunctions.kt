package com.fluxion.builtin.flow

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.builtin.meta.BuiltinFunctionMetas
import com.fluxion.core.engine.ExpressionEvaluator
import com.fluxion.core.engine.RuleEvaluator
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionResult
import org.slf4j.LoggerFactory
import org.slf4j.debug

/**
 * 内置条件分支函数（builtin:conditionBranch）
 *
 * 统一规则列表模式（按优先级）：
 * 1. rules 列表：{logic, rules, target} — 1 条为单条件，多条按 logic 组合
 * 2. 表达式模式（高级）：{condition, target} — JEXL 表达式
 * 3. 兼容旧数据：{field, operator, value, target} — 自动包装为单规则
 */
class ConditionBranchFunction : WorkflowFunction<String?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

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
            // 1. 规则列表模式（统一入口，1 条或多条均走此路径）
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
                    // rules 为空：回退到表达式模式
                    val condition = item["condition"] as? String
                    if (!condition.isNullOrBlank()) {
                        ExpressionEvaluator.evalBoolean(condition, directInput, workflowInput)
                    } else {
                        false
                    }
                }
            }

            // 2. 兼容旧数据：单字段格式 {field, operator, value} 自动包装为单规则
            !((item["field"] as? String).isNullOrBlank()) && !((item["operator"] as? String).isNullOrBlank()) -> {
                val singleRule = mapOf(
                    "field" to item["field"],
                    "operator" to item["operator"],
                    "value" to item["value"],
                    "negate" to (item["negate"] as? Boolean ?: false)
                )
                RuleEvaluator.evalCompoundRules(listOf(singleRule), logic, directInput)
            }

            // 3. 表达式模式（高级）
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

    override fun meta() = BuiltinFunctionMetas.CONDITION_BRANCH
}

/**
 * 内置条件过滤函数（builtin:filter）
 *
 * 统一规则列表模式（按优先级）：
 * 1. rules 列表：{logic, rules} — 1 条为单条件，多条按 logic 组合，返回 true/false
 * 2. 表达式模式（高级）：{condition} — JEXL 表达式求值
 * 3. 兼容旧数据：{field, operator, value} — 自动包装为单规则
 *
 * 配合节点的 conditionalNexts 实现条件路由：
 *   - true  分支 → 条件成立，路由到"是"节点
 *   - false 分支 → 条件不成立，路由到"否"节点
 */
class FilterFunction : WorkflowFunction<Boolean>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Boolean> {
        val globalNegate = input.param("negate", false)
        val logic = input.param("logic", "and")
        val rulesGlobalEnabled = input.param("rulesGlobalLogicEnabled", false)
        val rulesGlobalOp = input.param("rulesGlobalLogicOperator", logic)

        val rawResult = when {
            // 1. 规则列表模式（统一入口，1 条或多条均走此路径）
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
                    // rules 为空：回退到表达式模式
                    val condition = input.param<String>("condition")
                    if (!condition.isNullOrBlank()) {
                        ExpressionEvaluator.evalBoolean(condition, input.directInput, input.workflowInput)
                    } else {
                        false
                    }
                }
            }

            // 2. 兼容旧数据：单字段格式 {field, operator, value} 自动包装为单规则
            !input.param<String>("field").isNullOrBlank() && !input.param<String>("operator").isNullOrBlank() -> {
                val singleRule = mapOf(
                    "field" to input.param<String>("field"),
                    "operator" to input.param<String>("operator"),
                    "value" to input.param<Any>("value"),
                    "negate" to input.param("negate", false)
                )
                RuleEvaluator.evalCompoundRules(listOf(singleRule), logic, input.directInput)
            }

            // 3. 表达式模式
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

    override fun meta() = BuiltinFunctionMetas.FILTER
}

/**
 * 内置并行结果聚合函数（builtin:loopAggregator）
 *
 * 支持两种输入来源：
 * 1. DAG 合并：读取 [NodeInput.declaredDeps] 中所有依赖节点的输出；
 * 2. 线性流：读取 [NodeInput.directInput]，支持 List / 单值 / null。
 */
class LoopAggregatorFunction : WorkflowFunction<Any?>, BuiltinFunction {

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val mode = input.param("mode", "list")
        val directInput = input.directInput

        val items: List<*> = if (input.declaredDeps.isNotEmpty()) {
            // DAG 场景：有显式依赖时，聚合所有依赖节点的输出
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

    override fun meta() = BuiltinFunctionMetas.LOOP_AGGREGATOR
}
