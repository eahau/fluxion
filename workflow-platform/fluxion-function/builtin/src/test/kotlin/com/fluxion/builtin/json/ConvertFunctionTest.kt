package com.fluxion.builtin.json

import com.fluxion.core.enums.FunctionStatus
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.uncheckedCast
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConvertFunctionTest {

    private val function = ConvertFunction()

    @Test
    fun `map to json string`() {
        val input = NodeInput(
            directInput = mapOf("name" to "fluxion", "version" to 1),
            nodeParams = mapOf("targetType" to "json")
        )
        val result = function.apply(input)
        assertEquals(FunctionStatus.SUCCESS, result.status)
        val json = result.output as String
        assertTrue(json.contains("\"name\":\"fluxion\""))
        assertTrue(json.contains("\"version\":1"))
    }

    @Test
    fun `json string to map`() {
        val input = NodeInput(
            directInput = """{"name":"fluxion","version":1}""",
            nodeParams = mapOf("targetType" to "map")
        )
        val result = function.apply(input)
        assertEquals(FunctionStatus.SUCCESS, result.status)
        val map = result.output.uncheckedCast<Map<String, Any>>()!!
        assertEquals("fluxion", map["name"])
        assertEquals(1, map["version"])
    }

    @Test
    fun `map to pojo`() {
        val input = NodeInput(
            directInput = mapOf("name" to "fluxion", "age" to 18),
            nodeParams = mapOf(
                "targetType" to "pojo",
                "targetClass" to "com.fluxion.builtin.json.Person"
            )
        )
        val result = function.apply(input)
        assertEquals(FunctionStatus.SUCCESS, result.status)
        val pojo = result.output as Person
        assertEquals("fluxion", pojo.name)
        assertEquals(18, pojo.age)
    }

    @Test
    fun `json string to pojo`() {
        val input = NodeInput(
            directInput = """{"name":"fluxion","age":18}""",
            nodeParams = mapOf(
                "targetType" to "pojo",
                "targetClass" to "com.fluxion.builtin.json.Person"
            )
        )
        val result = function.apply(input)
        assertEquals(FunctionStatus.SUCCESS, result.status)
        val pojo = result.output as Person
        assertEquals("fluxion", pojo.name)
        assertEquals(18, pojo.age)
    }

    @Test
    fun `json array string to list`() {
        val input = NodeInput(
            directInput = """[1,2,3]""",
            nodeParams = mapOf("targetType" to "list")
        )
        val result = function.apply(input)
        assertEquals(FunctionStatus.SUCCESS, result.status)
        val list = result.output.uncheckedCast<List<Int>>()!!
        assertEquals(listOf(1, 2, 3), list)
    }

    @Test
    fun `pretty json output`() {
        val input = NodeInput(
            directInput = mapOf("name" to "fluxion"),
            nodeParams = mapOf("targetType" to "json", "pretty" to true)
        )
        val result = function.apply(input)
        assertEquals(FunctionStatus.SUCCESS, result.status)
        val json = result.output as String
        assertTrue(json.contains("\n"))
    }
}

data class Person(val name: String, val age: Int)
