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
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * 瀹搞儰缍斿ù浣界殶鐠?Mock 瀵洘鎼搁敍鍫濈唨娴?AviatorScript閿涘鈧? *
 * 鐎电懓顦绘稉澶夐嚋閸忣剙绱?API 娑撳骸甯?JEXL 閻楀牊婀?/ MockEngineTest 鐎瑰苯鍙忕€靛綊缍堥敍? *   - [evaluate]          閺嶈宓?MockConfig + functionRef + NodeInput 閹垫儳鎳℃稉顓☆潐閸掓瑥鑻熸潻鏂挎礀 mock 缂佹挻鐏? *   - [renderTemplate]    閻欘剛鐝涢幎濠傛惙鎼存梹膩閺夋寧瑕嗛弻鎾茶礋 JSON 鐎涙顑佹稉璇х礄`${var}` / `${var:default}` / `#{aviatorExpr}`閿? *   - [flattenNodeInput] 閹?NodeInput 鐏炴洖閽╅幋?`directInput.userId -> "u001"` 瑜般垹绱￠敍灞肩返 FieldMatcher 娴ｈ法鏁? */
object MockEngine {

  private val log = LoggerFactory.getLogger(javaClass)

  /** `${var}` 閹?`${var:default}` 瑜般垹绱￠敍鍫濆綁闁插繐宕版担宥忕礉鐢箑褰查柅澶愮帛鐠併倕鈧》绱?*/
  private val templateVarPattern = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?}")

  /** `#{aviator-expr}` 瑜般垹绱￠敍鍫濈暚閺佺銆冩潏鎯х础濮瑰倸鈧》绱?*/
  private val templateExprPattern = Pattern.compile("#\\{([^}]+)}")

  /** SpEL `#identifier` 閸撳秶绱戦崜銉╂珟 */
  private val spELPrefixPattern = Pattern.compile("#(\\p{Alpha}[\\p{Alnum}_]*)")

  /** 鐞涖劏鎻蹇涱暕婢跺嫮鎮婄紓鎾崇摠閿涘牊顒滈崚娆忓⒑ # 缂佹挻鐏夐敍?*/
  private val normalizedCache = ConcurrentHashMap<String, String>()

  /**
   * 鏉堝懎濮敍姘€柅鐘辩娑擃亜褰查崣妯哄棘閺佹壆娈?AviatorFunction 閸栧灝鎮曠€圭偘绶ラ敍灞惧Ω AviatorObject[] 閸忋劑鍎存潪顑胯礋 Java 缁鐎烽崥搴ゆ祮閸欐垹绮?body閵?   */
  private inline fun mkFn(name: String, crossinline body: (List<Any?>) -> Any): AviatorFunction =
    object : AbstractVariadicFunction() {
      override fun getName(): String = name
      override fun variadicCall(env: MutableMap<String, Any>?, args: Array<out AviatorObject>?): AviatorObject {
        val javaArgs = args?.map { a -> a?.getValue(env) } ?: emptyList()
        val result = runCatching { body(javaArgs) }.getOrNull()
        return if (result == null) AviatorNil.NIL else AviatorRuntimeJavaType.valueOf(result)
      }
    }

  /**
   * Aviator 閸楁洑绶ラ敍鍫滃▏閻?getInstance() 閸忓彉闊╁鎻掑鏉炶棄鍞寸純顔侥侀崸妤冩畱鐎圭偘绶ラ敍? 濞屾瑧顔堥柅澶愩€?+ fn.* 閺傝纭跺▔銊ュ斀閵?   * 濞夈劍鍓伴敍姝痚wInstance() 閸掓稑缂撻惃鍕敄閻ц棄鐤勬笟瀣╃瑝娴兼俺鍤滈崝銊ュ鏉?string/math/seq/date/regexp 缁涘鍞寸純顔煎毐閺佹澘绨遍敍?   *       getInstance() 瀹告煡鈧俺绻?ServiceLoader 閹殿偅寮?META-INF/aviator_functions 鐎瑰本鍨氶崘鍛枂濡€虫健濞夈劌鍞介妴?   */
  private val aviator: AviatorEvaluatorInstance = AviatorEvaluator.getInstance().also { inst ->
    inst.setOption(Options.MAX_LOOP_COUNT, 10_000L)
    inst.setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EVAL)

    // 5 娑?fn.* 瀹搞儱鍙块崙鑺ユ殶閿涘牅绗熼崝鈥茬瑩鐏?+ 濡剝瀚欏娲付濮瑰偊绱辩€涙顑佹稉?闂嗗棗鎮?闂呭繑婧€濞搭喚鍋ｅ銉ュ徔閻╁瓨甯撮悽?Aviator 閸愬懐鐤嗛敍?    inst.addFunction("fn.now",       mkFn("fn.now")       { _ -> FnFunctions.now() })
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
  // 閸忣剙绱?API閿涘湣ockEngineTest 1:1 鐎靛綊缍堥敍?  // ============================================================

  /**
   * 娑撹鍙嗛崣锝忕窗閺嶈宓?functionRef 閹垫儳鎳℃稉顓☆潐閸掓瑱绱濇潻鏂挎礀 mock 缂佹挻鐏夐敍灞惧灗 null閿涘牊鐥呴崨鎴掕厬閿涘鈧?   *
   * 鏉╂柨娲栭崐纭风窗
   *   - config.enabled == false 閳?null
   *   - 閹碘偓閺堝顫夐崚娆撳厴 disabled / functionRef 娑撳秴灏柊?/ 閺夆€叉娑撳秴鎳℃稉?閳?null
   *   - 閸涙垝鑵戠憴鍕灟娑?`response != null` 閳?response 濞ｈ鲸瀚圭拹婵撶礄闂冨弶顒涙径鏍劥娣囶喗鏁煎Ч鈩冪厠閿?   *   - 閸涙垝鑵戠憴鍕灟娑撴柨褰ч張?`responseTemplate` 閳?濞撳弶鐓嬮崥搴ば掗弸鎰礋 JSON 鐎电钖勯敍鍫濄亼鐠愩儱鍨幎娑氱舶鐠嬪啰鏁ら弬鐧哥礆
   */
  fun evaluate(config: MockConfig, functionRef: String, nodeInput: NodeInput): Any? {
    if (!config.enabled) return null

    val rules = config.rules.filter { it.functionRef == functionRef && it.enabled }
    if (rules.isEmpty()) return null

    for (rule in rules) {
      if (conditionHit(rule.condition, nodeInput)) {
        // 閸涙垝鑵戦敍姘崇箲閸ョ偤娼ら幀?response > responseTemplate 鐟欙絾鐎介崥搴ｆ畱 JSON
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
            // 濡剝婢樻稉鈧懜顒佹Ц JSON 鐎涙顑佹稉璇х幢鏉╂瑩鍣风憴锝嗙€介崶?Map/List/閸╃儤婀扮猾璇茬€?            parseJsonSafely(rendered) ?: rendered
          }
          else -> null
        }
      }
    }
    return null
  }

  /**
   * 閻欘剛鐝涘Ο鈩冩緲濞撳弶鐓嬮敍姝?{var:default}` 閸楃姳缍?+ `#{expr}` 鐞涖劏鎻蹇嬧偓?   *
   * MockEngineTest 娑擃厾娲块幒銉ㄧ殶閻㈩煉绱癭renderTemplate("""{"ts":#{fn.now()}...}""", nodeInput())` 閺堢喐婀滄潻鏂挎礀 JSON 鐎涙顑佹稉灞傗偓?   */
  fun renderTemplate(template: String, nodeInput: NodeInput): String {
    val env = buildEnv(nodeInput)
    // 1) 閸忓牆顦╅悶?`#{expr}`
    val withExpr = templateExprPattern.matcher(template).replaceAll { m ->
      val expr = normalize(m.group(1).trim())
      runCatching { aviator.execute(expr, env)?.toString() ?: "" }.getOrElse { "" }
    }
    // 2) 閸愬秴顦╅悶?`${var}` / `${var:default}`閿涘牊鏁幐渚€绮拋銈呪偓纭风礆
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
   * 閹?NodeInput 鐏炴洖閽╂稉?key=string, value=string 閻?Map閿涘奔绶?FieldMatcher 娴ｈ法鏁ら妴?   *
   * 鏉堟挸鍤粈杞扮伐閿?   *   directInput.userId       -> u001
   *   workflowInput.b.c        -> deep
   *   declaredDeps.n1.d        -> dep
   *   nodeParams.p             -> param
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
  // 閺夆€叉閸涙垝鑵戦崚銈嗘焽閿涘湣ockCondition.expression AND fieldMatchers 闁姤寮х搾铏缁犳鎳℃稉顓ㄧ礆
  // ============================================================

  private fun conditionHit(condition: MockCondition?, nodeInput: NodeInput): Boolean {
    if (condition == null) return true
    val env = buildEnv(nodeInput)
    val exprOk = condition.expression.isNullOrBlank() || runCatching {
      val expr = normalize(condition.expression!!)
      truthy(aviator.execute(expr, env))
    }.getOrElse { e ->
      log.warn("Mock condition expression evaluation error: expr={}, error={}", condition.expression, e.message, e)
      false
    }
    if (!exprOk) return false
    // 2. fieldMatchers: all must match after flattening
    val flat = flattenNodeInput(nodeInput)
    val matchers = condition.fieldMatchers ?: emptyMap()
    for ((flatKey, matcher) in matchers) {
      if (matcher.isEmpty()) continue
      if (!matcher.match(flat[flatKey])) return false
    }
    return true
  }

  // ============================================================
  // 閸愬懘鍎村銉ュ徔
  // ============================================================

  /** 鐞涖劏鎻蹇涱暕婢跺嫮鎮婄紓鎾崇摠閿涙艾澧?SpEL `#` 閸撳秶绱?*/
  private fun normalize(expr: String): String = normalizedCache.computeIfAbsent(expr) { raw ->
    spELPrefixPattern.matcher(raw).replaceAll("$1")
  }

  /**
   * 閺嬪嫰鈧?Aviator env閿涘牅绗?JEXL 閺冄呭鐎靛綊缍堥敍澶涚窗
   *   - directInput / workflowInput / declaredDeps / nodeParams 閸ユ稐閲滅拠顓濈疅閸?   *   - 妞よ泛鐪扮仦鏇為挬娴兼ê鍘涚痪褝绱癲irectInput > workflowInput > declaredDeps > nodeParams閿涘牅绗岄崢?JEXL 閻楀牅绔撮懛杈剧礆
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

    // 妞よ泛鐪扮仦鏇為挬閿涙艾鍘涢崘娆忓弳閿涘牅缍嗘导妯哄帥缁狙冩躬閸撳稄绱?    params?.forEach { (k, v) -> if (k is String) env[k] = v }
    deps?.forEach { (k, v) -> if (k is String) env[k] = v }
    wf?.forEach { (k, v) -> if (k is String) env[k] = v }
    di?.forEach { (k, v) -> if (k is String) env[k] = v }

    // fn 閸楃姳缍呴敍鍫濈杽闂?fn.* 闁俺绻?aviator.addFunction 濞夈劌鍞介敍?    env["fn"] = "now, uuidShort, uuid, timestamp, random, randInt, lower, upper, trim, len"
    return env
  }

  /** 閹?`a.b.c` 鐠侯垰绶炴禒?env 閸欐牕鈧》绱欓弨顖涘瘮閻愮懓褰?+ List[int]閿?*/
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

  /** Aviator 閻喎鈧厧鍨介弬顓ㄧ窗娑?ExpressionEvaluator.evalBoolean 鐟欏嫬鍨稉鈧懛?*/
  private fun truthy(r: Any?): Boolean = when (r) {
    null -> false
    is Boolean -> r
    is Number -> r.toDouble() != 0.0
    is String -> r.isNotBlank()
    is Collection<*> -> r.isNotEmpty()
    is Map<*, *> -> r.isNotEmpty()
    else -> true
  }

  /** 鐏炴洖閽╂禒缁樺壈瀹撳苯顨滅紒鎾寸€稉?`prefix.key -> value.toString()` 閻?Map閿涘湗ieldMatcher 閻㈩煉绱?*/
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

  /** 濞ｈ鲸瀚圭拹婵撶窗闁灝鍘ら崨鎴掕厬闂堟瑦鈧?response 鏉╂柨娲栭惃?Map/List 鐞氼偄顦婚柈銊ゆ叏閺€纭呪偓灞捐杽閺?config */
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

  /** 鐟欙絾鐎?JSON 鐎涙顑佹稉鎻掑煂鐎电钖勯敍娑樸亼鐠愩儴绻戦崶?null閿涘牆顦婚柈?fallback 娑撳搫甯€涙顑佹稉璇х礆 */
  private fun parseJsonSafely(s: String): Any? = try {
    // Try to parse JSON string via Jackson reflection;
    // if Jackson is not on classpath, return null and let caller use raw string.
    val mapperCls = Class.forName("com.fasterxml.jackson.databind.ObjectMapper")
    val mapper = mapperCls.getDeclaredConstructor().newInstance()
    val readValue = mapperCls.getMethod("readValue", String::class.java, Class::class.java)
    readValue.invoke(mapper, s, Any::class.java)
  } catch (_: Exception) {
    null
  }
}

