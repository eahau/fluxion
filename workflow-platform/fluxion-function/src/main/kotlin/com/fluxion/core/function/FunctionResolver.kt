/**
 * Resolution side of the function registry SPI.
 *
 * The DAG executor depends only on this interface when looking up executable
 * references by string key; it never touches registration APIs directly.
 * Keeping the read path isolated simplifies testing – a fake implementation
 * can be wired into [com.fluxion.core.engine.WorkflowEngine] without pulling
 * in Spring or ServiceLoader machinery.
 *
 * Lookups support two optional dimensions:
 * * `nodeType` prefix hint (`builtin:`, `script:`, `external:`) used when a
 *   bare function name is ambiguous across namespaces.
 * * `version` for hot-reload scenarios where in-flight executions must keep
 *   referencing a retiring revision until their in-flight count drains to
 *   zero – see [VersionedFunction] for the state machine.
 */
package com.fluxion.core.function

import com.fluxion.core.enums.NodeType

/**
 * Resolves [WorkflowFunction] instances from opaque string references.
 *
 * Implementations are encouraged to treat missing references as fatal and
 * throw [com.fluxion.core.exception.FunctionNotFoundException] so the engine
 * can surface a clear error to the operator rather than silently skipping a
 * node. The production-grade implementation is [FunctionRegistry]; test code
 * typically uses [com.fluxion.core.mock.MockFunctionRegistry] instead.
 */
interface FunctionResolver {

    /**
     * Resolves a function reference, optionally disambiguating by node type.
     *
     * @param functionRef opaque reference – either a canonical key or a bare
     *   name that will be prefixed using `nodeType` when non-null
     * @param nodeType namespace hint that supplies the lookup prefix
     * @return the resolved executable, never `null`
     * @throws com.fluxion.core.exception.FunctionNotFoundException if no
     *   matching registration exists
     */
    fun resolve(functionRef: String, nodeType: NodeType?): WorkflowFunction<Any>

    /**
     * Convenience overload that skips the namespace-hint disambiguation step.
     *
     * @param functionRef exact canonical key registered with the registrar
     * @return the resolved executable, never `null`
     */
    fun resolve(functionRef: String): WorkflowFunction<Any>

    /**
     * Pins the lookup to a specific version, falling back to the retiring
     * revision if the active slot no longer carries that identifier.
     *
     * @param functionRef canonical key registered with the registrar
     * @param nodeType optional namespace hint for bare-name lookups
     * @param version exact version marker requested by the caller
     * @return the version-pinned executable, never `null`
     */
    fun resolve(functionRef: String, nodeType: NodeType?, version: Long): WorkflowFunction<Any>
}
