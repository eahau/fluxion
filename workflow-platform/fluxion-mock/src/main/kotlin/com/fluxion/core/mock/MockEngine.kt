package com.fluxion.core.mock

import com.fluxion.core.model.NodeInput
import com.googlecode.aviator.AviatorEvaluator
import com.googlecode.aviator.AviatorEvaluatorInstance
import com.googlecode.aviator.Options
import com.googlecode.aviator.runtime.function.AbstractVariadicFunction
import com.googlecode.aviator.runtime.type.AviatorFunction
import com.googlecode.aviator.runtime.type.AviatorNil
import com.googlecode.aviator.runtime.type.AviatorObject
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType
import org.slf4j.LoggerFactory
import org.slf4j.warn
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Central engine for declarative DAG-node mocking.
 *
 * Evaluates incoming [NodeInput] calls against a set of [MockRule]s and,
 * on a match, returns the canned response (static or templated) instead of
 * letting the real function execute.
 *
 * Three public APIs are exposed — each directly exercised by
 * `MockEngineTest`:
 * - **[evaluate]** — primary entry point: given `(MockConfig, functionRef, NodeInput)`,
 *   walks the rules in declaration order and returns the first matched response
 *   (or `null` if no rule matches, signalling "call the real function").
 * - **[renderTemplate]** — expand `${var}` / `${var:default}` variable placeholders
 *   and `#{expr}` AviatorScript expressions inside a string. Called by `evaluate`
 *   on `responseTemplate` rules; also exposed for operators to debug templates
 *   against sample inputs in the debug UI.
 * - **[flattenNodeInput]** — flatten a `NodeInput` into a dot-path → string
 *   map (e.g. `directInput.userId -> "u001"`) so operators can write
 *   [FieldMatcher] rules against any leaf value in the request graph.
 *
 * Expression engine uses **AviatorScript** (Googlecode) — a lightweight,
 * high-performance expression language with a safe default sandbox. A
 * small helper library (`fn.*`) is registered as built-in functions to
 * cover the common id/time/random use cases operators reach for.
 */
