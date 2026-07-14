package com.fluxion.builtin.db.transaction

import com.fluxion.core.util.uncheckedCastGeneric
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Adapts Spring's [PlatformTransactionManager] to our [TransactionManager] SPI.
 *
 * All constants (propagation, isolation) are chosen to match Spring's own
 * `TransactionDefinition` values so the [TransactionDefinition] →
 * `TransactionTemplate` mapping is a direct field copy with no conversion.
 *
 * This adapter lets `builtin:dbExecute` nodes, `TransactionDecorator`, and
 * `WorkflowTransactionDecorator` participate in the SAME transaction that
 * `@Transactional`, Hibernate sessions, or MyBatis mappers use in the same
 * service deployment — otherwise decorator-owned tx and framework-owned tx
 * would be independent physical connections with no atomicity.
 */
class SpringTransactionManager(private val transactionManager: PlatformTransactionManager) : TransactionManager {

    override fun <T> execute(dataSourceName: String, definition: TransactionDefinition, action: () -> T): T =
        TransactionTemplate(transactionManager).apply {
            // Propagation constants align exactly with Spring's.
            propagationBehavior = definition.propagation
            definition.isolation?.let { isolationLevel = it }
            if (definition.timeout > 0) {
                timeout = definition.timeout
            }
            isReadOnly = definition.readOnly
            definition.name?.let { setName(it) }
        }.execute { _ -> action() }.uncheckedCastGeneric()!!

    override fun isActive(dataSourceName: String): Boolean =
        TransactionSynchronizationManager.isActualTransactionActive()
}
