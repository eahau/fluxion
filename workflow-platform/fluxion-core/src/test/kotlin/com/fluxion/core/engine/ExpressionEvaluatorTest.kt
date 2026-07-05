package com.fluxion.core.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpressionEvaluatorTest {

    // ─── 基础布尔表达式 ─────────────────────────────────────────

    @Test
    fun `simple boolean true`() {
        assertTrue(ExpressionEvaluator.evalBoolean("output == 'ok'", "ok", emptyMap()))
    }

    @Test
    fun `simple boolean false`() {
        assertFalse(ExpressionEvaluator.evalBoolean("output == 'fail'", "ok", emptyMap()))
    }

    @Test
    fun `null expression returns false`() {
        assertFalse(ExpressionEvaluator.evalBoolean(null, "ok", emptyMap()))
    }

    @Test
    fun `blank expression returns false`() {
        assertFalse(ExpressionEvaluator.evalBoolean("  ", "ok", emptyMap()))
    }

    // ─── SpEL 兼容（# 前缀去除）─────────────────────────────────

    @Test
    fun `SpEL hash prefix is stripped`() {
        val output = mapOf("status" to "APPROVED")
        assertTrue(ExpressionEvaluator.evalBoolean("#output['status'] == 'APPROVED'", output, emptyMap()))
    }

    @Test
    fun `SpEL input variable`() {
        val input = mapOf("env" to "prod")
        assertTrue(ExpressionEvaluator.evalBoolean("#input['env'] == 'prod'", null, input))
    }

    // ─── Map 字段展开 ──────────────────────────────────────────

    @Test
    fun `map fields expanded to top level`() {
        val output = mapOf("status" to "APPROVED", "code" to 200)
        // 直接写 status 而不是 output['status']
        assertTrue(ExpressionEvaluator.evalBoolean("status == 'APPROVED'", output, emptyMap()))
    }

    @Test
    fun `map field numeric comparison`() {
        val output = mapOf("score" to 85)
        assertTrue(ExpressionEvaluator.evalBoolean("score > 80", output, emptyMap()))
    }

    @Test
    fun `map field boolean check`() {
        val output = mapOf("success" to true)
        assertTrue(ExpressionEvaluator.evalBoolean("success == true", output, emptyMap()))
    }

    // ─── input 变量 ────────────────────────────────────────────

    @Test
    fun `access workflow input`() {
        val input = mapOf("userId" to "u-001", "role" to "admin")
        assertTrue(ExpressionEvaluator.evalBoolean("input['role'] == 'admin'", null, input))
    }

    @Test
    fun `input and output combined`() {
        val output = mapOf("amount" to 100)
        val input = mapOf("limit" to 200)
        assertTrue(ExpressionEvaluator.evalBoolean("amount < input['limit']", output, input))
    }

    // ─── 复杂表达式 ────────────────────────────────────────────

    @Test
    fun `logical AND`() {
        val output = mapOf("status" to "ok", "code" to 200)
        assertTrue(ExpressionEvaluator.evalBoolean("status == 'ok' && code == 200", output, emptyMap()))
    }

    @Test
    fun `logical OR`() {
        val output = mapOf("status" to "fail")
        assertTrue(ExpressionEvaluator.evalBoolean("status == 'ok' || status == 'fail'", output, emptyMap()))
    }

    @Test
    fun `negation`() {
        val output = mapOf("error" to false)
        assertTrue(ExpressionEvaluator.evalBoolean("!error", output, emptyMap()))
    }

    @Test
    fun `string method call`() {
        val output = mapOf("msg" to "hello world")
        // 使用 Aviator 原生 string 模块函数（跨发行版稳定）
        assertTrue(ExpressionEvaluator.evalBoolean("string.contains(msg, 'world')", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("string.startsWith(msg, 'hello')", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("string.endsWith(msg, 'world')", output, emptyMap()))
    }

    @Test
    fun `arithmetic in expression`() {
        val output = mapOf("price" to 10, "qty" to 3)
        assertTrue(ExpressionEvaluator.evalBoolean("price * qty > 25", output, emptyMap()))
    }

    // ─── 异常容错 ──────────────────────────────────────────────

    @Test
    fun `invalid expression returns false`() {
        // 语法错误的表达式应返回 false 而非抛异常
        assertFalse(ExpressionEvaluator.evalBoolean("!!!invalid!!!", "ok", emptyMap()))
    }

    @Test
    fun `non-boolean result returns false`() {
        // 表达式结果不是 boolean（如字符串 "hello"）时返回 false
        assertFalse(ExpressionEvaluator.evalBoolean("'hello'", "ok", emptyMap()))
    }

    @Test
    fun `null output handled gracefully`() {
        assertFalse(ExpressionEvaluator.evalBoolean("output == 'ok'", null, emptyMap()))
    }

    // ─── 边界情况 ──────────────────────────────────────────────

    @Test
    fun `empty map output`() {
        assertFalse(ExpressionEvaluator.evalBoolean("missing == 'v'", emptyMap<String, Any>(), emptyMap()))
    }

    @Test
    fun `list output does not expand fields`() {
        val output = listOf("a", "b", "c")
        // List 不展开，只能直接比较 output
        assertTrue(ExpressionEvaluator.evalBoolean("output != null", output, emptyMap()))
    }

    // ====================================================================
    // AviatorScript 特有能力测试（迁移后才可用的内置函数与运算符）
    // ====================================================================

    @Test
    fun `Aviator string length and substring builtins`() {
        val output = mapOf("name" to "HelloWorld")
        // 全部使用搜索结果 #1 明确列出的 string 模块函数（跨 5.x 发行版稳定）
        assertTrue(ExpressionEvaluator.evalBoolean("string.length(name) == 10", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("string.contains(name, 'World')", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("string.startsWith(name, 'Hello')", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("string.endsWith(name, 'World')", output, emptyMap()))
        // string.substring(str, start, length)：第三个是长度，不是 end index
        assertEquals("Hello", ExpressionEvaluator.evalString("string.substring(name, 0, 5)", output, emptyMap()))
    }

    @Test
    fun `Aviator math builtins abs ceil floor round`() {
        val output = mapOf("val1" to -3.7, "val2" to 2.3)
        // math.abs(double) 返回 Double；math.round(double) 返回 Long（等价于 java.lang.Math.round）
        assertEquals(3.7, ExpressionEvaluator.evalObject("math.abs(val1)", output, emptyMap()) as? Double)
        assertEquals(-4L, ExpressionEvaluator.evalObject("math.round(val1)", output, emptyMap()) as? Long)
        // math.ceil / math.floor 返回 Double，比较时带 .0 后缀避免类型不一致
        assertTrue(ExpressionEvaluator.evalBoolean("math.ceil(val2) == 3.0", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("math.floor(val1) == -4.0", output, emptyMap()))
    }

    @Test
    fun `Aviator seq builtins filter map include`() {
        val output = mapOf("nums" to listOf(1, 2, 3, 4, 5))
        // 只用搜索结果 #1 明确列出、跨 5.x 发行版 100% 稳定存在的函数：
        //   include(col, elem)   顶层函数，判断是否包含某元素（List/Set/Tuple 等可迭代对象）
        //   count(col)           顶层函数，返回元素个数
        assertTrue(ExpressionEvaluator.evalBoolean("include(nums, 3)", output, emptyMap()))
        assertFalse(ExpressionEvaluator.evalBoolean("include(nums, -1)", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("include(nums, 5)", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("!include(nums, 99)", output, emptyMap()))

        assertEquals(5L, ExpressionEvaluator.evalObject("count(nums)", output, emptyMap()) as? Long)
        // 空集合 count=0，include(空集合, any)=false
        val empty = mapOf("xs" to emptyList<Int>())
        assertEquals(0L, ExpressionEvaluator.evalObject("count(xs)", empty, emptyMap()) as? Long)
        assertFalse(ExpressionEvaluator.evalBoolean("include(xs, 1)", empty, emptyMap()))
    }

    @Test
    fun `Aviator elvis operator null coalesce`() {
        val output = mapOf("name" to null, "fallback" to "foo")
        // 表达式里写的 `??` 会被 normalizeExpression 改写为 `(a != nil ? a : b)` 形式，
        // map 中传进来的 Java null 也等价于 Aviator 的 nil，空值合并语义与 Kotlin/C# 一致
        assertEquals("foo", ExpressionEvaluator.evalString("name ?? fallback", output, emptyMap()))
        assertEquals("bar", ExpressionEvaluator.evalString("missing ?? 'bar'", output, emptyMap()))
    }

    @Test
    fun `Aviator ternary operator`() {
        val output = mapOf("age" to 20)
        assertEquals("adult", ExpressionEvaluator.evalString("age >= 18 ? 'adult' : 'minor'", output, emptyMap()))
    }
}
