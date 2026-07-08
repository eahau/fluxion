package com.fluxion.core.engine

import com.fluxion.decorator.decorator.WorkflowDecorator
import com.fluxion.decorator.decorator.WorkflowDecoratorRegistry
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.metrics.WorkflowMetrics
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.retry.RetryScheduler
import com.fluxion.core.schema.SchemaValidator
import com.fluxion.core.value.EngineResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DagExecutorWorkflowDecoratorTest {

    @Test
    fun `should invoke workflow decorators in order`() = runBlocking {
        val registry = WorkflowDecoratorRegistry()
        val order = mutableListOf<String>()
        registry.register(RecordingDecorator("d1", order))
        registry.register(RecordingDecorator("d2", order))

        val engine = createEngine()
        val dagExecutor = DagExecutor(engine, workflowDecoratorRegistry = registry)

        val def = WorkflowDefinition().apply {
            id = "wf-decorated"
            workflowDecorators = listOf("d1", "d2")
        }

        val result = dagExecutor.execute(def, emptyMap())
        assertEquals("d1-before,d2-before,d2-after,d1-after", order.joinToString(","))
        assertEquals(null, result.data)
    }

    @Test
    fun `should skip decorator chain when no workflow decorators configured`() = runBlocking {
        val registry = WorkflowDecoratorRegistry()
        val order = mutableListOf<String>()
        registry.register(RecordingDecorator("d1", order))

        val engine = createEngine()
        val dagExecutor = DagExecutor(engine, workflowDecoratorRegistry = registry)

        val def = WorkflowDefinition().apply { id = "wf-plain" }
        val result = dagExecutor.execute(def, emptyMap())
        assertEquals(null, result.data)
        assertEquals(emptyList<String>(), order)
    }

    private fun createEngine(): WorkflowEngine {
        return WorkflowEngine(
            functionRegistry = FunctionRegistry(),
            schemaValidator = SchemaValidator(),
            decoratorRegistry = com.fluxion.decorator.decorator.DecoratorRegistry(),
            metrics = WorkflowMetrics.noOp(),
            retryScheduler = RetryScheduler(),
            devMode = false,
            validateNodeInput = false,
            validateNodeOutput = false
        )
    }

    private class RecordingDecorator(
        private val name: String,
        private val order: MutableList<String>
    ) : WorkflowDecorator {
        override fun name(): String = name

        override suspend fun decorate(
            def: WorkflowDefinition,
            rawInput: Map<String, Any>,
            execute: suspend () -> EngineResult
        ): EngineResult {
            order.add("$name-before")
            val result = execute()
            order.add("$name-after")
            return result
        }
    }
}
