package com.fluxion.core.engine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class ExpressionEvaluatorTest {
    
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
    
    @Test
    fun `map fields expanded to top level`() {
        val output = mapOf("status" to "APPROVED", "code" to 200)
        
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
        
        assertTrue(ExpressionEvaluator.evalBoolean("string.startsWith(msg, 'hello')", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("string.endsWith(msg, 'world')", output, emptyMap()))
    }
    @Test
    fun `arithmetic in expression`() {
        val output = mapOf("price" to 10, "qty" to 3)
        assertTrue(ExpressionEvaluator.evalBoolean("price * qty > 25", output, emptyMap()))
    }
    
    @Test
    fun `invalid expression returns false`() {
        
    }
    @Test
    fun `non-boolean result returns false`() {
        
        assertFalse(ExpressionEvaluator.evalBoolean("'hello'", "ok", emptyMap()))
    }
    @Test
    fun `null output handled gracefully`() {
        assertFalse(ExpressionEvaluator.evalBoolean("output == 'ok'", null, emptyMap()))
    }
    
    @Test
    fun `empty map output`() {
        assertFalse(ExpressionEvaluator.evalBoolean("missing == 'v'", emptyMap<String, Any>(), emptyMap()))
    }
    @Test
    fun `list output does not expand fields`() {
        val output = listOf("a", "b", "c")
        
        assertTrue(ExpressionEvaluator.evalBoolean("output != null", output, emptyMap()))
    }
    
    
    @Test
    fun `Aviator string length and substring builtins`() {
        val output = mapOf("name" to "HelloWorld")
        
        assertTrue(ExpressionEvaluator.evalBoolean("string.contains(name, 'World')", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("string.startsWith(name, 'Hello')", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("string.endsWith(name, 'World')", output, emptyMap()))
        
        assertEquals("Hello", ExpressionEvaluator.evalString("string.substring(name, 0, 5)", output, emptyMap()))
    }
    @Test
    fun `Aviator math builtins abs ceil floor round`() {
        val output = mapOf("val1" to -3.7, "val2" to 2.3)
        
        assertEquals(-4L, ExpressionEvaluator.evalObject("math.round(val1)", output, emptyMap()) as? Long)
        
        assertTrue(ExpressionEvaluator.evalBoolean("math.floor(val1) == -4.0", output, emptyMap()))
    }
    @Test
    fun `Aviator seq builtins filter map include`() {
        val output = mapOf("nums" to listOf(1, 2, 3, 4, 5))
        
        assertFalse(ExpressionEvaluator.evalBoolean("include(nums, -1)", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("include(nums, 5)", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("!include(nums, 99)", output, emptyMap()))
        assertEquals(5L, ExpressionEvaluator.evalObject("count(nums)", output, emptyMap()) as? Long)
        
        val empty = mapOf("xs" to emptyList<Int>())
        assertEquals(0L, ExpressionEvaluator.evalObject("count(xs)", empty, emptyMap()) as? Long)
        assertFalse(ExpressionEvaluator.evalBoolean("include(xs, 1)", empty, emptyMap()))
    }
    @Test
    fun `Aviator elvis operator null coalesce`() {
        val output = mapOf("name" to null, "fallback" to "foo")
        
        assertEquals("bar", ExpressionEvaluator.evalString("missing ?? 'bar'", output, emptyMap()))
    }
    @Test
    fun `Aviator ternary operator`() {
        val output = mapOf("age" to 20)
        assertEquals("adult", ExpressionEvaluator.evalString("age >= 18 ? 'adult' : 'minor'", output, emptyMap()))
    }
}
