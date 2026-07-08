package com.fluxion.core.mock

/**
 * Top-level container for the declarative Mock configuration consumed by
 * [MockEngine] and exposed through the debug-API and config-center
 * `debug.mocks` namespace.
 *
 * Two ways to enable / disable the mock layer:
 * - `enabled=false` — master kill switch; the engine short-circuits
 *   immediately and all calls fall through to the real [WorkflowFunction].
 * - `rules[].enabled=false` — granular per-rule disable.
 *
 * `EMPTY` is provided as a convenience for callers that need to build a
 * zero-mock config (typically used by the default provider when no debug
 * snapshot is loaded from the config center).
 */
data class MockConfig(
    /** Master switch — when false NO rule in this config will ever match. */
    val enabled: Boolean = true,
    /** Ordered list of rules; they are evaluated first-declared → first-wins. */
    val rules: List<MockRule> = emptyList()
) {
    companion object {
        /** Shared empty-config instance (no rules, enabled=true — acts as no-op). */
        val EMPTY = MockConfig()
    }
}

/**
 * A single declarative mock rule — maps a function + optional condition to a
 * canned response (static JSON or template) + optional simulated latency.
 *
 * Matching contract (all four must be true for the rule to fire):
 * 1. `MockConfig.enabled == true`
 * 2. `this.enabled == true`
 * 3. `this.functionRef` exactly matches the DAG node's function reference.
 * 4. `condition` (AviatorScript expression + fieldMatchers) evaluates to true
 *    against the current [NodeInput].
 *
 * When a rule fires the response is chosen by priority:
 * 1. `response` (static value, deep-copied per call)
 * 2. `responseTemplate` (String template that is rendered then optionally
 *    parsed back as JSON — supports `${var}` / `${var:default}` for field
 *    lookups and `#{expr}` for full AviatorScript expressions).
 * 3. No response set → rule returns null (behaves as if the rule missed).
 */
data class MockRule(
    /** Rule identity — used by the debug UI for edit/delete and by audit logs. */
    val id: String = "",
    /** The exact function reference this rule applies to. e.g. `custom:userService.queryById`. */
    val functionRef: String = "",
    /** Per-rule enable flag — operators can silence a rule without deleting it. */
    val enabled: Boolean = true,
    /** Optional conditional gate — both `expression` and `fieldMatchers` must pass. */
    val condition: MockCondition? = null,
    /** Static response payload. Deep-copied on each match so callers can't mutate the rule. */
    val response: Any? = null,
    /** Template response — `${var}` / `${var:default}` / `#{aviatorExpr}` are supported. */
    val responseTemplate: String? = null,
    /** Simulated latency in ms — applied via `Thread.sleep(delayMs)` before returning the mock. */
    val delayMs: Int = 0
)

/**
 * Composite condition for a [MockRule]. A condition is the AND of its
 * sub-components: the AviatorScript `expression` (if non-blank) AND every
 * declared `fieldMatchers` entry must evaluate true for the rule to fire.
 *
 * Two independent condition languages are supported so operators can pick
 * whichever matches their mental model:
 * - **`expression`** — full power of AviatorScript (arithmetic, comparison,
 *   regex, helper functions via `fn.*`). The expression is normalised to
 *   strip SpEL-style `#id` prefixes before evaluation.
 * - **`fieldMatchers`** — table-driven / no-code style: each entry maps a
 *   flattened field path (e.g. `directInput.userId`) to a [FieldMatcher]
 *   declaring one or more string predicate(s).
 */
data class MockCondition(
    /** Optional AviatorScript expression; truthy result means the condition passes. */
    val expression: String? = null,
    /** Per-field string matchers — each key is a dotted path as produced by [MockEngine.flattenNodeInput]. */
    val fieldMatchers: Map<String, FieldMatcher>? = null
)

/**
 * String predicates for a single flattened field value.
 *
 * The four predicates are OR-ed together — the field matches if ANY of the
 * declared predicates pass. This lets an operator express "user 123 OR user
 * 456" declaratively without falling back to a script expression.
 *
 * All predicates are case-sensitive; if you need case-insensitive matching
 * use the lower() helper inside an AviatorScript expression.
 */
data class FieldMatcher(
    /** String must be exactly equal to one of these values. */
    val exact: Set<String> = emptySet(),
    /** String starts with at least one of these prefixes. */
    val startsWith: List<String> = emptyList(),
    /** String ends with at least one of these suffixes. */
    val endsWith: List<String> = emptyList(),
    /** String contains at least one of these substrings. */
    val contains: List<String> = emptyList()
) {
    /**
     * Test a candidate value against this matcher.
     *
     * Returns `false` if value is null or blank (matcher never matches
     * against missing / empty fields — use an expression if you need to
     * assert absence).
     */
    fun match(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false
        return exact.contains(value)
            || startsWith.any { value.startsWith(it) }
            || endsWith.any { value.endsWith(it) }
            || contains.any { value.contains(it) }
    }

    /** True if the matcher declares no predicates (acts as unconditional pass-through). */
    fun isEmpty(): Boolean = exact.isEmpty() && startsWith.isEmpty() && endsWith.isEmpty() && contains.isEmpty()
}
