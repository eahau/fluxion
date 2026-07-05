package com.fluxion.core.engine

import com.fluxion.core.model.FilterRule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RuleEvaluatorTest {

    // ─── eq / neq ───────────────────────────────────────────────

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

    // ─── gt / gte / lt / lte ───────────────────────────────────

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

    // ─── in / notIn ────────────────────────────────────────────

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

    // ─── string operators ───────────────────────────────────────

    @Test
    fun `contains checks substring`() {
        val output = mapOf("msg" to "hello world")
        assertTrue(RuleEvaluator.evalRule("msg", "contains", "world", output))
        assertFalse(RuleEvaluator.evalRule("msg", "contains", "foo", output))
    }

    @Test
    fun `startsWith checks prefix`() {
        val output = mapOf("url" to "https://example.com")
        assertTrue(RuleEvaluator.evalRule("url", "startsWith", "https", output))
        assertTrue(RuleEvaluator.evalRule("url", "starts_with", "https", output))
    }

    @Test
    fun `endsWith checks suffix`() {
        val output = mapOf("file" to "report.pdf")
        assertTrue(RuleEvaluator.evalRule("file", "endsWith", ".pdf", output))
        assertTrue(RuleEvaluator.evalRule("file", "ends_with", ".pdf", output))
    }

    // ─── null checks ───────────────────────────────────────────

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

    // ─── regex ──────────────────────────────────────────────────

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

    // ─── 嵌套路径 ──────────────────────────────────────────────

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

    // ─── 非 Map 输出 ───────────────────────────────────────────

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

    // ─── unknown operator ───────────────────────────────────────

    @Test
    fun `unknown operator returns false`() {
        assertFalse(RuleEvaluator.evalRule("x", "unknown_op", "v", mapOf("x" to "v")))
    }

    // ─── compound rules ─────────────────────────────────────────

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
        // "default" (not "or") should behave as AND
        assertTrue(RuleEvaluator.evalCompoundRules(rules, "default", output))
    }

    // ─── negate ─────────────────────────────────────────────────

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
        // NOT ok AND code == 200 → false AND true → false
        assertFalse(RuleEvaluator.evalCompoundRules(rules, "and", output))

        // NOT ok OR code == 200 → false OR true → true
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

    // ─── 否定型 operator 与基础 operator + negate 的等价性 ─────────────────

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
        // 显式双重取反：neq + negate=true 应等价于 eq
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
        // snake_case 别名同样应归一化
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
        // neq(value=实际值) + negate=true == eq
        assertTrue(RuleEvaluator.evalRule("status", "neq", "ok", mapOf("status" to "ok"), negate = true))
        // notIn + negate=true == in
        assertTrue(
            RuleEvaluator.evalRule(
                "role",
                "notIn",
                listOf("admin", "superadmin"),
                mapOf("role" to "admin"),
                negate = true
            )
        )
        // isNotNull + negate=true == isNull
        assertTrue(RuleEvaluator.evalRule("missing", "isNotNull", null, mapOf("a" to 1), negate = true))
    }

    // ─── 规则级独立 logic（每条规则可单独指定 AND/OR）────────────

    @Test
    fun `per-rule logic mixing AND and OR - first match pattern`() {
        // 场景：(status == 'ok') AND (code == 200) OR (role == 'admin')
        // 第 1 条 (index=0) 忽略 logic；第 2 条 logic=AND；第 3 条 logic=OR
        val rules = listOf(
            mapOf("field" to "status", "operator" to "eq", "value" to "ok"),
            mapOf("field" to "code", "operator" to "eq", "value" to 200, "logic" to "and"),
            mapOf("field" to "role", "operator" to "eq", "value" to "admin", "logic" to "or"),
        )
        // T AND T OR F = T
        assertTrue(RuleEvaluator.evalCompoundRules(rules, defaultLogic = "and", nodeOutput =
            mapOf("status" to "ok", "code" to 200, "role" to "guest")))
        // F AND T OR T = T
        assertTrue(RuleEvaluator.evalCompoundRules(rules, defaultLogic = "and", nodeOutput =
            mapOf("status" to "fail", "code" to 200, "role" to "admin")))
        // T AND F OR F = F
        assertFalse(RuleEvaluator.evalCompoundRules(rules, defaultLogic = "and", nodeOutput =
            mapOf("status" to "ok", "code" to 500, "role" to "guest")))
    }

    @Test
    fun `per-rule logic falls back to defaultLogic when missing on a rule`() {
        // 第 2 条规则缺失 logic 字段 → 回退到 defaultLogic=or
        val rules = listOf(
            mapOf("field" to "a", "operator" to "eq", "value" to 1),
            mapOf("field" to "b", "operator" to "eq", "value" to 2),
        )
        val output = mapOf("a" to 1, "b" to 99)
        // defaultLogic = or → a==1 OR b==2 → T
        assertTrue(RuleEvaluator.evalCompoundRules(rules, defaultLogic = "or", nodeOutput = output))
        // defaultLogic = and → a==1 AND b==2 → F
        assertFalse(RuleEvaluator.evalCompoundRules(rules, defaultLogic = "and", nodeOutput = output))
    }

    @Test
    fun `per-rule logic works with FilterRule (typesafe) form too`() {
        val rules = listOf(
            FilterRule("x", "eq", 1),
            FilterRule("y", "eq", 2, logic = "or"),
            FilterRule("z", "eq", 3, logic = "and"),
        )
        // x=1 OR y=? AND z=?
        // 计算过程：(x=1)=T → (OR y=99)=T → (AND z=99)=F
        val out = mapOf("x" to 1, "y" to 99, "z" to 99)
        assertFalse(RuleEvaluator.evalFilterRules(rules, defaultLogic = "and", nodeOutput = out))

        // x=1 OR y=? AND z=3 → F after AND
        val out2 = mapOf("x" to 1, "y" to 99, "z" to 3)
        assertTrue(RuleEvaluator.evalFilterRules(rules, defaultLogic = "and", nodeOutput = out2))
    }

    // ─── 全局逻辑开关（覆盖每条规则的独立 logic）─────────────────

    @Test
    fun `globalLogicEnabled overrides per-rule logic to AND`() {
        val rules = listOf(
            mapOf("field" to "a", "operator" to "eq", "value" to 1),
            mapOf("field" to "b", "operator" to "eq", "value" to 2, "logic" to "or"), // 本条声明 OR 但被全局覆盖
            mapOf("field" to "c", "operator" to "eq", "value" to 3, "logic" to "or"), // 同上
        )
        val output = mapOf("a" to 1, "b" to 2, "c" to 99)

        // 未启用全局：默认 AND，但 2、3 条显式 OR → (1==1 OR 2==2) OR c==3 → T
        assertTrue(RuleEvaluator.evalCompoundRules(
            rules = rules,
            defaultLogic = "and",
            nodeOutput = output,
            globalLogicEnabled = false,
        ))

        // 启用全局 AND：1==1 AND 2==2 AND c==3 → F
        assertFalse(RuleEvaluator.evalCompoundRules(
            rules = rules,
            defaultLogic = "and",
            nodeOutput = output,
            globalLogicEnabled = true,
            globalLogicOperator = "and",
        ))

        // 启用全局 OR：1==1 OR 任何 → T
        assertTrue(RuleEvaluator.evalCompoundRules(
            rules = rules,
            defaultLogic = "and",
            nodeOutput = mapOf("a" to 99, "b" to 99, "c" to 3),
            globalLogicEnabled = true,
            globalLogicOperator = "or",
        ))
    }

    @Test
    fun `globalLogicEnabled overrides per-rule logic on FilterRule (typesafe)`() {
        val rules = listOf(
            FilterRule("x", "eq", 1),
            FilterRule("y", "eq", 2, logic = "and"), // 被全局覆盖
            FilterRule("z", "eq", 3, logic = "and"),
        )
        // 启用全局 OR：x=? OR y=? OR z=3 → T 只要任一成立
        val out = mapOf("x" to 99, "y" to 99, "z" to 3)
        assertTrue(RuleEvaluator.evalFilterRules(
            rules = rules,
            defaultLogic = "and",
            nodeOutput = out,
            globalLogicEnabled = true,
            globalLogicOperator = "or",
        ))
        // 未启用全局（默认 AND，每规则也是 AND）：全 AND 成立需全部为 T → F
        assertFalse(RuleEvaluator.evalFilterRules(
            rules = rules,
            defaultLogic = "and",
            nodeOutput = out,
            globalLogicEnabled = false,
        ))
    }

    // ─── negate 替代反向操作符的集成验证（UI 层不再暴露 isNotNull/notIn/neq）

    @Test
    fun `using isNull + negate equals isNotNull behavior`() {
        val data = mapOf("name" to "Alice", "email" to null)

        // name != null：使用 isNull + negate
        val nameNotNull = RuleEvaluator.evalRule("name", "isNull", null, data, negate = true)
        assertTrue(nameNotNull)
        // email == null：直接 isNull 无 negate
        val emailIsNull = RuleEvaluator.evalRule("email", "isNull", null, data)
        assertTrue(emailIsNull)
        // email != null：isNull + negate = false
        val emailNotNull = RuleEvaluator.evalRule("email", "isNull", null, data, negate = true)
        assertFalse(emailNotNull)
        // 等价于旧 isNotNull
        assertEquals(RuleEvaluator.evalRule("name", "isNotNull", null, data), nameNotNull)
        assertEquals(RuleEvaluator.evalRule("email", "isNotNull", null, data), emailNotNull)
    }

    @Test
    fun `using in + negate equals notIn behavior`() {
        val data = mapOf("role" to "guest")
        val adminList = listOf("admin", "superadmin")

        // guest not in admin list
        val guestNotInAdmin = RuleEvaluator.evalRule("role", "in", adminList, data, negate = true)
        assertTrue(guestNotInAdmin)
        // 等价于旧 notIn
        assertEquals(RuleEvaluator.evalRule("role", "notIn", adminList, data), guestNotInAdmin)

        // admin in admin list → negate → false
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

    // ─── isEmpty 操作符（string / list / map / null / array）────────────────────

    @Test
    fun `isEmpty - string cases`() {
        assertTrue(RuleEvaluator.evalRule("a", "isEmpty", null, mapOf("a" to "")))
        assertTrue(RuleEvaluator.evalRule("a", "isEmpty", null, mapOf("a" to "   ")))
        assertFalse(RuleEvaluator.evalRule("a", "isEmpty", null, mapOf("a" to "x")))
        // null 也视为 empty（与一般语义一致）
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

    // ─── size* 操作符（string / list / map） ─────────────────────────────────

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
        // items size >= 3 → T ; items >= 4 → F
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
        // number field → no size
        assertFalse(RuleEvaluator.evalRule("n", "sizeEq", 1, mapOf("n" to 42)))
        // null → sizeOf returns null → 任何 size* 返回 false
        assertFalse(RuleEvaluator.evalRule("x", "sizeGte", 0, mapOf<String, Any?>("x" to null)))
        // 但 negate=true 会得到 true
        assertTrue(RuleEvaluator.evalRule("n", "sizeEq", 1, mapOf("n" to 42), negate = true))
    }

    // ─── 数组（array）字段仅支持"限定操作符"的语义验证（与前端 OPERATORS_BY_TYPE[array] 对应） ──
    // 即：对 items 这样的数组字段，isNull / isEmpty / size* / contains / in 有意义；
    //    对 items 使用 startsWith / regex 会得到 false（字符串匹配失败）。

    @Test
    fun `array field - allowed operators behave as expected`() {
        val data = mapOf(
            "items" to listOf("apple", "banana", "cherry"),
            "emptyItems" to emptyList<Any>(),
            "nullItems" to null,
        )

        // isNull
        assertFalse(RuleEvaluator.evalRule("items", "isNull", null, data))
        assertTrue(RuleEvaluator.evalRule("nullItems", "isNull", null, data))

        // isEmpty
        assertFalse(RuleEvaluator.evalRule("items", "isEmpty", null, data))
        assertTrue(RuleEvaluator.evalRule("emptyItems", "isEmpty", null, data))

        // sizeGte 3 → T ; sizeGte 4 → F
        assertTrue(RuleEvaluator.evalRule("items", "sizeGte", 3, data))
        assertFalse(RuleEvaluator.evalRule("items", "sizeGte", 4, data))

        // contains（列表中是否包含某元素）
        assertTrue(RuleEvaluator.evalRule("items", "contains", "banana", data))
        assertFalse(RuleEvaluator.evalRule("items", "contains", "grape", data))

        // in：这里 in 的语义是 fieldValue 是否在 ruleValue 提供的列表中。
        //   对 array 字段其实语义上不常用，但后端按实现：list.toString() 是否等于目标
        //   所以我们这里验证行为稳定即可（直接用 eq 语义）。
    }

    @Test
    fun `array field - string-only operators return based on toString form but must not throw`() {
        val data = mapOf("items" to listOf("apple", "banana"))
        // 当用户在 items 上用 string 专用操作符（前端已按类型过滤，避免用户误选）。
        // 后端不抛异常即可。为了稳定，我们使用完全不会出现在列表 string 形式中的词测试：
        assertFalse(RuleEvaluator.evalRule("items", "startsWith", "ZZZZ_NOPE", data))
        assertFalse(RuleEvaluator.evalRule("items", "endsWith", "ZZZZ_NOPE", data))
        assertFalse(RuleEvaluator.evalRule("items", "regex", "ZZZZ_NOPE_\\d+", data))
    }
}
