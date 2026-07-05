package com.fluxion.builtin.db.transaction

import com.fluxion.core.util.uncheckedCastGeneric
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Spring 事务管理器适配。
 *
 * 将 [TransactionManager] SPI 映射到 Spring 的 [PlatformTransactionManager]，
 * 使工作流节点事务能够与 Spring @Transactional、Hibernate、MyBatis 等共享同一个事务生态。
 */
class SpringTransactionManager(private val transactionManager: PlatformTransactionManager) : TransactionManager {

    override fun <T> execute(dataSourceName: String, definition: TransactionDefinition, action: () -> T): T =
        TransactionTemplate(transactionManager).apply {
            // Propagation 常量值与 Spring TransactionDefinition 完全一致，可直接透传
            propagationBehavior = definition.propagation
            // TransactionIsolation 常量值与 Spring TransactionDefinition 完全一致，可直接透传
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