object MockEngine {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Matches `${var}` and `${var:defaultValue}` placeholders in response templates. */
    private val templateVarPattern = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?}")

    /** Matches `#{aviatorScriptExpression}` blocks in response templates (full expression evaluation). */
    private val templateExprPattern = Pattern.compile("#\\{([^}]+)}")

    /** Matches a SpEL-style `#identifier` prefix — normalised to bare name before evaluation. */
    private val spELPrefixPattern = Pattern.compile("#(\\p{Alpha}[\\p{Alnum}_]*)")

    /** Cache of `normalise(expr)` results — avoids recompiling the regex-replace on every expression eval. */
    private val normalizedCache = ConcurrentHashMap<String, String>()

    /**
     * Build an Aviator variadic function from a plain Kotlin lambda — converts
     * the AviatorObject[] arguments into a plain Java list and the return value
     * back into `AviatorNil` / `AviatorRuntimeJavaType` so callers don't have to
     * know about Aviator's internal type system.
     */
    private inline fun mkFn(name: String, crossinline body: (List<Any?>) -> Any): AviatorFunction =
        object : AbstractVariadicFunction() {
            override fun getName(): String = name
            override fun variadicCall(env: MutableMap<String, Any>?, args: Array<out AviatorObject>?): AviatorObject {
                val javaArgs = args?.map { a -> a.getValue(env) } ?: emptyList()
                val result = runCatching { body(javaArgs) }.getOrNull()
                return if (result == null) AviatorNil.NIL else AviatorRuntimeJavaType.valueOf(result)
            }
        }

    /**
     * The shared AviatorScript engine instance.
     *
     * We intentionally use `EVAL` mode (not `COMPILE`) here — mock expressions
     * change frequently (operator edits through the debug UI) and the eval
     * mode trades a tiny amount of per-call CPU for not needing to manage a
     * compiled-script cache invalidation lifecycle. `MAX_LOOP_COUNT=10,000`
     * acts as a safety bound against a rogue expression hanging the worker.
     *
     * Built-in helpers (fn.*) are registered once at engine construction time.
     * Additional string/math helpers are available via Aviator's default
     * library (string.*, math.*, seq.*, date.*, regexp.* functions).
     */
    private val aviator: AviatorEvaluatorInstance = AviatorEvaluator.getInstance().also { inst ->
        inst.setOption(Options.MAX_LOOP_COUNT, 10_000L)
        inst.setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EVAL)

        inst.addFunction("fn.now",       mkFn("fn.now")       { _ -> FnFunctions.now() })
        inst.addFunction("fn.uuidShort", mkFn("fn.uuidShort") { _ -> FnFunctions.uuidShort() })
        inst.addFunction("fn.uuid",      mkFn("fn.uuid")      { _ -> FnFunctions.uuid() })
        inst.addFunction("fn.timestamp", mkFn("fn.timestamp") { _ -> FnFunctions.timestamp() })
        inst.addFunction("fn.randInt",   mkFn("fn.randInt")   { args ->
            val s = (args.getOrNull(0) as? Number)?.toInt() ?: 0
            val e = (args.getOrNull(1) as? Number)?.toInt() ?: 0
            FnFunctions.randInt(s, e)
        })
    }

    // ============================================================
    // Public API — each call path is covered 1:1 by MockEngineTest
    // ============================================================

    /**
     * Primary entry point — evaluate rules for a function call.
     *
     * Returns the mock response payload, or `null` if NO rule matched (the
     * caller should invoke the real function). Short-circuit cases:
     * - `config.enabled == false` → `null` immediately.
     * - No rule with matching `functionRef && enabled` → `null`.
     *
     * Rule evaluation is ordered first-declared → first-wins; once a rule's
     * condition hits, its `delayMs` is applied before returning a deep copy
     * of `response` or the rendered / JSON-parsed `responseTemplate`.
     */
    fun evaluate(config: MockConfig, functionRef: String, nodeInput: NodeInput): Any? {
        if (!config.enabled) return null

        val rules = config.rules.filter { it.functionRef == functionRef && it.enabled }
        if (rules.isEmpty()) return null

        for (rule in rules) {
            if (conditionHit(rule.condition, nodeInput)) {
                val delayMs = rule.delayMs
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs.toLong())
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                return when {
                    rule.response != null -> deepCopy(rule.response)
                    rule.responseTemplate != null -> {
                        val rendered = renderTemplate(rule.responseTemplate, nodeInput)
                        parseJsonSafely(rendered) ?: rendered
                    }
                    else -> null
                }
            }
        }
        return null
    }

    /**
     * Render a response template (variable placeholders + AviatorScript blocks)
     * against a node input.
     *
     * Two-stage expansion (order matters):
     * 1. All `#{expr}` blocks are evaluated first so expression results can
     *    themselves contain `${var}` references (rare but valid in nested
     *    string-interpolation scenarios).
     * 2. All `${var}` / `${var:default}` tokens are resolved against the
     *    flattened env; missing values fall back to the declared default or
     *    the empty string.
     */
    fun renderTemplate(template: String, nodeInput: NodeInput): String {
        val env = buildEnv(nodeInput)
        // 1) Expand AviatorScript `#{expr}` blocks first
        val withExpr = templateExprPattern.matcher(template).replaceAll { m ->
            val expr = normalize(m.group(1).trim())
            runCatching { aviator.execute(expr, env)?.toString() ?: "" }.getOrElse { "" }
        }
        // 2) Expand simple variable `${var}` / `${var:default}` tokens
        return templateVarPattern.matcher(withExpr).replaceAll { m ->
            val path = m.group(1).trim()
            val default = if (m.groupCount() >= 2) m.group(2) else null
            val value = resolvePath(path, env)
            when {
                value != null -> value.toString()
                default != null -> default
                else -> ""
            }
        }
    }

    /**
     * Flatten a [NodeInput]'s object graph into a flat `dot.path -> value as
     * string` map. Used primarily to drive [FieldMatcher] rules.
     *
     * Examples of the produced keys:
     * ```
     * directInput.userId           -> "u001"
     * workflowInput.b.c            -> "deep"
     * declaredDeps.n1.d            -> "dep"
     * nodeParams.p                 -> "param"
     * directInput.items[0]         -> "first"  (list indexing via `[i]`)
     * ```
     */
    fun flattenNodeInput(nodeInput: NodeInput): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        flatten("directInput", nodeInput.directInput, out)
        flatten("workflowInput", nodeInput.workflowInput, out)
        flatten("declaredDeps", nodeInput.declaredDeps, out)
        flatten("nodeParams", nodeInput.nodeParams, out)
        return out
    }

    // ============================================================
    // Condition evaluation (MockCondition.expression AND fieldMatchers)
    // ============================================================

    /**
     * True if the optional expression evaluates truthy AND all declared
     * field matchers pass (AND semantics). Null condition is treated as
     * "match any" (backwards-compatible default for simple rules).
     */
    private fun conditionHit(condition: MockCondition?, nodeInput: NodeInput): Boolean {
        if (condition == null) return true
        val env = buildEnv(nodeInput)
        val exprOk = condition.expression.isNullOrBlank() || runCatching {
            val expr = normalize(condition.expression!!)
            truthy(aviator.execute(expr, env))
        }.getOrElse { e ->
            log.warn(e) { "Mock condition expression evaluation error: expr=${condition.expression}, error=${e.message}" }
            false
        }
        if (!exprOk) return false
        // 2. fieldMatchers: every declared matcher must match the flattened field
        val flat = flattenNodeInput(nodeInput)
        val matchers = condition.fieldMatchers ?: emptyMap()
        for ((flatKey, matcher) in matchers) {
            if (matcher.isEmpty()) continue
            if (!matcher.match(flat[flatKey])) return false
        }
        return true
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Normalise an expression string before evaluation — rewrite SpEL-style
     * `#identifier` references (which a subset of operators are trained on)
     * to bare identifiers (`identifier`). Result is cached in CHM keyed by
     * the raw expression.
     */
    private fun normalize(expr: String): String = normalizedCache.computeIfAbsent(expr) { raw ->
        spELPrefixPattern.matcher(raw).replaceAll("$1")
    }

    /**
     * Build the AviatorScript env for an input — every well-known node
     * bucket is exposed under its own namespace plus flat-merged aliases
     * so operators can use either `directInput.userId` (explicit) or
     * `userId` (convenience, precedence: nodeParams < declaredDeps <
     * workflowInput < directInput) when writing expressions.
     */
    private fun buildEnv(nodeInput: NodeInput): MutableMap<String, Any?> {
        val env: MutableMap<String, Any?> = HashMap(64)
        env["directInput"] = nodeInput.directInput
        env["workflowInput"] = nodeInput.workflowInput
        env["declaredDeps"] = nodeInput.declaredDeps
        env["nodeParams"] = nodeInput.nodeParams

        val params = nodeInput.nodeParams as? Map<*, *>
        val deps = nodeInput.declaredDeps as? Map<*, *>
        val wf = nodeInput.workflowInput as? Map<*, *>
        val di = nodeInput.directInput as? Map<*, *>

        // Flat aliases — later keys intentionally overwrite earlier ones so
        // directInput wins (it's the merged, "final" value from the runner).
        params?.forEach { (k, v) -> if (k is String) env[k] = v }
        deps?.forEach { (k, v) -> if (k is String) env[k] = v }
        wf?.forEach { (k, v) -> if (k is String) env[k] = v }
        di?.forEach { (k, v) -> if (k is String) env[k] = v }

        // Marker string so operators writing `#{fn.xxx}` expressions can
        // introspect the function list in the debug UI (we register fn.* as
        // Aviator functions directly via addFunction above).
        env["fn"] = "now, uuidShort, uuid, timestamp, random, randInt, lower, upper, trim, len"
        return env
    }

    /**
     * Walk a dotted path (with optional `[i]` list-index segments) against
     * an env map, returning the leaf value or null if any intermediate
     * segment is absent.
     */
    private fun resolvePath(path: String, env: Map<String, Any?>): Any? {
        if (path.isBlank()) return null
        val parts = path.split('.')
        var current: Any? = env[parts[0]]
        for (i in 1 until parts.size) {
            if (current == null) return null
            val part = parts[i]
            current = when (val c = current) {
                is Map<*, *> -> c[part]
                is List<*> -> part.toIntOrNull()?.let { idx -> if (idx in c.indices) c[idx] else null }
                else -> null
            }
        }
        return current
    }

    /**
     * AviatorScript → boolean coercion — mirrors common language conventions.
     * (Null, false, zero numbers, blank strings, empty collections all count as false.)
     */
    private fun truthy(r: Any?): Boolean = when (r) {
        null -> false
        is Boolean -> r
        is Number -> r.toDouble() != 0.0
        is String -> r.isNotBlank()
        is Collection<*> -> r.isNotEmpty()
        is Map<*, *> -> r.isNotEmpty()
        else -> true
    }

    /**
     * Recursive flatten — converts any object graph into `prefix.path → string`.
     * Lists/arrays use `[i]` suffix; null / empty collections emit a sentinel
     * string so FieldMatcher rules can distinguish "field is missing" from
     * "field exists but is empty".
     */
    private fun flatten(prefix: String, value: Any?, out: MutableMap<String, String>) {
        when (value) {
            null -> out[prefix] = "null"
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    out[prefix] = ""
                } else {
                    for ((k, v) in value) {
                        flatten("$prefix.$k", v, out)
                    }
                }
            }
            is List<*> -> {
                if (value.isEmpty()) {
                    out[prefix] = ""
                } else {
                    value.forEachIndexed { i, v -> flatten("$prefix[$i]", v, out) }
                }
            }
            is Array<*> -> {
                if (value.isEmpty()) {
                    out[prefix] = ""
                } else {
                    value.forEachIndexed { i, v -> flatten("$prefix[$i]", v, out) }
                }
            }
            else -> out[prefix] = value.toString()
        }
    }

    /**
     * Deep-copy a response payload (Map / List / scalar) so callers who
     * mutate the returned mock response (e.g. DAG node code that appends
     * to a list) cannot poison subsequent calls' return values.
     */
    @Suppress("UNCHECKED_CAST")
    private fun deepCopy(value: Any?): Any? = when (value) {
        null -> null
        is MutableMap<*, *> -> value.entries.associate { (k, v) -> k to deepCopy(v) } as MutableMap<Any?, Any?>
        is Map<*, *> -> value.entries.associate { (k, v) -> k to deepCopy(v) }
        is MutableList<*> -> value.mapTo(ArrayList(value.size)) { deepCopy(it) }
        is List<*> -> value.map { deepCopy(it) }
        is Array<*> -> value.map { deepCopy(it) }.toTypedArray()
        else -> value
    }

    /**
     * Best-effort JSON parse of a rendered template. Uses reflection against
     * Jackson (`com.fasterxml.jackson.databind.ObjectMapper`) so the mock
     * module doesn't take a hard compile-time dependency on Jackson (it
     * already comes transitively from Spring or workflow-core). Returns
     * `null` on parse failure or if Jackson is absent — callers fall back
     * to returning the raw rendered string.
     */
    private fun parseJsonSafely(s: String): Any? = try {
        val mapperCls = Class.forName("com.fasterxml.jackson.databind.ObjectMapper")
        val mapper = mapperCls.getDeclaredConstructor().newInstance()
        val readValue = mapperCls.getMethod("readValue", String::class.java, Class::class.java)
        readValue.invoke(mapper, s, Any::class.java)
    } catch (_: Exception) {
        null
    }
}

