package com.fluxion.core.model

/**
 * A single leaf predicate evaluated by the conditional-next router.
 *
 * A rule has two complementary shapes — select one:
 *  - **Simple:** `field` + `operator` + `value` (single comparison,
 *    exact-match semantics).  Operators are validated by the rule
 *    evaluator and cover the usual EQ/GT/GTE/LT/LTE/IN/CONTAINS/...
 *    plus their negated variants.
 *  - **Compound (nested):** `rules` + `logic` (AND/OR) — composes
 *    child rules recursively.  Optional `globalLogicEnabled` flips
 *    the compound to use *global* AND/OR instead of per-group logic.
 *
 * `negate` flips the final result of *this* rule (simple or compound)
 * before the enclosing group's logic combines it with its siblings.
 */
data class FilterRule(
    /** Dotted path into the context map, e.g. `"user.address.city"`. */
    val field: String? = null,
    /** Operator token — see evaluator docs for the canonical list. */
    val operator: String? = null,
    /** Right-hand side value; ignored for `isNull` / `isNotNull`. */
    val value: Any? = null,
    /** If true, invert the boolean result of this rule *after* evaluation. */
    val negate: Boolean = false,
    /**
     * Child rules combined with `logic` (`and` / `or`).
     *
     * When `null` (default) this is a *simple* rule and `field/operator/value`
     * drive behaviour instead.  When present it is a *compound* rule and the
     * simple fields are ignored unless the caller explicitly mixed both
     * shapes (undefined).
     */
    val rules: List<FilterRule>? = null,
    /**
     * Combining logic for child rules when this is a compound node —
     * `"and"` or `"or"`.  Falls back to the parent's default when `null`.
     */
    val logic: String? = null,
    /**
     * When true, `globalLogicOperator` overrides the per-child `logic`
     * field and all children are combined uniformly.  Defaults to `false`
     * for backwards compatibility with admin console V1 payloads.
     */
    val globalLogicEnabled: Boolean? = null,
    /** `and` or `or` — only consulted when `globalLogicEnabled == true`. */
    val globalLogicOperator: String? = null,
)

/**
 * A conditional edge from one node to a candidate successor.
 *
 * Stored in [WorkflowNode.conditionalNexts]; the DAG executor walks
 * the list in declaration order and fires the first target whose
 * condition (either expression or filter-rule tree) evaluates true.
 *
 * Two alternative predicates are supported (may be combined):
 *  - `condition`: a free-form JEXL / SpEL expression.
 *  - `rules`: a tree of [FilterRule] structs combined with `logic`.
 */
data class ConditionalNext(
    /** Target node id selected when this rule wins. */
    val target: String,
    /**
     * Expression-language condition (JEXL / SpEL).
     *
     * Has access to the current node's output (`#output[...]`), the
     * whole execution inputs (`#input[...]`), and a small set of
     * utility functions.  Example:
     * `#output['status'] == 'APPROVED'`
     */
    val condition: String? = null,
    /** Default combining logic for the rules list — `"and"` (default) or `"or"`. */
    val logic: String = "and",
    /** Negate the overall boolean of the whole ConditionalNext before target selection. */
    val negate: Boolean = false,
    /** Filter-rule tree; evaluated only when `condition` is absent or when combined. */
    val rules: List<FilterRule>? = null,
    /** When true, all `rules` use `rulesGlobalLogicOperator` instead of their own `logic`. */
    val rulesGlobalLogicEnabled: Boolean = false,
    /** Global AND/OR override — only consulted when [rulesGlobalLogicEnabled] is true. */
    val rulesGlobalLogicOperator: String = "and",
)
