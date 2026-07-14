package com.fluxion.core.engine
import com.fluxion.core.model.FilterRule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class RuleEvaluatorTest {
    
    @Test
    fun `eq with exact match`() {
        val output = mapOf("status" to "APPROVED")
        assertTrue(RuleEvaluator.evalRule("status", "eq", "APPROVED", output))
    }
    @Test
    fun `eq with string coercion`() {
        val output = mapOf("code" to 200)
        assertTrue(RuleEvaluator.evalRule("code", "eq", "200", output))
    }
    @Test
    fun `eq with ==  alias`() {
        assertTrue(RuleEvaluator.evalRule("x", "==", "1", mapOf("x" to "1")))
    }
    @Test
    fun `neq returns false for equal values`() {
        assertFalse(RuleEvaluator.evalRule("status", "neq", "ok", mapOf("status" to "ok")))
    }
    @Test
    fun `neq returns true for different values`() {
        assertTrue(RuleEvaluator.evalRule("status", "neq", "fail", mapOf("status" to "ok")))
    }
    
    @Test
    fun `gt compares numerically`() {
        val output = mapOf("score" to 85)
        assertTrue(RuleEvaluator.evalRule("score", "gt", 80, output))
        assertFalse(RuleEvaluator.evalRule("score", "gt", 85, output))
    }
    @Test
    fun `gte includes equal`() {
        val output = mapOf("score" to 85)
        assertTrue(RuleEvaluator.evalRule("score", "gte", 85, output))
    }
    @Test
    fun `lt compares numerically`() {
        val output = mapOf("age" to 18)
        assertTrue(RuleEvaluator.evalRule("age", "lt", 20, output))
        assertFalse(RuleEvaluator.evalRule("age", "lt", 18, output))
    }
    @Test
    fun `lte includes equal`() {
        assertTrue(RuleEvaluator.evalRule("age", "lte", 18, mapOf("age" to 18)))
    }
    @Test
    fun `numeric comparison with string numbers`() {
        val output = mapOf("price" to "99.9")
        assertTrue(RuleEvaluator.evalRule("price", "gt", "50", output))
    }
    
    @Test
    fun `in with list`() {
        val output = mapOf("role" to "admin")
        assertTrue(RuleEvaluator.evalRule("role", "in", listOf("admin", "superadmin"), output))
        assertFalse(RuleEvaluator.evalRule("role", "in", listOf("user", "guest"), output))
    }
    @Test
    fun `in with comma-separated string`() {
        val output = mapOf("env" to "dev")
        assertTrue(RuleEvaluator.evalRule("env", "in", "dev, staging, prod", output))
    }
    @Test
    fun `notIn negates in`() {
        val output = mapOf("role" to "guest")
        assertTrue(RuleEvaluator.evalRule("role", "notIn", listOf("admin", "superadmin"), output))
        assertTrue(RuleEvaluator.evalRule("role", "not_in", listOf("admin"), output))
    }
    
    @Test
    fun `contains checks substring`() {
        val output = mapOf("msg" to "hello world")
        assertTrue(RuleEvaluator.evalRule("msg", "contains", "world", output))
        assertFalse(RuleEvaluator.evalRule("msg", "contains", "foo", output))
    }
    @Test
    fun `startsWith checks prefix`() {
        val output = mapOf("url" to "https:
        assertTrue(RuleEvaluator.evalRule("url", "startsWith", "https", output))
        assertTrue(RuleEvaluator.evalRule("url", "starts_with", "https", output))
    }
    @Test
    fun `endsWith checks suffix`() {
        val output = mapOf("file" to "report.pdf")
        assertTrue(RuleEvaluator.evalRule("file", "endsWith", ".pdf", output))
        assertTrue(RuleEvaluator.evalRule("file", "ends_with", ".pdf", output))
    }
    
    @Test
    fun `isNull returns true for missing field`() {
        val output = mapOf("a" to 1)
        assertTrue(RuleEvaluator.evalRule("b", "isNull", null, output))
        assertTrue(RuleEvaluator.evalRule("b", "is_null", null, output))
    }
    @Test
    fun `isNotNull returns true for existing field`() {
        val output = mapOf("a" to 1)
        assertTrue(RuleEvaluator.evalRule("a", "isNotNull", null, output))
        assertTrue(RuleEvaluator.evalRule("a", "is_not_null", null, output))
    }
    
    @Test
    fun `regex matches pattern`() {
        val output = mapOf("email" to "user@example.com")
        assertTrue(RuleEvaluator.evalRule("email", "regex", "^.+@.+\\..+$", output))
    }
    @Test
    fun `regex returns false for invalid pattern`() {
        val output = mapOf("data" to "test")
        assertFalse(RuleEvaluator.evalRule("data", "regex", "[invalid", output))
    }
    
    @Test
    fun `nested field path`() {
        val output = mapOf("user" to mapOf("address" to mapOf("city" to "Shanghai")))
        assertTrue(RuleEvaluator.evalRule("user.address.city", "eq", "Shanghai", output))
    }
    @Test
    fun `nested path returns null for missing intermediate`() {
        val output = mapOf("user" to mapOf("name" to "alice"))
        assertTrue(RuleEvaluator.evalRule("user.address.city", "isNull", null, output))
    }
    
    @Test
    fun `non-map output with field output`() {
        assertTrue(RuleEvaluator.evalRule("output", "eq", "success", "success"))
    }
    @Test
    fun `non-map output with other field returns null`() {
        assertTrue(RuleEvaluator.evalRule("status", "isNull", null, "not a map"))
    }
    @Test
    fun `blank field returns whole output`() {
        assertTrue(RuleEvaluator.evalRule("", "eq", "hello", "hello"))
    }
    
    @Test
    fun `unknown operator returns false`() {
        assertFalse(RuleEvaluator.evalRule("x", "unknown_op", "v", mapOf("x" to "v")))
    }
    
    @Test
    fun `compound rules AND all true`() {
        val rules = listOf(
            mapOf("field" to "status", "operator" to "eq", "value" to "ok"),
            mapOf("field" to "code", "operator" to "eq", "value" to 200)
        )
        val output = mapOf("status" to "ok", "code" to 200)
        assertTrue(RuleEvaluator.evalCompoundRules(rules, "and", output))
    }
    @Test
    fun `compound rules AND one false`() {
        val rules = listOf(
            mapOf("field" to "status", "operator" to "eq", "value" to "ok"),
            mapOf("field" to "code", "operator" to "eq", "value" to 500)
        )
        val output = mapOf("status" to "ok", "code" to 200)
        assertFalse(RuleEvaluator.evalCompoundRules(rules, "and", output))
    }
    @Test
    fun `compound rules OR one true`() {
        val rules = listOf(
            mapOf("field" to "status", "operator" to "eq", "value" to "fail"),
            mapOf("field" to "code", "operator" to "eq", "value" to 200)
        )
        val output = mapOf("status" to "ok", "code" to 200)
        assertTrue(RuleEvaluator.evalCompoundRules(rules, "or", output))
    }
    @Test
    fun `compound rules OR all false`() {
        val rules = listOf(
            mapOf("field" to "status", "operator" to "eq", "value" to "fail"),
            mapOf("field" to "code", "operator" to "eq", "value" to 500)
        )
        val output = mapOf("status" to "ok", "code" to 200)
        assertFalse(RuleEvaluator.evalCompoundRules(rules, "or", output))
    }
    @Test
    fun `empty rules returns false`() {
        assertFalse(RuleEvaluator.evalCompoundRules(emptyList<Any>(), "and", mapOf("a" to 1)))
    }
    @Test
    fun `default logic is AND`() {
        val rules = listOf(
            mapOf("field" to "a", "operator" to "eq", "value" to 1),
            mapOf("field" to "b", "operator" to "eq", "value" to 2)
        )
        val output = mapOf("a" to 1, "b" to 2)
        
        assertTrue(RuleEvaluator.evalCompoundRules(rules, "default", output))
    }
    
    @Test
    fun `negate inverts single rule result`() {
        val output = mapOf("status" to "ok")
        assertFalse(RuleEvaluator.evalRule("status", "neq", "ok", output))
        assertFalse(RuleEvaluator.evalRule("status", "eq", "ok", output, negate = true))
        assertTrue(RuleEvaluator.evalRule("status", "neq", "ok", output, negate = true))
    }
    @Test
    fun `negate with contains operator`() {
        val output = mapOf("msg" to "hello world")
        assertFalse(RuleEvaluator.evalRule("msg", "contains", "world", output, negate = true))
        assertTrue(RuleEvaluator.evalRule("msg", "contains", "foo", output, negate = true))
    }
    @Test
    fun `compound rules with negate`() {
        val rules = listOf(
            mapOf("field" to "status", "operator" to "eq", "value" to "ok", "negate" to true),
            mapOf("field" to "code", "operator" to "eq", "value" to 200)
        )
        val output = mapOf("status" to "ok", "code" to 200)
        
        assertFalse(RuleEvaluator.evalCompoundRules(rules, "and", output))
        
        assertTrue(RuleEvaluator.evalCompoundRules(rules, "or", output))
    }
    @Test
    fun `evalFilterRules with negate`() {
        val rules = listOf(
            FilterRule(field = "status", operator = "eq", value = "ok", negate = true),
            FilterRule(field = "code", operator = "eq", value = 200)
        )
        val output = mapOf("status" to "ok", "code" to 200)
        assertFalse(RuleEvaluator.evalFilterRules(rules, "and", output))
        assertTrue(RuleEvaluator.evalFilterRules(rules, "or", output))
    }
    
    @Test
    fun `neq is equivalent to eq with negate`() {
        val output = mapOf("status" to "ok", "code" to 200)
        assertEquals(
            RuleEvaluator.evalRule("status", "neq", "ok", output),
            RuleEvaluator.evalRule("status", "eq", "ok", output, negate = true)
        )
        assertEquals(
            RuleEvaluator.evalRule("status", "neq", "fail", output),
            RuleEvaluator.evalRule("status", "eq", "fail", output, negate = true)
        )
        assertEquals(
            RuleEvaluator.evalRule("code", "neq", 200, output),
            RuleEvaluator.evalRule("code", "eq", 200, output, negate = true)
        )
        
        assertEquals(
            RuleEvaluator.evalRule("status", "neq", "ok", output, negate = true),
            RuleEvaluator.evalRule("status", "eq", "ok", output)
        )
    }
    @Test
    fun `notIn is equivalent to in with negate`() {
        val output = mapOf("role" to "guest")
        val list = listOf("admin", "superadmin")
        assertEquals(
            RuleEvaluator.evalRule("role", "notIn", list, output),
            RuleEvaluator.evalRule("role", "in", list, output, negate = true)
        )
        
        assertEquals(
            RuleEvaluator.evalRule("role", "not_in", list, output),
            RuleEvaluator.evalRule("role", "in", list, output, negate = true)
        )
    }
    @Test
    fun `isNotNull is equivalent to isNull with negate`() {
        val output = mapOf("a" to 1)
        assertEquals(
            RuleEvaluator.evalRule("a", "isNotNull", null, output),
            RuleEvaluator.evalRule("a", "isNull", null, output, negate = true)
        )
        assertEquals(
            RuleEvaluator.evalRule("missing", "isNotNull", null, output),
            RuleEvaluator.evalRule("missing", "isNull", null, output, negate = true)
        )
    }
    @Test
    fun `negating operator aliases with negate true cancel out`() {
        
        assertTrue(RuleEvaluator.evalRule("status", "neq", "ok", mapOf("status" to "ok"), negate = true))
        
        assertTrue(
            RuleEvaluator.evalRule(
                "role",
                "notIn",
                listOf("admin", "superadmin"),
                mapOf("role" to "admin"),
                negate = true
            )
        )
        
        assertTrue(RuleEvaluator.evalRule("missing", "isNotNull", null, mapOf("a" to 1), negate = true))
    }
    
    @Test
    fun `per-rule logic mixing AND and OR - first match pattern`() {
        
        
        val rules = listOf(
            mapOf("field" to "status", "operator" to "eq", "value" to "ok"),
            mapOf("field" to "code", "operator" to "eq", "value" to 200, "logic" to "and"),
            mapOf("field" to "role", "operator" to "eq", "value" to "admin", "logic" to "or"),
        )
        
        assertTrue(
            RuleEvaluator.evalCompoundRules(
                rules, defaultLogic = "and", nodeOutput =
                mapOf("status" to "ok", "code" to 200, "role" to "guest")
            )
        )
        
        assertTrue(
            RuleEvaluator.evalCompoundRules(
                rules, defaultLogic = "and", nodeOutput =
                mapOf("status" to "fail", "code" to 200, "role" to "admin")
            )
        )
        
        assertFalse(
            RuleEvaluator.evalCompoundRules(
                rules, defaultLogic = "and", nodeOutput =
                mapOf("status" to "ok", "code" to 500, "role" to "guest")
            )
        )
    }
    @Test
    fun `per-rule logic falls back to defaultLogic when missing on a rule`() {
        
        val rules = listOf(
            mapOf("field" to "a", "operator" to "eq", "value" to 1),
            mapOf("field" to "b", "operator" to "eq", "value" to 2),
        )
        val output = mapOf("a" to 1, "b" to 99)
        
        assertTrue(RuleEvaluator.evalCompoundRules(rules, defaultLogic = "or", nodeOutput = output))
        
        assertFalse(RuleEvaluator.evalCompoundRules(rules, defaultLogic = "and", nodeOutput = output))
    }
    @Test
    fun `per-rule logic works with FilterRule (typesafe) form too`() {
        val rules = listOf(
            FilterRule("x", "eq", 1),
            FilterRule("y", "eq", 2, logic = "or"),
            FilterRule("z", "eq", 3, logic = "and"),
        )
        
        
        val out = mapOf("x" to 1, "y" to 99, "z" to 99)
        assertFalse(RuleEvaluator.evalFilterRules(rules, defaultLogic = "and", nodeOutput = out))
        
        val out2 = mapOf("x" to 1, "y" to 99, "z" to 3)
        assertTrue(RuleEvaluator.evalFilterRules(rules, defaultLogic = "and", nodeOutput = out2))
    }
    
    @Test
    fun `globalLogicEnabled overrides per-rule logic to AND`() {
        val rules = listOf(
            mapOf("field" to "a", "operator" to "eq", "value" to 1),
            mapOf(
                "field" to "b",
                "operator" to "eq",
                "value" to 2,
                "logic" to "or"
            ), 
            mapOf("field" to "c", "operator" to "eq", "value" to 3, "logic" to "or"), 
        )
        val output = mapOf("a" to 1, "b" to 2, "c" to 99)
        
        assertTrue(
            RuleEvaluator.evalCompoundRules(
                rules = rules,
                defaultLogic = "and",
                nodeOutput = output,
                globalLogicEnabled = false,
            )
        )
        
        assertFalse(
            RuleEvaluator.evalCompoundRules(
                rules = rules,
                defaultLogic = "and",
                nodeOutput = output,
                globalLogicEnabled = true,
                globalLogicOperator = "and",
            )
        )
        
        assertTrue(
            RuleEvaluator.evalCompoundRules(
                rules = rules,
                defaultLogic = "and",
                nodeOutput = mapOf("a" to 99, "b" to 99, "c" to 3),
                globalLogicEnabled = true,
                globalLogicOperator = "or",
            )
        )
    }
    @Test
    fun `globalLogicEnabled overrides per-rule logic on FilterRule (typesafe)`() {
        val rules = listOf(
            FilterRule("x", "eq", 1),
            FilterRule("y", "eq", 2, logic = "and"), 
            FilterRule("z", "eq", 3, logic = "and"),
        )
        
        val out = mapOf("x" to 99, "y" to 99, "z" to 3)
        assertTrue(
            RuleEvaluator.evalFilterRules(
                rules = rules,
                defaultLogic = "and",
                nodeOutput = out,
                globalLogicEnabled = true,
                globalLogicOperator = "or",
            )
        )
        
        assertFalse(
            RuleEvaluator.evalFilterRules(
                rules = rules,
                defaultLogic = "and",
                nodeOutput = out,
                globalLogicEnabled = false,
            )
        )
    }
    
    @Test
    fun `using isNull + negate equals isNotNull behavior`() {
        val data = mapOf("name" to "Alice", "email" to null)
        
        val nameNotNull = RuleEvaluator.evalRule("name", "isNull", null, data, negate = true)
        assertTrue(nameNotNull)
        
        val emailIsNull = RuleEvaluator.evalRule("email", "isNull", null, data)
        assertTrue(emailIsNull)
        
        val emailNotNull = RuleEvaluator.evalRule("email", "isNull", null, data, negate = true)
        assertFalse(emailNotNull)
        
        assertEquals(RuleEvaluator.evalRule("name", "isNotNull", null, data), nameNotNull)
        assertEquals(RuleEvaluator.evalRule("email", "isNotNull", null, data), emailNotNull)
    }
    @Test
    fun `using in + negate equals notIn behavior`() {
        val data = mapOf("role" to "guest")
        val adminList = listOf("admin", "superadmin")
        
        val guestNotInAdmin = RuleEvaluator.evalRule("role", "in", adminList, data, negate = true)
        assertTrue(guestNotInAdmin)
        
        assertEquals(RuleEvaluator.evalRule("role", "notIn", adminList, data), guestNotInAdmin)
        
        val adminNotInAdmin = RuleEvaluator.evalRule("role", "in", listOf("guest"), data, negate = true)
        assertFalse(adminNotInAdmin)
    }
    @Test
    fun `using eq + negate equals neq behavior`() {
        val data = mapOf("code" to 200)
        assertEquals(
            RuleEvaluator.evalRule("code", "neq", 200, data),
            RuleEvaluator.evalRule("code", "eq", 200, data, negate = true)
        )
        assertEquals(
            RuleEvaluator.evalRule("code", "neq", 500, data),
            RuleEvaluator.evalRule("code", "eq", 500, data, negate = true)
        )
    }
    
    @Test
    fun `isEmpty - string cases`() {
        assertTrue(RuleEvaluator.evalRule("a", "isEmpty", null, mapOf("a" to "")))
        assertTrue(RuleEvaluator.evalRule("a", "isEmpty", null, mapOf("a" to "   ")))
        assertFalse(RuleEvaluator.evalRule("a", "isEmpty", null, mapOf("a" to "x")))
        
        assertTrue(RuleEvaluator.evalRule("a", "isEmpty", null, mapOf("a" to null)))
    }
    @Test
    fun `isEmpty - collection cases`() {
        assertTrue(RuleEvaluator.evalRule("items", "isEmpty", null, mapOf("items" to emptyList<Any>())))
        assertFalse(RuleEvaluator.evalRule("items", "isEmpty", null, mapOf("items" to listOf(1, 2, 3))))
        assertTrue(RuleEvaluator.evalRule("meta", "isEmpty", null, mapOf("meta" to emptyMap<Any, Any>())))
        assertFalse(RuleEvaluator.evalRule("meta", "isEmpty", null, mapOf("meta" to mapOf("a" to 1))))
    }
    @Test
    fun `isEmpty + negate = isNotEmpty`() {
        val data = mapOf("items" to listOf(1, 2))
        assertTrue(RuleEvaluator.evalRule("items", "isEmpty", null, data, negate = true))
        assertFalse(RuleEvaluator.evalRule("items", "isEmpty", null, mapOf("items" to emptyList<Any>()), negate = true))
    }
    
    @Test
    fun `sizeEq for list string map`() {
        assertTrue(RuleEvaluator.evalRule("items", "sizeEq", 3, mapOf("items" to listOf(1, 2, 3))))
        assertFalse(RuleEvaluator.evalRule("items", "sizeEq", 2, mapOf("items" to listOf(1, 2, 3))))
        assertTrue(RuleEvaluator.evalRule("s", "sizeEq", 5, mapOf("s" to "hello")))
        assertTrue(RuleEvaluator.evalRule("m", "sizeEq", 2, mapOf("m" to mapOf("a" to 1, "b" to 2))))
    }
    @Test
    fun `sizeGte sizeGt - array user requirement style size GTE N for items`() {
        val output = mapOf("items" to listOf("p1", "p2", "p3"))
        
        assertTrue(RuleEvaluator.evalRule("items", "sizeGte", 3, output))
        assertTrue(RuleEvaluator.evalRule("items", "sizeGte", 2, output))
        assertFalse(RuleEvaluator.evalRule("items", "sizeGte", 4, output))
        assertTrue(RuleEvaluator.evalRule("items", "sizeGt", 2, output))
        assertFalse(RuleEvaluator.evalRule("items", "sizeGt", 3, output))
    }
    @Test
    fun `sizeLte sizeLt boundary`() {
        val output = mapOf("items" to listOf(1, 2))
        assertTrue(RuleEvaluator.evalRule("items", "sizeLte", 2, output))
        assertTrue(RuleEvaluator.evalRule("items", "sizeLte", 5, output))
        assertFalse(RuleEvaluator.evalRule("items", "sizeLte", 1, output))
        assertTrue(RuleEvaluator.evalRule("items", "sizeLt", 3, output))
        assertFalse(RuleEvaluator.evalRule("items", "sizeLt", 2, output))
    }
    @Test
    fun `size* operators return false for unknown or null values`() {
        
        assertFalse(RuleEvaluator.evalRule("n", "sizeEq", 1, mapOf("n" to 42)))
        
        assertFalse(RuleEvaluator.evalRule("x", "sizeGte", 0, mapOf<String, Any?>("x" to null)))
        
        assertTrue(RuleEvaluator.evalRule("n", "sizeEq", 1, mapOf("n" to 42), negate = true))
    }
    
    
    
    @Test
    fun `array field - allowed operators behave as expected`() {
        val data = mapOf(
            "items" to listOf("apple", "banana", "cherry"),
            "emptyItems" to emptyList<Any>(),
            "nullItems" to null,
        )
        
        assertFalse(RuleEvaluator.evalRule("items", "isNull", null, data))
        assertTrue(RuleEvaluator.evalRule("nullItems", "isNull", null, data))
        
        assertFalse(RuleEvaluator.evalRule("items", "isEmpty", null, data))
        assertTrue(RuleEvaluator.evalRule("emptyItems", "isEmpty", null, data))
        
        assertTrue(RuleEvaluator.evalRule("items", "sizeGte", 3, data))
        assertFalse(RuleEvaluator.evalRule("items", "sizeGte", 4, data))
        
        assertTrue(RuleEvaluator.evalRule("items", "contains", "banana", data))
        assertFalse(RuleEvaluator.evalRule("items", "contains", "grape", data))
        
    }
    @Test
    fun `array field - string-only operators return based on toString form but must not throw`() {
        val data = mapOf("items" to listOf("apple", "banana"))
        
        assertFalse(RuleEvaluator.evalRule("items", "startsWith", "ZZZZ_NOPE", data))
        assertFalse(RuleEvaluator.evalRule("items", "endsWith", "ZZZZ_NOPE", data))
        assertFalse(RuleEvaluator.evalRule("items", "regex", "ZZZZ_NOPE_\\d+", data))
    }
}
