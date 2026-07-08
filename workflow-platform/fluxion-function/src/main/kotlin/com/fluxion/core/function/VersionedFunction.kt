/**
 * Version slot container supporting zero-downtime hot-reload of functions.
 *
 * A single named function holds two logical slots:
 * * **active** – returned for new `resolve()` calls.
 * * **retiring** – kept alive so in-flight executions that pinned the old
 *   version before the swap can complete without `ClassNotFoundException`
 *   or linkage errors.
 *
 * The two-slot model mirrors Erlang's "old code / new code" soft-upgrade
 * semantics and is safe under concurrent lookup because each slot is backed
 * by an `AtomicReference` and the versioned function wrapper tracks
 * in-flight count via [FunctionVersion].
 *
 * Retiring slots are never removed eagerly; the admin housekeeping loop
 * calls [purgeRetiring] periodically to reclaim slots whose `inFlight`
 * counter has drained to zero.
 */
package com.fluxion.core.function

import com.fluxion.core.exception.FunctionNotFoundException
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the active and retiring [FunctionVersion] slots for a given
 * canonical function name.
 *
 * @property functionName the canonical key shared with the outer [FunctionRegistry]
 */
class VersionedFunction(val functionName: String) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val active = AtomicReference<FunctionVersion?>()
    private val retiring = AtomicReference<FunctionVersion?>()

    /**
     * Inserts a new version into the slot machine.
     *
     * * `version == 0` marks a code-first bootstrap registration and writes
     *   directly to the active slot without retirement accounting.
     * * `version > 0` is the hot-reload path: the currently active entry is
     *   flipped to `RETIRING` and promoted into the retiring slot, bumping
     *   out any previous retiring entry (callers are expected to have
     *   already purged it before reloading).
     *
     * @param version incoming version record
     */
    fun add(version: FunctionVersion) {
        if (version.version == 0L) {
            active.set(version)
            log.debug { "Registered code function [$functionName] with version=0" }
            return
        }

        val previousActive = active.getAndSet(version)
        previousActive?.retire()

        val previousRetiring = retiring.getAndSet(previousActive)
        previousRetiring?.let { dropRetiring(it) }

        log.info {
            "Registered function [$functionName] version=${version.version}, " +
                "previous=${previousActive?.version}, retiring dropped=${previousRetiring?.version}"
        }
    }

    fun resolve(): WorkflowFunction<Any> {
        val version = active.get() ?: throw FunctionNotFoundException(functionName)
        return version.toTrackingFunction()
    }

    fun resolve(version: Long): WorkflowFunction<Any> {
        val target = active.get()?.takeIf { it.version == version }
            ?: retiring.get()?.takeIf { it.version == version }
            ?: throw FunctionNotFoundException("$functionName@$version")
        return target.toTrackingFunction()
    }

    /**
     * Clears both slots – invoked when the registry drops the entry.
     */
    fun remove() {
        active.set(null)
        val oldRetiring = retiring.getAndSet(null)
        oldRetiring?.let { dropRetiring(it) }
        log.info { "Removed function [$functionName]" }
    }

    fun activeVersion(): Long? = active.get()?.version

    fun retiringVersion(): Long? = retiring.get()?.version

    /**
     * Attempts to discard the retiring slot if no executions are using it.
     *
     * Uses CAS so concurrent purges by the housekeeping loop never double
     * free; returns `true` only if a transition actually happened so the
     * caller can report accurate cleanup stats.
     *
     * @return `true` if the retiring slot was freed during this call
     */
    fun purgeRetiring(): Boolean {
        val version = retiring.get() ?: return false
        if (version.inFlight == 0L) {
            val removed = retiring.compareAndSet(version, null)
            if (removed) {
                log.debug { "Purged retiring function [$functionName] version=${version.version}" }
            }
            return removed
        }
        return false
    }

    private fun dropRetiring(version: FunctionVersion) {
        log.debug {
            "Dropping retiring function [$functionName] version=${version.version}, " +
                "inFlight=${version.inFlight}"
        }
    }
}
