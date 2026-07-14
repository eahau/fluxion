/**
 * Core function SPI and foundational types for the Fluxion workflow engine.
 *
 * This package defines the minimum contract every executable unit in a workflow
 * graph must satisfy: a pure `apply(NodeInput) -> FunctionResult` transformation
 * plus optional lifecycle / composition hooks. Implementations range from
 * built-in helpers (HTTP call, DB execution, flow control) through custom
 * application beans up to externally orchestrated RPC targets, all sharing the
 * same invocation surface so the DAG executor treats them uniformly.
 *
 * Key collaborators:
 * * [com.fluxion.core.model.NodeInput] – immutable execution context carrying
 *   direct input, merged node-level vars and side-channel metadata.
 * * [com.fluxion.core.value.FunctionResult] – strongly typed success/failure
 *   envelope the engine inspects for compensation, retry and edge routing.
 * * [FunctionResolver] / [FunctionRegistrar] – registry interfaces that sit
 *   between the DAG executor (consumer) and concrete sources such as Spring
 *   beans, Java ServiceLoader, config-center driven `FunctionMeta` bridges.
 */
package com.fluxion.core.function

import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionResult

/**
 * Single abstract-method contract executed by a workflow node.
 *
 * Implementations MUST treat [apply] as side-effect friendly but idempotent
 * where possible: the engine retries on transient failures and the same
 * function may be replayed during saga compensation. Pure transformations,
 * RPC calls with idempotency keys and transactional DB helpers all fit this
 * shape well.
 *
 * The default [functionName] derives from the concrete class name, but
 * built-in components and config-center functions typically override it with
 * a stable, human readable reference (e.g. `"builtin:httpCall"`) that matches
 * the corresponding [com.fluxion.core.value.FunctionMeta].
 *
 * @param O output value type emitted on successful execution
 */
@FunctionalInterface
fun interface WorkflowFunction<O> {

    /**
     * Executes this function against the given immutable execution snapshot.
     *
     * Implementations MUST NOT mutate [input]; intermediate state should be
     * assembled locally and returned inside the [FunctionResult] envelope.
     * The engine feeds the returned output into downstream nodes via
     * [NodeInput.withDirectInput] or stores it as a node side-effect.
     *
     * @param input snapshot of the current node execution context
     * @return a result carrying either the produced output or a failure detail
     */
    fun apply(input: NodeInput): FunctionResult<O>

    /**
     * Stable, registry-friendly identifier for this function.
     *
     * Defaults to the fully qualified class name so ad-hoc implementations
     * work out of the box. Built-in and meta-driven functions MUST return a
     * canonical reference that matches the key used in
     * [FunctionRegistry.getMeta] so the admin UI can locate documentation.
     */
    val functionName: String
        get() = javaClass.name

    /**
     * Invoked by the engine when [apply] raises a non-transient exception.
     *
     * The default behaviour rethrows as [WorkflowNodeException] which bubbles
     * up and triggers the node's configured error strategy (fail/retry/skip).
     * Functions that know how to produce a partial, safe result – for example
     * a cache reader returning `null` on connection loss – should override
     * this method and return a success result instead.
     *
     * @param input the same input snapshot originally passed to [apply]
     * @param ex the throwable raised during the failed invocation
     * @return a result envelope describing the fallback outcome
     */
    fun fallback(input: NodeInput, ex: Throwable): FunctionResult<O> {
        throw WorkflowNodeException(functionName, ex)
    }

    /**
     * Composes this function with `after`, piping the output of `this` as
     * the direct input of `after`.
     *
     * The returned wrapper preserves a composed [functionName] of shape
     * `"a >> b"` so observability tooling can surface the pipeline. Usage is
     * intentionally opt-in: the DAG executor normally wires edges explicitly
     * via node definitions, but this helper is handy for component authors
     * assembling reusable function chains.
     *
     * @param V output type produced by `after`
     * @param after downstream function to invoke with this function's output
     * @return a new [WorkflowFunction] representing the sequential pipeline
     */
    fun <V> andThen(after: WorkflowFunction<V>): WorkflowFunction<V> {
        val self = this
        val composedName = "${self.functionName} >> ${after.functionName}"
        return object : WorkflowFunction<V> {
            override val functionName: String = composedName

            override fun apply(input: NodeInput): FunctionResult<V> {
                val intermediate = self.apply(input)
                val nextInput = input.withDirectInput(intermediate.output)
                return after.apply(nextInput)
            }
        }
    }
}
