package com.fluxion.builtin.db.transaction

import com.fluxion.core.model.TransactionConfig
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.value.EngineResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkflowTransactionDecoratorTest {

    private val transactionManager = RecordingTransactionManager()

    @Test
    fun `should open transaction from transactionConfig`() = runBlocking {
        val decorator = WorkflowTransactionDecorator(transactionManager)
        val def = WorkflowDefinition().apply {
            id = "wf-tx-1"
            name = "create-order"
            transactionConfig = TransactionConfig(
                propagationBehavior = TransactionConfig.PropagationBehavior.REQUIRED,
                isolationLevel = java.sql.Connection.TRANSACTION_READ_COMMITTED,
                timeoutMs = 30_000,
                transactionName = "tx-create-order"
            )
        }

        val result = decorator.decorate(def, emptyMap()) { EngineResult.success("ok", mockState()) }

        assertEquals("ok", result.data)
        assertEquals(1, transactionManager.beginCount)
        assertEquals(1, transactionManager.commitCount)
        assertEquals("default", transactionManager.lastDataSource)
        assertEquals(Propagation.REQUIRED, transactionManager.lastDefinition?.propagation)
        assertEquals("tx-create-order", transactionManager.lastDefinition?.name)
    }

    @Test
    fun `should rollback on execution exception`() = runBlocking {
        val decorator = WorkflowTransactionDecorator(transactionManager)
        val def = WorkflowDefinition().apply {
            id = "wf-tx-2"
            transactionConfig = TransactionConfig()
        }

        assertThrows(RuntimeException::class.java) {
            runBlocking {
                decorator.decorate(def, emptyMap()) { throw RuntimeException("boom") }
            }
        }

        assertEquals(1, transactionManager.beginCount)
        assertEquals(1, transactionManager.rollbackCount)
        assertEquals(0, transactionManager.commitCount)
    }

    @Test
    fun `should read parameters from workflowDecoratorParams`() = runBlocking {
        val decorator = WorkflowTransactionDecorator(transactionManager)
        val def = WorkflowDefinition().apply {
            id = "wf-tx-3"
            workflowDecoratorParams = mapOf(
                "workflow:transaction" to mapOf(
                    "dataSource" to "order-db",
                    "propagation" to "REQUIRES_NEW",
                    "isolation" to "SERIALIZABLE",
                    "timeout" to 10,
                    "readOnly" to true
                )
            )
        }

        decorator.decorate(def, emptyMap()) { EngineResult.success("ok", mockState()) }

        assertEquals("order-db", transactionManager.lastDataSource)
        assertEquals(Propagation.REQUIRES_NEW, transactionManager.lastDefinition?.propagation)
        assertEquals(java.sql.Connection.TRANSACTION_SERIALIZABLE, transactionManager.lastDefinition?.isolation)
        assertEquals(10, transactionManager.lastDefinition?.timeout)
        assertEquals(true, transactionManager.lastDefinition?.readOnly)
    }

    private fun mockState() = com.fluxion.core.model.ImmutableExecutionState.start(
        WorkflowDefinition().apply { id = "mock" },
        emptyMap()
    )

    private class RecordingTransactionManager : TransactionManager {
        var beginCount = 0
        var commitCount = 0
        var rollbackCount = 0
        var lastDataSource: String? = null
        var lastDefinition: TransactionDefinition? = null

        override fun <T> execute(dataSourceName: String, definition: TransactionDefinition, action: () -> T): T {
            beginCount++
            lastDataSource = dataSourceName
            lastDefinition = definition
            return try {
                val result = action()
                commitCount++
                result
            } catch (ex: Exception) {
                rollbackCount++
                throw ex
            }
        }

        override fun isActive(dataSourceName: String): Boolean = false
    }
}
