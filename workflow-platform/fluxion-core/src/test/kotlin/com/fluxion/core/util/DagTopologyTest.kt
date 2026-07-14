package com.fluxion.core.util
import com.fluxion.core.exception.CyclicDependencyException
import com.fluxion.core.exception.DuplicateNodeIdException
import com.fluxion.core.model.WorkflowNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
class DagTopologyTest {
    private fun node(id: String, dependsOn: List<String>? = null) =
        WorkflowNode(id = id, name = id, dependsOn = dependsOn)
    

    @Test
    fun `linear chain sorted correctly`() {
        val nodes = listOf(
            node("a"),
            node("b", dependsOn = listOf("a")),
            node("c", dependsOn = listOf("b"))
        )
        val sorted = DagTopology.topologicalSort(nodes)
        assertNotNull(sorted)
        assertEquals(listOf("a", "b", "c"), sorted)
    }
    @Test
    fun `diamond dependency`() {
        
        val nodes = listOf(
            node("a"),
            node("b", dependsOn = listOf("a")),
            node("c", dependsOn = listOf("a")),
            node("d", dependsOn = listOf("b", "c"))
        )
        val sorted = DagTopology.topologicalSort(nodes)
        assertNotNull(sorted)
        
        assertEquals("a", sorted!!.first())
        assertEquals("d", sorted.last())
        
        assertTrue(sorted.indexOf("b") < sorted.indexOf("d"))
        assertTrue(sorted.indexOf("c") < sorted.indexOf("d"))
    }
    @Test
    fun `parallel independent nodes`() {
        val nodes = listOf(node("a"), node("b"), node("c"))
        val sorted = DagTopology.topologicalSort(nodes)
        assertNotNull(sorted)
        assertEquals(3, sorted!!.size)
    }
    @Test
    fun `single node`() {
        val sorted = DagTopology.topologicalSort(listOf(node("only")))
        assertNotNull(sorted)
        assertEquals(listOf("only"), sorted)
    }
    @Test
    fun `empty list returns empty`() {
        val sorted = DagTopology.topologicalSort(emptyList())
        assertNotNull(sorted)
        assertTrue(sorted!!.isEmpty())
    }
    

    @Test
    fun `cycle returns null`() {
        val nodes = listOf(
            node("a", dependsOn = listOf("c")),
            node("b", dependsOn = listOf("a")),
            node("c", dependsOn = listOf("b"))
        )
        assertNull(DagTopology.topologicalSort(nodes))
    }
    @Test
    fun `self-cycle returns null`() {
        val nodes = listOf(node("a", dependsOn = listOf("a")))
        assertNull(DagTopology.topologicalSort(nodes))
    }
    @Test
    fun `partial cycle returns null`() {
        
        val nodes = listOf(
            node("a"),
            node("b", dependsOn = listOf("c")),
            node("c", dependsOn = listOf("b"))
        )
        assertNull(DagTopology.topologicalSort(nodes))
    }
    

    @Test
    fun `validate returns sorted result for valid DAG`() {
        val nodes = listOf(
            node("a"),
            node("b", dependsOn = listOf("a"))
        )
        val result = DagTopology.validate("wf-1", nodes)
        assertEquals(listOf("a", "b"), result.sortedIds)
    }
    @Test
    fun `validate throws DuplicateNodeIdException`() {
        val nodes = listOf(node("a"), node("a"))
        assertThrows<DuplicateNodeIdException> {
            DagTopology.validate("wf-1", nodes)
        }
    }
    @Test
    fun `validate throws for dangling dependency`() {
        val nodes = listOf(
            node("a", dependsOn = listOf("nonexistent"))
        )
        val ex = assertThrows<IllegalArgumentException> {
            DagTopology.validate("wf-1", nodes)
        }
        assertTrue(ex.message!!.contains("nonexistent"))
    }
    @Test
    fun `validate throws CyclicDependencyException for cycle`() {
        val nodes = listOf(
            node("a", dependsOn = listOf("b")),
            node("b", dependsOn = listOf("a"))
        )
        assertThrows<CyclicDependencyException> {
            DagTopology.validate("wf-1", nodes)
        }
    }
    

    @Test
    fun `complex DAG with multiple roots`() {
        
        
        
        val nodes = listOf(
            node("a"),
            node("x"),
            node("b", dependsOn = listOf("a")),
            node("y", dependsOn = listOf("x")),
            node("d", dependsOn = listOf("b", "y"))
        )
        val sorted = DagTopology.topologicalSort(nodes)
        assertNotNull(sorted)
        assertEquals(5, sorted!!.size)
        assertEquals("d", sorted.last())
        assertTrue(sorted.indexOf("a") < sorted.indexOf("b"))
        assertTrue(sorted.indexOf("x") < sorted.indexOf("y"))
    }
    @Test
    fun `fan-out then fan-in`() {
        
        
        val nodes = listOf(
            node("a"),
            node("b", dependsOn = listOf("a")),
            node("c", dependsOn = listOf("a")),
            node("d", dependsOn = listOf("a")),
            node("e", dependsOn = listOf("b", "c", "d"))
        )
        val sorted = DagTopology.topologicalSort(nodes)
        assertNotNull(sorted)
        assertEquals("a", sorted!!.first())
        assertEquals("e", sorted.last())
    }
}
