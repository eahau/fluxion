package com.fluxion.core.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpressionEvaluatorTest {

    // 閳光偓閳光偓閳光偓 閸╄櫣顢呯敮鍐ㄧ毜鐞涖劏鎻?閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 SpEL 閸忕厧顔愰敍? 閸撳秶绱戦崢濠氭珟閿涘鏀㈤埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧?
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

    // 閳光偓閳光偓閳光偓 Map 鐎涙顔岀仦鏇炵磻 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    @Test
    fun `map fields expanded to top level`() {
        val output = mapOf("status" to "APPROVED", "code" to 200)
        // 閻╁瓨甯撮崘?status 閼板奔绗夐弰?output['status']
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

    // 閳光偓閳光偓閳光偓 input 閸欐﹢鍣?閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 婢跺秵娼呯悰銊ㄦ彧瀵?閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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
        // 娴ｈ法鏁?Aviator 閸樼喓鏁?string 濡€虫健閸戣姤鏆熼敍鍫ｆ硶閸欐垼顢戦悧鍫⑶旂€规熬绱?        assertTrue(ExpressionEvaluator.evalBoolean("string.contains(msg, 'world')", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("string.startsWith(msg, 'hello')", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("string.endsWith(msg, 'world')", output, emptyMap()))
    }

    @Test
    fun `arithmetic in expression`() {
        val output = mapOf("price" to 10, "qty" to 3)
        assertTrue(ExpressionEvaluator.evalBoolean("price * qty > 25", output, emptyMap()))
    }

    // 閳光偓閳光偓閳光偓 瀵倸鐖剁€瑰綊鏁?閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    @Test
    fun `invalid expression returns false`() {
        // 鐠囶厽纭堕柨娆掝嚖閻ㄥ嫯銆冩潏鎯х础鎼存棁绻戦崶?false 閼板矂娼幎娑樼磽鐢?        assertFalse(ExpressionEvaluator.evalBoolean("!!!invalid!!!", "ok", emptyMap()))
    }

    @Test
    fun `non-boolean result returns false`() {
        // 鐞涖劏鎻蹇曠波閺嬫粈绗夐弰?boolean閿涘牆顩х€涙顑佹稉?"hello"閿涘妞傛潻鏂挎礀 false
        assertFalse(ExpressionEvaluator.evalBoolean("'hello'", "ok", emptyMap()))
    }

    @Test
    fun `null output handled gracefully`() {
        assertFalse(ExpressionEvaluator.evalBoolean("output == 'ok'", null, emptyMap()))
    }

    // 閳光偓閳光偓閳光偓 鏉堝湱鏅幆鍛枌 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    @Test
    fun `empty map output`() {
        assertFalse(ExpressionEvaluator.evalBoolean("missing == 'v'", emptyMap<String, Any>(), emptyMap()))
    }

    @Test
    fun `list output does not expand fields`() {
        val output = listOf("a", "b", "c")
        // List 娑撳秴鐫嶅鈧敍灞藉涧閼崇晫娲块幒銉︾槷鏉?output
        assertTrue(ExpressionEvaluator.evalBoolean("output != null", output, emptyMap()))
    }

    // ====================================================================
    // AviatorScript 閻楄婀侀懗钘夊濞村鐦敍鍫ｇ讣缁夎鎮楅幍宥呭讲閻劎娈戦崘鍛枂閸戣姤鏆熸稉搴ょ箥缁犳顑侀敍?    // ====================================================================

    @Test
    fun `Aviator string length and substring builtins`() {
        val output = mapOf("name" to "HelloWorld")
        // 閸忋劑鍎存担璺ㄦ暏閹兼粎鍌ㄧ紒鎾寸亯 #1 閺勫海鈥橀崚妤€鍤惃?string 濡€虫健閸戣姤鏆熼敍鍫ｆ硶 5.x 閸欐垼顢戦悧鍫⑶旂€规熬绱?        assertTrue(ExpressionEvaluator.evalBoolean("string.length(name) == 10", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("string.contains(name, 'World')", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("string.startsWith(name, 'Hello')", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("string.endsWith(name, 'World')", output, emptyMap()))
        // string.substring(str, start, length)閿涙氨顑囨稉澶夐嚋閺勵垶鏆辨惔锔肩礉娑撳秵妲?end index
        assertEquals("Hello", ExpressionEvaluator.evalString("string.substring(name, 0, 5)", output, emptyMap()))
    }

    @Test
    fun `Aviator math builtins abs ceil floor round`() {
        val output = mapOf("val1" to -3.7, "val2" to 2.3)
        // math.abs(double) 鏉╂柨娲?Double閿涙驳ath.round(double) 鏉╂柨娲?Long閿涘牏鐡戞禒铚傜艾 java.lang.Math.round閿?        assertEquals(3.7, ExpressionEvaluator.evalObject("math.abs(val1)", output, emptyMap()) as? Double)
        assertEquals(-4L, ExpressionEvaluator.evalObject("math.round(val1)", output, emptyMap()) as? Long)
        // math.ceil / math.floor 鏉╂柨娲?Double閿涘本鐦潏鍐╂鐢?.0 閸氬海绱戦柆鍨帳缁鐎锋稉宥勭閼?        assertTrue(ExpressionEvaluator.evalBoolean("math.ceil(val2) == 3.0", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("math.floor(val1) == -4.0", output, emptyMap()))
    }

    @Test
    fun `Aviator seq builtins filter map include`() {
        val output = mapOf("nums" to listOf(1, 2, 3, 4, 5))
        // 閸欘亞鏁ら幖婊呭偍缂佹挻鐏?#1 閺勫海鈥橀崚妤€鍤妴浣芥硶 5.x 閸欐垼顢戦悧?100% 缁嬪啿鐣剧€涙ê婀惃鍕毐閺佸府绱?        //   include(col, elem)   妞よ泛鐪伴崙鑺ユ殶閿涘苯鍨介弬顓熸Ц閸氾箑瀵橀崥顐ｇ厙閸忓啰绀岄敍鍦爄st/Set/Tuple 缁涘褰叉潻顓濆敩鐎电钖勯敍?        //   count(col)           妞よ泛鐪伴崙鑺ユ殶閿涘矁绻戦崶鐐插帗缁辩姳閲滈弫?        assertTrue(ExpressionEvaluator.evalBoolean("include(nums, 3)", output, emptyMap()))
        assertFalse(ExpressionEvaluator.evalBoolean("include(nums, -1)", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("include(nums, 5)", output, emptyMap()))
        assertTrue(ExpressionEvaluator.evalBoolean("!include(nums, 99)", output, emptyMap()))

        assertEquals(5L, ExpressionEvaluator.evalObject("count(nums)", output, emptyMap()) as? Long)
        // 缁屾椽娉﹂崥?count=0閿涘nclude(缁屾椽娉﹂崥? any)=false
        val empty = mapOf("xs" to emptyList<Int>())
        assertEquals(0L, ExpressionEvaluator.evalObject("count(xs)", empty, emptyMap()) as? Long)
        assertFalse(ExpressionEvaluator.evalBoolean("include(xs, 1)", empty, emptyMap()))
    }

    @Test
    fun `Aviator elvis operator null coalesce`() {
        val output = mapOf("name" to null, "fallback" to "foo")
        // 鐞涖劏鎻蹇涘櫡閸愭瑧娈?`??` 娴兼俺顫?normalizeExpression 閺€鐟板晸娑?`(a != nil ? a : b)` 瑜般垹绱￠敍?        // map 娑擃厺绱舵潻娑欐降閻?Java null 娑旂喓鐡戞禒铚傜艾 Aviator 閻?nil閿涘瞼鈹栭崐鐓庢値楠炴儼顕㈡稊澶夌瑢 Kotlin/C# 娑撯偓閼?        assertEquals("foo", ExpressionEvaluator.evalString("name ?? fallback", output, emptyMap()))
        assertEquals("bar", ExpressionEvaluator.evalString("missing ?? 'bar'", output, emptyMap()))
    }

    @Test
    fun `Aviator ternary operator`() {
        val output = mapOf("age" to 20)
        assertEquals("adult", ExpressionEvaluator.evalString("age >= 18 ? 'adult' : 'minor'", output, emptyMap()))
    }
}