/**
 * Operator-facing helper functions registered as `fn.*` AviatorScript
 * built-ins. A subset of the common helpers needed by typical mock
 * response-templates (IDs, timestamps, random ranges).
 *
 * Additional helpers (string ops, math, seq, date, regexp) are provided
 * by AviatorScript's default function library — operators should consult
 * Aviator docs for those.
 */
object FnFunctions {
    @JvmStatic fun now(): Long = System.currentTimeMillis()
    @JvmStatic fun uuidShort(): String = java.util.UUID.randomUUID().toString().replace("-", "")
    @JvmStatic fun uuid(): String = java.util.UUID.randomUUID().toString()
    @JvmStatic fun timestamp(): Long = System.currentTimeMillis() / 1000L
    @JvmStatic fun randInt(start: Int, end: Int): Int = if (end <= start) 0 else start + (Math.random() * (end - start)).toInt()
}

/**
 * Package-level convenience wrapper so [MockWorkflowFunction] can write
 * `evaluateMock(config, ref, input)` without mentioning `MockEngine`
 * directly (keeps the two classes loosely coupled).
 */
@Suppress("unused")
fun evaluateMock(config: MockConfig, functionRef: String, nodeInput: NodeInput): Any? =
    MockEngine.evaluate(config, functionRef, nodeInput)
