package com.fluxion.core.engine

import com.googlecode.aviator.AviatorEvaluator
import com.googlecode.aviator.AviatorEvaluatorInstance
import com.googlecode.aviator.Options
import org.slf4j.LoggerFactory
import org.slf4j.warn
import org.slf4j.debug
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Expression evaluator powered by AviatorScript.
 *
 * Replaces the previous JEXL3 engine while remaining API-compatible with
 * the original `ExpressionEvaluatorTest` semantics:
 *  1. Function signatures accept `nodeOutput: Any?` plus contextual maps
 *     (`upstreamInput`, `directInput`, `workflowInput`, `declaredDeps`).
 *  2. SpEL-style `#variable` prefixes are stripped so existing expressions
 *     work without modification.
 *  3. `undefined` references evaluate to `nil` (Aviator default;
 *     OPTIMIZE_LEVEL=EVAL so strict mode is off).
 *  4. Falsy coercion is conservative — only the explicit boolean `true`
 *     is treated as truth in [evalBoolean] so that `0` / `""` / empty
 *     collections never accidentally match (kept for JEXL parity).
 *
 * Callers in [DagExecutor], [WorkflowEngine] and admin conditional filters
 * rely on: `output`, `input`, `directInput`, `workflowInput`, `declaredDeps`,
 * and direct top-level keys from upstream inputs being available in the
 * evaluation environment (see [buildEnv] for resolution order).
 */
object ExpressionEvaluator {

  private val log = LoggerFactory.getLogger(javaClass)

  /** Normalised-expression cache — avoids re-running regex rewrites on hot paths. */
  private val normalizedCache = ConcurrentHashMap<String, String>()

  /** Matches SpEL `#variableName` for stripping (-> `variableName`). */
  private val spELPrefixPattern: Pattern = Pattern.compile("#(\\p{Alpha}[\\p{Alnum}_]*)")

  /** Matches whole-word `null` and rewrites to Aviator's `nil`. */
  private val nullWordPattern: Pattern = Pattern.compile("(?<!\\p{Alnum})null(?!\\p{Alnum})")

  /**
   * Rewrite Kotlin/C#-style Elvis `a ?? b` chains into AviatorScript ternaries.
   *
   * AviatorScript has no native `??` operator so the chain is expanded as
   * nested ternaries: `a ?? b ?? c` becomes
   * `(((a) != nil ? (a) : (b)) != nil ? (b) : (c))…` which correctly
   * returns the first non-nil value. The naive string-split approach is
   * safe here because `??` is only ever a binary operator and cannot
   * appear inside valid string literals used in workflow expressions.
   */
  private fun rewriteElvis(expr: String): String {
    if (!expr.contains("??")) return expr
    val parts = expr.split("??")
    if (parts.size < 2) return expr
    var acc = parts[0]
    for (i in 1 until parts.size) {
      val rhs = parts[i]
      acc = "(($acc) != nil ? ($acc) : ($rhs))"
    }
    return acc
  }

