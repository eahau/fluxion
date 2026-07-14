/**
 * Registration side of the function registry SPI.
 *
 * Component loaders – Spring bean scanning, Java ServiceLoader discovery,
 * config-center `FunctionMeta` bridge – depend exclusively on this write
 * interface while the engine consumes [FunctionResolver]. The split keeps
 * boot-time wiring decoupled from runtime lookup and allows admin-side
 * hot-reload components ([com.fluxion.functionmeta.spring.boot.FunctionMetaAutoBinder])
 * to drive dynamic registration without exposing resolver internals.
 *
 * Registration has two flavours:
 * * **Code-first**: `register(fn)` / `register(name, fn)` used by components
 *   that ship a concrete [WorkflowFunction] implementation at classpath
 *   compile time. Metadata falls back to [com.fluxion.core.value.InlineMeta].
 * * **Meta-first**: `register(name, meta, fn)` used by the config-center
 *   bridge where documentation, input/output schema and ownership info is
 *   authored externally and surfaced as [com.fluxion.core.value.FunctionMeta].
 */
package com.fluxion.core.function

import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.InlineMeta

/**
 * Accepts [WorkflowFunction] registrations keyed by a canonical string
 * reference and, optionally, descriptive metadata.
 */
interface FunctionRegistrar {

    /**
     * Registers a function using its intrinsic [WorkflowFunction.functionName]
     * as the lookup key.
     *
     * Convenience overload for components that already embed a stable name in
     * the function class itself.
     *
     * @param function implementation to register
     */
    fun register(function: WorkflowFunction<*>)

    /**
     * Registers a function with an explicit canonical name.
     *
     * Metadata is populated as [InlineMeta] – sufficient for internal code
     * wiring. Use the three-argument overload when richer admin-facing
     * documentation is required.
     *
     * @param name canonical lookup key used later by [FunctionResolver]
     * @param function implementation to register
     */
    fun register(name: String, function: WorkflowFunction<*>)

    /**
     * Registers a function together with its operator-facing metadata.
     *
     * Preferred form for anything surfaced through the admin console because
     * `meta` carries input/output schema references, owner info and retry
     * hints that drive UI rendering and runtime safety checks.
     *
     * @param name canonical lookup key used later by [FunctionResolver]
     * @param meta descriptive metadata mirrored by the admin console
     * @param function implementation to register
     */
    fun register(name: String, meta: FunctionMeta, function: WorkflowFunction<*>)
}
