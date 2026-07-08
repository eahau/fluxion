package com.fluxion.core.engine

import com.fluxion.core.model.FilterRule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RuleEvaluatorTest {

    // 閳光偓閳光偓閳光偓 eq / neq 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 gt / gte / lt / lte 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 in / notIn 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 string operators 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 null checks 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 regex 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 瀹撳苯顨滅捄顖氱窞 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 闂?Map 鏉堟挸鍤?閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 unknown operator 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    @Test
    fun `unknown operator returns false`() {
        assertFalse(RuleEvaluator.evalRule("x", "unknown_op", "v", mapOf("x" to "v")))
    }

    // 閳光偓閳光偓閳光偓 compound rules 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 negate 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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
        // NOT ok AND code == 200 閳?false AND true 閳?false
        assertFalse(RuleEvaluator.evalCompoundRules(rules, "and", output))

        // NOT ok OR code == 200 閳?false OR true 閳?true
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

    // 閳光偓閳光偓閳光偓 閸氾箑鐣鹃崹?operator 娑撳骸鐔€绾偓 operator + negate 閻ㄥ嫮鐡戞禒閿嬧偓?閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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
        // 閺勬儳绱￠崣宀勫櫢閸欐牕寮介敍姝痚q + negate=true 鎼存梻鐡戞禒铚傜艾 eq
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
        // snake_case 閸掝偄鎮曢崥灞剧壉鎼存柨缍婃稉鈧崠?
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
        // neq(value=鐎圭偤妾崐? + negate=true == eq
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

    // 閳光偓閳光偓閳光偓 鐟欏嫬鍨痪褏瀚粩?logic閿涘牊鐦￠弶陇顫夐崚娆忓讲閸楁洜瀚幐鍥х暰 AND/OR閿涘鏀㈤埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧?
    @Test
    fun `per-rule logic mixing AND and OR - first match pattern`() {
        // 閸︾儤娅欓敍?status == 'ok') AND (code == 200) OR (role == 'admin')
        // 缁?1 閺?(index=0) 韫囩晫鏆?logic閿涙稓顑?2 閺?logic=AND閿涙稓顑?3 閺?logic=OR
        val rules = listOf(
            mapOf("field" to "status", "operator" to "eq", "value" to "ok"),
            mapOf("field" to "code", "operator" to "eq", "value" to 200, "logic" to "and"),
            mapOf("field" to "role", "operator" to "eq", "value" to "admin", "logic" to "or"),
        )
        // T AND T OR F = T
        assertTrue(
            RuleEvaluator.evalCompoundRules(
                rules, defaultLogic = "and", nodeOutput =
                mapOf("status" to "ok", "code" to 200, "role" to "guest")
            )
        )
        // F AND T OR T = T
        assertTrue(
            RuleEvaluator.evalCompoundRules(
                rules, defaultLogic = "and", nodeOutput =
                mapOf("status" to "fail", "code" to 200, "role" to "admin")
            )
        )
        // T AND F OR F = F
        assertFalse(
            RuleEvaluator.evalCompoundRules(
                rules, defaultLogic = "and", nodeOutput =
                mapOf("status" to "ok", "code" to 500, "role" to "guest")
            )
        )
    }

    @Test
    fun `per-rule logic falls back to defaultLogic when missing on a rule`() {
        // 缁?2 閺壜ゎ潐閸掓瑧宸辨径?logic 鐎涙顔?閳?閸ョ偤鈧偓閸?defaultLogic=or
        val rules = listOf(
            mapOf("field" to "a", "operator" to "eq", "value" to 1),
            mapOf("field" to "b", "operator" to "eq", "value" to 2),
        )
        val output = mapOf("a" to 1, "b" to 99)
        // defaultLogic = or 閳?a==1 OR b==2 閳?T
        assertTrue(RuleEvaluator.evalCompoundRules(rules, defaultLogic = "or", nodeOutput = output))
        // defaultLogic = and 閳?a==1 AND b==2 閳?F
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
        // 鐠侊紕鐣绘潻鍥┾柤閿?x=1)=T 閳?(OR y=99)=T 閳?(AND z=99)=F
        val out = mapOf("x" to 1, "y" to 99, "z" to 99)
        assertFalse(RuleEvaluator.evalFilterRules(rules, defaultLogic = "and", nodeOutput = out))

        // x=1 OR y=? AND z=3 閳?F after AND
        val out2 = mapOf("x" to 1, "y" to 99, "z" to 3)
        assertTrue(RuleEvaluator.evalFilterRules(rules, defaultLogic = "and", nodeOutput = out2))
    }

    // 閳光偓閳光偓閳光偓 閸忋劌鐪柅鏄忕帆瀵偓閸忕绱欑憰鍡欐磰濮ｅ繑娼憴鍕灟閻ㄥ嫮瀚粩?logic閿涘鏀㈤埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧?
    @Test
    fun `globalLogicEnabled overrides per-rule logic to AND`() {
        val rules = listOf(
            mapOf("field" to "a", "operator" to "eq", "value" to 1),
            mapOf(
                "field" to "b",
                "operator" to "eq",
                "value" to 2,
                "logic" to "or"
            ), // 閺堫剚娼竟鐗堟 OR 娴ｅ棜顫﹂崗銊ョ湰鐟曞棛娲?
            mapOf("field" to "c", "operator" to "eq", "value" to 3, "logic" to "or"), // 閸氬奔绗?
        )
        val output = mapOf("a" to 1, "b" to 2, "c" to 99)

        // 閺堫亜鎯庨悽銊ュ弿鐏炩偓閿涙岸绮拋?AND閿涘奔绲?2閵? 閺夆剝妯夊?OR 閳?(1==1 OR 2==2) OR c==3 閳?T
        assertTrue(
            RuleEvaluator.evalCompoundRules(
                rules = rules,
                defaultLogic = "and",
                nodeOutput = output,
                globalLogicEnabled = false,
            )
        )

        // 閸氼垳鏁ら崗銊ョ湰 AND閿?==1 AND 2==2 AND c==3 閳?F
        assertFalse(
            RuleEvaluator.evalCompoundRules(
                rules = rules,
                defaultLogic = "and",
                nodeOutput = output,
                globalLogicEnabled = true,
                globalLogicOperator = "and",
            )
        )

        // 閸氼垳鏁ら崗銊ョ湰 OR閿?==1 OR 娴犺缍?閳?T
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
            FilterRule("y", "eq", 2, logic = "and"), // 鐞氼偄鍙忕仦鈧憰鍡欐磰
            FilterRule("z", "eq", 3, logic = "and"),
        )
        // 閸氼垳鏁ら崗銊ョ湰 OR閿涙=? OR y=? OR z=3 閳?T 閸欘亣顩︽禒璁崇閹存劗鐝?
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
        // 閺堫亜鎯庨悽銊ュ弿鐏炩偓閿涘牓绮拋?AND閿涘本鐦＄憴鍕灟娑旂喐妲?AND閿涘绱伴崗?AND 閹存劗鐝涢棁鈧崗銊╁劥娑?T 閳?F
        assertFalse(
            RuleEvaluator.evalFilterRules(
                rules = rules,
                defaultLogic = "and",
                nodeOutput = out,
                globalLogicEnabled = false,
            )
        )
    }

    // 閳光偓閳光偓閳光偓 negate 閺囧じ鍞崣宥呮倻閹垮秳缍旂粭锔炬畱闂嗗棙鍨氭宀冪槈閿涘湶I 鐏炲倷绗夐崘宥嗘瘹闂?isNotNull/notIn/neq閿?
    @Test
    fun `using isNull + negate equals isNotNull behavior`() {
        val data = mapOf("name" to "Alice", "email" to null)

        // name != null閿涙矮濞囬悽?isNull + negate
        val nameNotNull = RuleEvaluator.evalRule("name", "isNull", null, data, negate = true)
        assertTrue(nameNotNull)
        // email == null閿涙氨娲块幒?isNull 閺?negate
        val emailIsNull = RuleEvaluator.evalRule("email", "isNull", null, data)
        assertTrue(emailIsNull)
        // email != null閿涙sNull + negate = false
        val emailNotNull = RuleEvaluator.evalRule("email", "isNull", null, data, negate = true)
        assertFalse(emailNotNull)
        // 缁涘鐜禍搴㈡＋ isNotNull
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
        // 缁涘鐜禍搴㈡＋ notIn
        assertEquals(RuleEvaluator.evalRule("role", "notIn", adminList, data), guestNotInAdmin)

        // admin in admin list 閳?negate 閳?false
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

    // 閳光偓閳光偓閳光偓 isEmpty 閹垮秳缍旂粭锔肩礄string / list / map / null / array閿涘鏀㈤埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧?
    @Test
    fun `isEmpty - string cases`() {
        assertTrue(RuleEvaluator.evalRule("a", "isEmpty", null, mapOf("a" to "")))
        assertTrue(RuleEvaluator.evalRule("a", "isEmpty", null, mapOf("a" to "   ")))
        assertFalse(RuleEvaluator.evalRule("a", "isEmpty", null, mapOf("a" to "x")))
        // null 娑旂喕顫嬫稉?empty閿涘牅绗屾稉鈧懜顒冾嚔娑斿绔撮懛杈剧礆
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

    // 閳光偓閳光偓閳光偓 size* 閹垮秳缍旂粭锔肩礄string / list / map閿?閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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
        // items size >= 3 閳?T ; items >= 4 閳?F
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
        // number field 閳?no size
        assertFalse(RuleEvaluator.evalRule("n", "sizeEq", 1, mapOf("n" to 42)))
        // null 閳?sizeOf returns null 閳?娴犺缍?size* 鏉╂柨娲?false
        assertFalse(RuleEvaluator.evalRule("x", "sizeGte", 0, mapOf<String, Any?>("x" to null)))
        // 娴?negate=true 娴兼艾绶遍崚?true
        assertTrue(RuleEvaluator.evalRule("n", "sizeEq", 1, mapOf("n" to 42), negate = true))
    }

    // 閳光偓閳光偓閳光偓 閺佹壆绮嶉敍鍧卹ray閿涘鐡у▓鍏哥矌閺€顖涘瘮"闂勬劕鐣鹃幙宥勭稊缁?閻ㄥ嫯顕㈡稊澶愮崣鐠囦緤绱欐稉搴″缁?OPERATORS_BY_TYPE[array] 鐎电懓绨查敍?閳光偓閳光偓
    // 閸楃绱扮€?items 鏉╂瑦鐗遍惃鍕殶缂佸嫬鐡у▓纰夌礉isNull / isEmpty / size* / contains / in 閺堝鍓版稊澶涚幢
    //    鐎?items 娴ｈ法鏁?startsWith / regex 娴兼艾绶遍崚?false閿涘牆鐡х粭锔胯閸栧綊鍘ゆ径杈Е閿涘鈧?
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

        // sizeGte 3 閳?T ; sizeGte 4 閳?F
        assertTrue(RuleEvaluator.evalRule("items", "sizeGte", 3, data))
        assertFalse(RuleEvaluator.evalRule("items", "sizeGte", 4, data))

        // contains閿涘牆鍨悰銊よ厬閺勵垰鎯侀崠鍛儓閺屾劕鍘撶槐鐙呯礆
        assertTrue(RuleEvaluator.evalRule("items", "contains", "banana", data))
        assertFalse(RuleEvaluator.evalRule("items", "contains", "grape", data))

        // in閿涙俺绻栭柌?in 閻ㄥ嫯顕㈡稊澶嬫Ц fieldValue 閺勵垰鎯侀崷?ruleValue 閹绘劒绶甸惃鍕灙鐞涖劋鑵戦妴?        //   鐎?array 鐎涙顔岄崗璺虹杽鐠囶厺绠熸稉濠佺瑝鐢摜鏁ら敍灞肩稻閸氬海顏幐澶婄杽閻滃府绱發ist.toString() 閺勵垰鎯佺粵澶夌艾閻╊喗鐖?        //   閹碘偓娴犮儲鍨滄禒顒冪箹闁插矂鐛欑拠浣筋攽娑撹櫣菙鐎规艾宓嗛崣顖ょ礄閻╁瓨甯撮悽?eq 鐠囶厺绠熼敍澶堚偓?
    }

    @Test
    fun `array field - string-only operators return based on toString form but must not throw`() {
        val data = mapOf("items" to listOf("apple", "banana"))
        // 瑜版挾鏁ら幋宄版躬 items 娑撳﹦鏁?string 娑撴挾鏁ら幙宥勭稊缁楋讣绱欓崜宥囶伂瀹稿弶瀵滅猾璇茬€锋潻鍥ㄦ姢閿涘矂浼╅崗宥囨暏閹寸柉顕ら柅澶涚礆閵?        // 閸氬海顏稉宥嗗瀵倸鐖堕崡鍐插讲閵嗗倷璐熸禍鍡櫱旂€规熬绱濋幋鎴滄粦娴ｈ法鏁ょ€瑰苯鍙忔稉宥勭窗閸戣櫣骞囬崷銊ュ灙鐞?string 瑜般垹绱℃稉顓犳畱鐠囧秵绁寸拠鏇窗
        assertFalse(RuleEvaluator.evalRule("items", "startsWith", "ZZZZ_NOPE", data))
        assertFalse(RuleEvaluator.evalRule("items", "endsWith", "ZZZZ_NOPE", data))
        assertFalse(RuleEvaluator.evalRule("items", "regex", "ZZZZ_NOPE_\\d+", data))
    }
}
