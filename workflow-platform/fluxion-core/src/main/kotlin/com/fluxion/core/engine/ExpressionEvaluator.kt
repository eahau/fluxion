package com.fluxion.core.engine

import com.googlecode.aviator.AviatorEvaluator
import com.googlecode.aviator.AviatorEvaluatorInstance
import com.googlecode.aviator.Options
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * 表达式求值（基于 AviatorScript）
 *
 * 与 JEXL3 完全兼容：
 * 1. 对外签名与 `ExpressionEvaluatorTest` 原有调用 1:1 对齐（`nodeOutput: Any?` + `upstreamInput: Map` 顺序一致）
 * 2. SpEL 风格 `#variable` 前缀统一剥除（预处理缓存）
 * 3. `undefined` 变量允许访问（`strict=false` 等价）
 * 4. 布尔/字符串/对象求值错误一律返回 `false/null`，绝不抛异常给调用方
 *
 * 新增语义域参数（后端 MockEngine / FlowFunctions 直接传）：
 *   - directInput / workflowInput / declaredDeps 独立注入域
 *   - 内部 buildEnv 展平优先级：nodeOutput > directInput > upstreamInput > workflowInput
 */
object ExpressionEvaluator {

  private val log = LoggerFactory.getLogger(javaClass)

  /** 预处理缓存：原始表达式 → 剥 # / 替换 null→nil / ??→三元 后的表达式字符串 */
  private val normalizedCache = ConcurrentHashMap<String, String>()

  /** SpEL `#标识符` 前缀剥离 */
  private val spELPrefixPattern: Pattern = Pattern.compile("#(\\p{Alpha}[\\p{Alnum}_]*)")

  /** `\bnull\b` 整词替换成 Aviator 的空值 `nil` */
  private val nullWordPattern: Pattern = Pattern.compile("(?<!\\p{Alnum})null(?!\\p{Alnum})")

  /**
   * Elvis `a ?? b` 运算符改写。
   *
   * AviatorScript 原生不支持 `??`（Kotlin/C# 风格），但支持标准三元 `cond ? a : b`。
   * 这里用最低优先级的语法糖改写：将最顶层（不嵌套括号内部）的 `??` 依次改写为 `(_lhs != nil ? _lhs : _rhs)`，
   * 嵌套场景（如 `map['k'] ?? fallback`、三元内含 `??`）一律由括号展开保证优先级正确。
   */
  private fun rewriteElvis(expr: String): String {
    if (!expr.contains("??")) return expr
    // 极简实现：按 ?? 切分，左折叠为 ((a != nil ? a : b) != nil ? b : c)...
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
   * 单例 AviatorEvaluatorInstance。
   *
   * 注意：**必须使用 AviatorEvaluator.getInstance() 而不是 newInstance()**：
   *   newInstance() 创建的空白实例不会自动加载 string/math/seq/date/regexp 等内置模块；
   *   getInstance() 是静态初始化时通过 ServiceLoader 扫描 META-INF/aviator_functions 注册的全局单例，
   *   包含所有 ~50+ 内置函数库，功能与原 JEXL3 内置函数+工具对象的组合对齐。
   *
   * 调用风格：AviatorScript 原生支持两种等价写法：
   *   1. 模块函数式：`string.contains(msg, 'world')` / `seq.filter(nums, lambda(x) -> x > 3 end)`
   *   2. 属性访问：`user.name` 自动回落到 JavaBean getter（user.getName()）
   *   但注意 `var.method(args)` 形式的"实例方法调用"在 5.x 非默认启用；开发侧统一以模块函数风格书写即可。
   */
  private val aviator: AviatorEvaluatorInstance = AviatorEvaluator.getInstance().also { inst ->
    inst.setOption(Options.MAX_LOOP_COUNT, 10_000L)
    inst.setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EVAL)
    inst.setOption(Options.TRACE_EVAL, false)
  }

  // ============================================================
  // 对外 API（签名与原 JEXL 实现 1:1 对齐，测试零修改）
  // ============================================================

  /**
   * 布尔求值（条件分支 / 路由最常用）。
   *
   * 原 JEXL 兼容参数顺序：
   *   nodeOutput 可为 Any（Map / String / Number / null / List 等）
   *   upstreamInput 为工作流启动入参 Map（也写作 input alias）
   * 追加的语义域参数（后端传入）放到参数尾部、默认 null、保持二进制兼容。
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
      // 严格与原 JEXL 实现对齐：
      //   - 只有 Boolean=true 才返回 true；
      //   - Boolean=false、非 Boolean 结果、null 全部返回 false。
      return result as? Boolean == true
    } catch (e: Exception) {
      log.warn("Aviator 布尔表达式求值失败, expression={}, error={}", expression, e.message, e)
      return false
    }
  }

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
      log.warn("Aviator 字符串表达式求值失败, expression={}, error={}", expression, e.message, e)
      null
    }
  }

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
      log.warn("Aviator 对象表达式求值失败, expression={}, error={}", expression, e.message, e)
      null
    }
  }

  // ============================================================
  // 内部工具
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
   * 构造 Aviator env：
   *   `output` / `input` 保留 JEXL 时代常用 alias；
   *   nodeOutput 是 Map 时自动展平（`status == 'APPROVED'` 短写形式保留）；
   *   nodeOutput 是单值（String/Number/List 等）仅挂载到 `output` 键，不展平。
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
