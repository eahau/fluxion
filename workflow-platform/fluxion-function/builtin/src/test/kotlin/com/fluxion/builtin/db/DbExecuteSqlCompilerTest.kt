package com.fluxion.builtin.db

import com.fluxion.builtin.db.sql.CompiledSql
import com.fluxion.builtin.db.sql.DbExecuteSqlCompiler
import com.fluxion.builtin.db.sql.ParameterSchema
import com.fluxion.builtin.db.sql.SQL_SEGMENT_LITERAL
import com.fluxion.builtin.db.sql.SQL_SEGMENT_PARAM
import com.fluxion.builtin.db.sql.SQL_SEGMENT_VARIABLE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [DbExecuteSqlCompiler].
 */
class DbExecuteSqlCompilerTest {

    @Test
    fun `compile simple select`() {
        val compiled = DbExecuteSqlCompiler.compile("SELECT * FROM items WHERE id = :id")

        assertTrue(compiled.isQuery)
        assertFalse(compiled.isInsert)
        assertNull(compiled.writeTableName)
        assertEquals(listOf("id"), compiled.paramNames)
        assertTrue(compiled.paramTypes.isEmpty())
        assertEquals(2, compiled.segments.size)
        assertEquals(SQL_SEGMENT_LITERAL, compiled.segments[0].kind)
        assertEquals("SELECT * FROM items WHERE id = ", compiled.segments[0].value)
        assertEquals(SQL_SEGMENT_PARAM, compiled.segments[1].kind)
        assertEquals("id", compiled.segments[1].value)
    }

    @Test
    fun `compile insert with static table`() {
        val compiled = DbExecuteSqlCompiler.compile(
            "INSERT INTO items (id, name) VALUES (:id, :name)"
        )

        assertFalse(compiled.isQuery)
        assertTrue(compiled.isInsert)
        assertEquals("items", compiled.writeTableName)
        assertEquals(listOf("id", "name"), compiled.paramNames)
    }

    @Test
    fun `compile with dynamic table variable`() {
        val compiled = DbExecuteSqlCompiler.compile(
            "UPDATE \${table} SET name = :name WHERE id = :id"
        )

        assertFalse(compiled.isQuery)
        assertFalse(compiled.isInsert)
        assertNull(compiled.writeTableName)
        assertEquals(listOf("name", "id"), compiled.paramNames)
        assertEquals(SQL_SEGMENT_VARIABLE, compiled.segments[1].kind)
        assertEquals("table", compiled.segments[1].value)
    }

    @Test
    fun `compile mixed param and variable`() {
        val compiled = DbExecuteSqlCompiler.compile(
            "SELECT * FROM \${table} WHERE id = :id AND status = :status"
        )

        assertEquals(listOf("id", "status"), compiled.paramNames)
        val rendered = compiled.render(mapOf("table" to "orders", "id" to 1, "status" to "ok"))
        assertEquals("SELECT * FROM orders WHERE id = ? AND status = ?", rendered.first)
        assertEquals(listOf<Any?>(1, "ok"), rendered.second)
    }

    @Test
    fun `render template preserves param placeholders`() {
        val compiled = DbExecuteSqlCompiler.compile(
            "UPDATE \${table} SET name = :name WHERE id = :id"
        )
        val rendered = compiled.renderTemplate(mapOf("table" to "items"))
        assertEquals("UPDATE items SET name = :name WHERE id = :id", rendered)
    }

    @Test
    fun `cache returns same instance for same sql`() {
        val sql = "SELECT * FROM t WHERE id = :id"
        val first = DbExecuteSqlCompiler.compile(sql)
        val second = DbExecuteSqlCompiler.compile(sql)
        assertSame(first, second)
    }

    @Test
    fun `cache stores different instances for different sql`() {
        val first = DbExecuteSqlCompiler.compile("SELECT * FROM t WHERE id = :id")
        val second = DbExecuteSqlCompiler.compile("SELECT * FROM t WHERE name = :name")
        assertNotSame(first, second)
    }

    @Test
    fun `compile rejects drop`() {
        assertThrows<IllegalArgumentException> {
            DbExecuteSqlCompiler.compile("DROP TABLE items")
        }
    }

    @Test
    fun `compile rejects multi statement`() {
        assertThrows<IllegalArgumentException> {
            DbExecuteSqlCompiler.compile("SELECT 1; DELETE FROM items")
        }
    }

    @Test
    fun `compile rejects ddl hidden in comment`() {
        assertThrows<IllegalArgumentException> {
            DbExecuteSqlCompiler.compile("/* comment */ DROP TABLE items")
        }
    }

    @Test
    fun `render rejects invalid identifier value`() {
        val compiled = DbExecuteSqlCompiler.compile("SELECT * FROM \${table}")
        assertThrows<IllegalArgumentException> {
            compiled.render(mapOf("table" to "items;DROP"))
        }
    }

    @Test
    fun `render throws when variable missing`() {
        val compiled = DbExecuteSqlCompiler.compile("SELECT * FROM \${table}")
        assertThrows<IllegalArgumentException> {
            compiled.render(emptyMap())
        }
    }

    @Test
    fun `compile with schema validates param existence`() {
        val available = mapOf(
            "id" to ParameterSchema("id", "integer"),
            "status" to ParameterSchema("status", "string")
        )
        val compiled = DbExecuteSqlCompiler.compile(
            "SELECT * FROM items WHERE id = :id AND status = :status",
            available
        )
        assertEquals(mapOf("id" to "integer", "status" to "string"), compiled.paramTypes)
    }

    @Test
    fun `compile with schema rejects missing param`() {
        val available = mapOf("id" to ParameterSchema("id", "integer"))
        assertThrows<IllegalArgumentException> {
            DbExecuteSqlCompiler.compile(
                "SELECT * FROM items WHERE id = :id AND status = :status",
                available
            )
        }
    }

    @Test
    fun `compile with schema validates variable type`() {
        val available = mapOf(
            "table" to ParameterSchema("table", "string"),
            "id" to ParameterSchema("id", "integer")
        )
        val compiled = DbExecuteSqlCompiler.compile(
            "SELECT * FROM \${table} WHERE id = :id",
            available
        )
        assertEquals(mapOf("id" to "integer"), compiled.paramTypes)
    }

    @Test
    fun `compile with schema rejects non string variable`() {
        val available = mapOf("table" to ParameterSchema("table", "integer"))
        assertThrows<IllegalArgumentException> {
            DbExecuteSqlCompiler.compile("SELECT * FROM \${table}", available)
        }
    }
}
