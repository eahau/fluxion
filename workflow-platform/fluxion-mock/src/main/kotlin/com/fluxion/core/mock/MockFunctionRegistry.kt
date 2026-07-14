package com.fluxion.core.mock

import com.fluxion.core.enums.NodeType
import com.fluxion.core.function.FunctionResolver
import com.fluxion.core.function.WorkflowFunction

/**
 * Decorating [FunctionResolver] that wraps every resolved function with
 * [MockWorkflowFunction].
 *
 * Plugging this into the runtime is a single-line configuration change —
 * simply provide a bean of type [FunctionResolver] that delegates to the
 * real registry and wraps results here. The decorator approach means:
 *
 * - NO code changes required inside the WorkflowEngine / DAG runner.
 * - The mock layer can be shipped as an optional dependency and disabled
 *   completely on prod nodes by simply not registering this resolver.
 * - [mockConfigProvider] allows hot-reload of mock rules (typically backed
 *   by the debug config-center subscriber) — no container restart needed.
 *
 * All three resolve overloads are forwarded because the runtime uses
 * different signatures depending on the call-site (typed nodes, versioned
 * lookups, and unversioned default lookups).
 */
class MockFunctionRegistry(
    private val inner: FunctionResolver,
    private val mockConfigProvider: () -> MockConfig
) : FunctionResolver {

    /** Wrap a node-type-typed resolve. */
    override fun resolve(functionRef: String, nodeType: NodeType?): WorkflowFunction<Any> {
        return MockWorkflowFunction(inner.resolve(functionRef, nodeType), mockConfigProvider, functionRef)
    }

    /** Wrap a simple untyped resolve. */
    override fun resolve(functionRef: String): WorkflowFunction<Any> {
        return MockWorkflowFunction(inner.resolve(functionRef), mockConfigProvider, functionRef)
    }

    /** Wrap a version-pinned resolve. */
    override fun resolve(functionRef: String, nodeType: NodeType?, version: Long): WorkflowFunction<Any> {
        return MockWorkflowFunction(inner.resolve(functionRef, nodeType, version), mockConfigProvider, functionRef)
    }
}
