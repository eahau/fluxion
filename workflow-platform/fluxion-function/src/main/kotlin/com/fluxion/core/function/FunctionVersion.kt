/**
 * Immutable descriptor plus lifecycle tracking for one specific revision of
 * a registered [WorkflowFunction].
 *
 * Each instance carries:
 * * The canonical `version` number – `0` for code-first registrations,
 *   monotonic positive values for config-centre hot-reloads.
 * * The executable function instance itself.
 * * Operator-facing `meta` used by the admin console and schema validators.
 * * An `inFlight` counter bumped by the wrapper so [VersionedFunction] can
 *   decide when it is safe to garbage collect a retiring slot.
 *
 * The state machine is intentionally minimal: `ACTIVE → RETIRING`. There is
 * no explicit PURGED state because retirement is tracked externally via
 * atomic reference swap in the parent container.
 */
package com.fluxion.core.function

import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.LongAdder

/**
 * Lifecycle phases for a loaded function revision.
 */
enum class FunctionVersionState {
    ACTIVE,
    RETIRING
}

/**
 * Single revision record loaded into the versioned slot.
 *
 * @property version monotonic revision identifier; `0` means bootstrap-only
 * @property function executable implementation
 * @property meta operator-facing metadata (schema references, owner, ...)
 * @property createdAt wall-clock timestamp at registration, for observability
 */
class FunctionVersion(
    val version: Long,
    val function: WorkflowFunction<Any>,
    val meta: FunctionMeta,
    val createdAt: Long = System.currentTimeMillis()
) {
    private val stateRef = AtomicReference(FunctionVersionState.ACTIVE)
    private val inFlightRef = LongAdder()

    val state: FunctionVersionState get() = stateRef.get()

    val inFlight: Long get() = inFlightRef.sum()

    fun retire() = stateRef.set(FunctionVersionState.RETIRING)

    /**
     * Wraps the bare executable so in-flight counters live around each call.
     *
     * The wrapper delegates `fallback` to the inner function unchanged so
     * user-specified recovery logic is preserved.
     */
    fun toTrackingFunction(): WorkflowFunction<Any> = InFlightTrackingFunction(this)

    internal fun incrementInFlight() = inFlightRef.increment()
    internal fun decrementInFlight() = inFlightRef.decrement()
}

/**
 * Decorator that bumps the parent `inFlight` counter for the duration of
 * each [WorkflowFunction.apply] invocation.
 *
 * Using `finally` guarantees the counter decrements even when user code
 * throws; this is what makes the "purge when inFlight == 0" rule safe
 * under arbitrary failure modes.
 */
private class InFlightTrackingFunction(
    private val version: FunctionVersion
) : WorkflowFunction<Any> {

    override fun apply(input: NodeInput): FunctionResult<Any> {
        version.incrementInFlight()
        try {
            return version.function.apply(input)
        } finally {
            version.decrementInFlight()
        }
    }

    override fun fallback(input: NodeInput, ex: Throwable): FunctionResult<Any> =
        version.function.fallback(input, ex)
}
