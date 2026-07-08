package com.fluxion.core.signal

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * External signal -- similar to Temporal Signal, used to inject data into running workflows.
 *
 * Typical scenarios:
 *   - Manual approval: send approve/reject signal to a workflow waiting for approval
 *   - External event driven: webhook callback injects data into workflow
 *   - Manual intervention: ops injects skip data into a stuck workflow
 */
data class Signal(
    /** Signal name (matches the signalName parameter of a waitForSignal node) */
    val name: String,
    /** Data carried by the signal (serves as output of the waitForSignal node) */
    val payload: Any? = null
)

/**
 * Signal delivery broker -- manages signal reception for running workflows.
 *
 * Supports signal buffering: signals can arrive before await (deliver-then-await)
 * or after await (await-then-deliver); both orderings are safe.
 *
 * Thread-safe: based on ConcurrentHashMap, suitable for high-concurrency scenarios.
 */
class SignalBroker {

    /** Waiting signal slots: executionId -> signalName -> pending future */
    private val waiters = ConcurrentHashMap<String, ConcurrentHashMap<String, CompletableFuture<Signal>>>()

    /** Buffered signals (signal arrived before await): executionId -> signalName -> completed future */
    private val buffered = ConcurrentHashMap<String, ConcurrentHashMap<String, CompletableFuture<Signal>>>()

    /**
     * Send a signal to a specified execution.
     *
     * If a node is currently waiting for this signal -> wake it immediately;
     * If no node is waiting -> buffer the signal, later await returns immediately.
     *
     * @return true if signal was accepted (delivered or buffered)
     */
    fun send(executionId: String, signal: Signal): Boolean {
        // check if there are waiters
        val execWaiters = waiters[executionId]
        if (execWaiters != null) {
            val future = execWaiters.remove(signal.name)
            if (future != null) {
                future.complete(signal)
                return true
            }
        }
        // no waiter -> buffer
        buffered.computeIfAbsent(executionId) { ConcurrentHashMap() }[signal.name] =
            CompletableFuture.completedFuture(signal)
        return true
    }

    /**
     * Block waiting for a signal to arrive.
     *
     * If signal is already buffered -> return immediately;
     * If signal hasn't arrived -> register waiter and block until [send] wakes it.
     *
     * @param timeoutMs timeout in milliseconds (<=0 means wait indefinitely)
     * @return Signal object, or null on timeout
     */
    fun awaitSignal(executionId: String, signalName: String, timeoutMs: Long = 0): Signal? {
        // check buffered
        val execBuffered = buffered[executionId]
        if (execBuffered != null) {
            val future = execBuffered.remove(signalName)
            if (future != null) {
                return future.get()
            }
        }

        // register waiter
        val future = CompletableFuture<Signal>()
        val execWaiters = waiters.computeIfAbsent(executionId) { ConcurrentHashMap() }
        execWaiters[signalName] = future

        // double-check buffered (send may have arrived between put and get)
        val execBuffered2 = buffered[executionId]
        if (execBuffered2 != null) {
            val bufferedFuture = execBuffered2.remove(signalName)
            if (bufferedFuture != null) {
                execWaiters.remove(signalName)
                return bufferedFuture.get()
            }
        }

        return if (timeoutMs > 0) {
            try {
                future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                execWaiters.remove(signalName)
                null
            }
        } else {
            try {
                future.get()
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Query buffered signal list for a specified execution (used by Query API)
     */
    fun pendingSignals(executionId: String): List<String> {
        val execBuffered = buffered[executionId] ?: return emptyList()
        return execBuffered.keys().toList()
    }

    /**
     * Query waiting signal list for a specified execution (used by Query API)
     */
    fun waitingSignals(executionId: String): List<String> {
        val execWaiters = waiters[executionId] ?: return emptyList()
        return execWaiters.keys().toList()
    }

    /**
     * Clean up all signal slots for a specified execution (called after execution ends)
     */
    fun cleanup(executionId: String) {
        waiters.remove(executionId)?.values?.forEach { it.cancel(true) }
        buffered.remove(executionId)
    }
}