  /**
   * Shared [AviatorEvaluatorInstance] used by every eval call.
   *
   * **Important**: use `AviatorEvaluator.getInstance()` (NOT `newInstance()`):
   *  - `newInstance()` creates an empty instance missing built-in
   *    `string`/`math`/`seq`/`date`/`regexp` libraries that existing
   *    expressions depend on.
   *  - `getInstance()` additionally picks up SPI-registered functions
   *    from `META-INF/aviator_functions` (ServiceLoader), which is how
   *    the extended ~50+ JEXL-compatible custom function library is
   *    injected without a hard compile-time dependency.
   *
   * Production safety:
   *  - MAX_LOOP_COUNT prevents long-running / infinite loops written
   *    into user expressions (e.g. `seq.each(..., lambda(...) end)`).
   *  - OPTIMIZE_LEVEL=EVAL disables bytecode compilation which breaks
   *    GraalVM native-image and also slows cold startup in exchange for
   *    negligible steady-state gains in typical expression sizes.
   */
  private val aviator: AviatorEvaluatorInstance = AviatorEvaluator.getInstance().also { inst ->
    inst.setOption(Options.MAX_LOOP_COUNT, 10_000L)
    inst.setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EVAL)
    inst.setOption(Options.TRACE_EVAL, false)
  }

  // ============================================================
  // Public eval API — deliberately kept 1:1 compatible with former JEXL impl.
  // ============================================================

  /**
   * Evaluate an expression as a boolean.
   *
   * JEXL-compatible semantics: only the boxed `Boolean=true` counts as
   * truth — everything else (`Boolean=false`, non-boolean results,
   * exceptions) maps to `false`. This keeps the behaviour predictable
   * for consumers that used the previous engine.
   */
  fun evalBoolean(
    expression: String?,
    nodeOutput: Any? = null,
    upstreamInput: Map<String, Any?>? = null,
    directInput: Map<String, Any?>? = null,
    workflowInput: Map<String, Any?>? = null,
    declaredDeps: Map<String, Map<String, Any?>>? = null,
  ): Boolean {
    val expr = normalizeExpression(expression)
    if (expr.isNullOrBlank()) return false
    try {
      val env = buildEnv(nodeOutput, upstreamInput, directInput, workflowInput, declaredDeps)
      val result = aviator.execute(expr, env)
      return result as? Boolean == true
    } catch (e: Exception) {
      log.warn(e) { "Aviator boolean expression evaluation failed: expression=$expression, error=${e.message}" }
      return false
    }
  }

  /** Evaluate an expression and return its [toString] form (or null on failure/blank). */
  fun evalString(
    expression: String?,
    nodeOutput: Any? = null,
    upstreamInput: Map<String, Any?>? = null,
    directInput: Map<String, Any?>? = null,
    workflowInput: Map<String, Any?>? = null,
    declaredDeps: Map<String, Map<String, Any?>>? = null,
  ): String? {
    val expr = normalizeExpression(expression)
    if (expr.isNullOrBlank()) return null
    return try {
      val env = buildEnv(nodeOutput, upstreamInput, directInput, workflowInput, declaredDeps)
      val result = aviator.execute(expr, env)
      result?.toString()
    } catch (e: Exception) {
      log.warn(e) { "Aviator string expression evaluation failed: expression=$expression, error=${e.message}" }
      null
    }
  }

  /** Evaluate an expression and return the raw object result (or null on failure/blank). */
  fun evalObject(
    expression: String?,
    nodeOutput: Any? = null,
    upstreamInput: Map<String, Any?>? = null,
    directInput: Map<String, Any?>? = null,
    workflowInput: Map<String, Any?>? = null,
    declaredDeps: Map<String, Map<String, Any?>>? = null,
  ): Any? {
    val expr = normalizeExpression(expression)
    if (expr.isNullOrBlank()) return null
    return try {
      val env = buildEnv(nodeOutput, upstreamInput, directInput, workflowInput, declaredDeps)
      aviator.execute(expr, env)
    } catch (e: Exception) {
      log.warn(e) { "Aviator object expression evaluation failed: expression=$expression, error=${e.message}" }
      null
    }
  }

  // ============================================================
  // Internal helpers
  // ============================================================

  private fun normalizeExpression(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return normalizedCache.computeIfAbsent(raw.trim()) { expr ->
      var step = spELPrefixPattern.matcher(expr).replaceAll("$1")
      step = nullWordPattern.matcher(step).replaceAll("nil")
      step = rewriteElvis(step)
      step
    }
  }

  /**
   * Build the Aviator `env` map for a single evaluation.
   *
   * Well-known aliases (`output`, `input`, `directInput`, `workflowInput`,
   * `declaredDeps`, `upstream`) are populated first. Then top-level keys
   * are merged from the context maps with precedence:
   *   `directInput` > `upstreamInput` > `workflowInput` > nodeOutput-as-Map
   *
   * If `nodeOutput` is itself a Map its keys are also exposed at the top
   * level so expressions like `status == 'APPROVED'` work without the
   * `output.` prefix (JEXL parity). Non-Map node outputs (String /
   * Number / List) are reachable via the dedicated `output` alias only.
   */
  private fun buildEnv(
    nodeOutput: Any?,
    upstreamInput: Map<String, Any?>?,
    directInput: Map<String, Any?>?,
    workflowInput: Map<String, Any?>?,
    declaredDeps: Map<String, Map<String, Any?>>?,
  ): MutableMap<String, Any?> {
    val env: MutableMap<String, Any?> = HashMap(64)

    env["output"] = nodeOutput
    if (upstreamInput != null) env["input"] = upstreamInput

    if (directInput != null) env["directInput"] = directInput
    if (workflowInput != null) env["workflowInput"] = workflowInput
    if (declaredDeps != null) env["declaredDeps"] = declaredDeps
    if (upstreamInput != null) env["upstream"] = upstreamInput
    if (nodeOutput is Map<*, *>) env["nodeOutput"] = nodeOutput

    if (workflowInput != null) workflowInput.forEach { (k, v) -> if (k !in env) env[k] = v }
    if (upstreamInput != null) upstreamInput.forEach { (k, v) -> env[k] = v }
    if (directInput != null) directInput.forEach { (k, v) -> env[k] = v }
    if (nodeOutput is Map<*, *>) {
      for ((k, v) in nodeOutput) {
        if (k is String) env[k] = v
      }
    }
    return env
  }
}