/**
 * Mock 濡剝婢?fn.* 瀹搞儱鍙块崙鑺ユ殶閿涘牓鈧俺绻?AbstractVariadicFunction 閸栧灝鎮曠€圭偟骞囧▔銊ュ斀娑?AviatorScript 閸忋劌鐪崙鑺ユ殶閿涘鈧? *
 * 娴犲懍绻氶悾娆庣瑹閸斅ゎ嚔娑斿妫ゅ▔鏇☆潶 Aviator 閸樼喓鏁撶憰鍡欐磰閻ㄥ嫰鍎撮崚鍡幢鐎涙顑佹稉?闂嗗棗鎮?濞搭喚鍋ｉ梾蹇旀簚缁涘鈧氨鏁ゅ銉ュ徔閻╁瓨甯撮悽顭掔窗
 *   - string.contains / substring / length / split / matches ...
 *   - math.abs / floor / ceil / round / max / min / pow / sqrt ...
 *   - seq.map / filter / reduce / count / include / sort / distinct ...
 *   - count(x) / include(col,x) / rand() 缁涘銆婄仦鍌氬敶缂冾喖鍤遍弫? */
object FnFunctions {
  @JvmStatic
  fun now(): Long = System.currentTimeMillis()

  @JvmStatic
  fun uuidShort(): String = java.util.UUID.randomUUID().toString().replace("-", "")

  @JvmStatic
  fun uuid(): String = java.util.UUID.randomUUID().toString()

  @JvmStatic
  fun timestamp(): Long = System.currentTimeMillis() / 1000L

  @JvmStatic
  fun randInt(start: Int, end: Int): Int = if (end <= start) 0 else start + (Math.random() * (end - start)).toInt()
}

/**
 * 閸栧懐楠囨笟鎸庡祹閸戣姤鏆熼敍鍫滅箽閹镐椒绗岄崢?JEXL 閻楀牊婀伴惃鍕殶閻劎鍋ｉ崗鐓庮啇閿? *   MockWorkflowFunction 閻╁瓨甯寸拫鍐暏 `evaluateMock(config, ref, input)` 閼板矂娼?`MockEngine.evaluate`閿涘鈧? */
@Suppress("unused")
fun evaluateMock(config: MockConfig, functionRef: String, nodeInput: NodeInput): Any? =
  MockEngine.evaluate(config, functionRef, nodeInput)
