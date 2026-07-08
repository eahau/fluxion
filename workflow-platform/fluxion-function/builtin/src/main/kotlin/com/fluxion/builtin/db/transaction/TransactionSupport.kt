package com.fluxion.builtin.db.transaction

import com.fluxion.core.model.TransactionConfig
import com.fluxion.core.model.booleanParam
import com.fluxion.core.model.intParam
import com.fluxion.core.model.stringParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Shared helpers used by both [TransactionDecorator] (per-node) and
 * [WorkflowTransactionDecorator] (per-workflow) decorators.
 *
 * Extracts the common logic of:
 * - Reading decorator params and producing a normalized [TransactionDefinition]
 *   with precedence for explicit [TransactionConfig] when provided by the
 *   workflow-level model.
 * - Bridging blocking JDBC transactions into coroutine contexts via
 *   [executeSuspending] so suspend-level decorators don't block the
 *   dispatcher.
 */
object TransactionSupport {

    /**
     * Resolves the target data source name + transaction definition.
     *
     * Precedence: explicit [transactionConfig] from workflow definition wins
     * over raw decorator params (admin UX edits transactionConfig; legacy
     * params are the fallback when config isn't set).
     *
     * @param params decorator-level parameter map
     * @param name optional label shown in monitors / tx log output
     * @param transactionConfig optional workflow-level typed model
     * @return Pair of (dataSourceName, definition)
     */
    fun resolveDefinition(
        params: Map<String, Any>?,
        name: String?,
        transactionConfig: TransactionConfig? = null
    ): Pair<String, TransactionDefinition> {
        val dataSourceName = params.stringParam("dataSource") ?: "default"
        val definition = when (transactionConfig) {
            null -> buildDefinitionFromParams(params, name)
            else -> buildDefinitionFromConfig(transactionConfig, name)
        }
        return dataSourceName to definition
    }

    /**
     * Runs [action] inside a blocking transaction boundary.
     *
     * Used by the blocking [WorkflowFunction]-level [TransactionDecorator].
     */
    fun <T> execute(
        transactionManager: TransactionManager,
        dataSourceName: String,
        definition: TransactionDefinition,
        action: () -> T
    ): T = transactionManager.execute(dataSourceName, definition, action)

    /**
     * Runs a suspending [action] inside a transaction boundary.
     *
     * The transaction itself (begin / commit / rollback) always runs on
     * [Dispatchers.IO] because JDBC drivers are blocking. The suspending
     * business action is bridged in via [runBlocking], which effectively
     * pins the thread for the duration of the work — acceptable because
     * `IO` is an unbounded pool and transactions are typically short.
     */
    suspend fun <T> executeSuspending(
        transactionManager: TransactionManager,
        dataSourceName: String,
        definition: TransactionDefinition,
        action: suspend () -> T
    ): T = withContext(Dispatchers.IO) {
        transactionManager.execute(dataSourceName, definition) { runBlocking { action() } }
    }

    /** Converts a user-facing propagation string to the [Propagation] int code. */
    fun parsePropagation(value: String): Int = when (value.uppercase()) {
        "REQUIRED" -> Propagation.REQUIRED
        "SUPPORTS" -> Propagation.SUPPORTS
        "MANDATORY" -> Propagation.MANDATORY
        "REQUIRES_NEW" -> Propagation.REQUIRES_NEW
        "NOT_SUPPORTED" -> Propagation.NOT_SUPPORTED
        "NEVER" -> Propagation.NEVER
        "NESTED" -> Propagation.NESTED
        else -> throw IllegalArgumentException("Unknown propagation: `$value")
    }

    /** Converts a user-facing isolation string to a [TransactionIsolation] code. */
    fun parseIsolation(value: String): Int? = when (value.uppercase()) {
        "DEFAULT" -> TransactionIsolation.DEFAULT
        "READ_UNCOMMITTED" -> TransactionIsolation.READ_UNCOMMITTED
        "READ_COMMITTED" -> TransactionIsolation.READ_COMMITTED
        "REPEATABLE_READ" -> TransactionIsolation.REPEATABLE_READ
        "SERIALIZABLE" -> TransactionIsolation.SERIALIZABLE
        else -> null
    }

    private fun buildDefinitionFromParams(
        params: Map<String, Any>?,
        name: String?
    ): TransactionDefinition = TransactionDefinition(
        propagation = parsePropagation(params.stringParam("propagation") ?: "REQUIRED"),
        isolation = parseIsolation(params.stringParam("isolation") ?: ""),
        timeout = params.intParam("timeout", -1),
        readOnly = params.booleanParam("readOnly", false),
        name = name
    )

    private fun buildDefinitionFromConfig(
        config: TransactionConfig,
        name: String?
    ): TransactionDefinition = TransactionDefinition(
        propagation = config.propagationBehavior,
        isolation = config.isolationLevel,
        timeout = config.timeoutMs,
        readOnly = false,
        name = config.transactionName ?: name
    )
}
