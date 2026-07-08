package com.fluxion.core.model

/**
 * Configuration for JDBC / Spring transaction wrapping applied to an
 * entire workflow or an individual node.
 *
 * Mirrors the relevant subset of Spring's `TransactionDefinition` so
 * that the decorator layer can drive `PlatformTransactionManager`
 * without introducing a compile-time Spring dependency on the core
 * module (numbers are the exact Spring Propagation / Isolation ints).
 */
data class TransactionConfig(
    var workflowId: String? = null,
    var executionId: String? = null,
    /** Human-readable tx name; surfaces in logging and monitoring. */
    var transactionName: String? = null,
    /**
     * JDBC isolation level passed to `Connection.setTransactionIsolation`.
     *
     * Defaults to `TRANSACTION_READ_COMMITTED` (the safest broadly
     * portable level).
     */
    var isolationLevel: Int = java.sql.Connection.TRANSACTION_READ_COMMITTED,
    /**
     * Spring propagation behaviour integer.
     *
     * 0 = REQUIRED, 3 = REQUIRES_NEW, 6 = NESTED (see PropagationBehavior).
     */
    var propagationBehavior: Int = PropagationBehavior.REQUIRED,
    /** Transaction timeout in milliseconds; 30_000 (30s) default. */
    var timeoutMs: Int = 30_000
) {
    /** Copies of Spring's `TransactionDefinition` propagation ints. */
    object PropagationBehavior {
        const val REQUIRED = 0
        const val SUPPORTS = 1
        const val MANDATORY = 2
        const val REQUIRES_NEW = 3
        const val NOT_SUPPORTED = 4
        const val NEVER = 5
        const val NESTED = 6
    }
}
