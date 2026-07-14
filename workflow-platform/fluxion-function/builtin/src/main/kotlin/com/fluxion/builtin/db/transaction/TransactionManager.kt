package com.fluxion.builtin.db.transaction

/**
 * Transaction manager SPI used by the built-in node- and workflow-level
 * transaction decorators.
 *
 * Abstracts underlying implementations (JDBC native, Spring
 * `PlatformTransactionManager`, JTA user-transaction) behind a simple
 * execute-in-transaction primitive so decorators don't depend on any
 * particular vendor library.
 *
 * A Spring-backed adapter ships in this module as
 * [SpringTransactionManager]; non-Spring deployments can supply their own.
 */
interface TransactionManager {

    /**
     * Executes [action] inside a transactional boundary for the named source.
     *
     * @param dataSourceName logical data source name (decorators derive this
     *   from decorator params; multi-ds deployments may open separate tx per ds)
     * @param definition propagation / isolation / timeout / read-only flags
     * @param action business logic to wrap
     * @return the action's return value, propagated unchanged
     * @throws RuntimeException any exception from action propagates out and
     *   triggers rollback of the work done by this boundary.
     */
    fun <T> execute(dataSourceName: String, definition: TransactionDefinition, action: () -> T): T

    /**
     * Returns `true` iff the calling thread already participates in an active
     * transaction for the given data source.
     *
     * Used by the decorator layer to decide whether REQUIRES_NEW really needs
     * to suspend, and for diagnostics in logging.
     */
    fun isActive(dataSourceName: String): Boolean
}

/**
 * Immutable transaction definition, values mirror
 * `org.springframework.transaction.TransactionDefinition` so the Spring
 * adapter can map them 1:1 without conversion tables.
 *
 * @property propagation propagation behavior (see [Propagation] constants)
 * @property isolation isolation level, `null` = use datasource default
 *   (see [TransactionIsolation] constants)
 * @property timeout seconds; `-1` = no explicit timeout
 * @property readOnly hint to the underlying resource manager; does NOT
 *   enforce anything on the caller side
 * @property name optional label shown in transaction monitors / logs
 */
data class TransactionDefinition(
    val propagation: Int = Propagation.REQUIRED,
    val isolation: Int? = null,
    val timeout: Int = -1,
    val readOnly: Boolean = false,
    val name: String? = null
)

/**
 * Propagation-behavior constants. Values intentionally match Spring's
 * `TransactionDefinition` constants for zero-copy mapping.
 */
object Propagation {
    const val REQUIRED = 0
    const val SUPPORTS = 1
    const val MANDATORY = 2
    const val REQUIRES_NEW = 3
    const val NOT_SUPPORTED = 4
    const val NEVER = 5
    const val NESTED = 6
}

/**
 * Isolation-level constants. Values intentionally match Spring's
 * `TransactionDefinition` AND `java.sql.Connection` standards.
 */
object TransactionIsolation {
    const val DEFAULT = -1
    const val READ_UNCOMMITTED = java.sql.Connection.TRANSACTION_READ_UNCOMMITTED
    const val READ_COMMITTED = java.sql.Connection.TRANSACTION_READ_COMMITTED
    const val REPEATABLE_READ = java.sql.Connection.TRANSACTION_REPEATABLE_READ
    const val SERIALIZABLE = java.sql.Connection.TRANSACTION_SERIALIZABLE
}
