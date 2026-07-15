package com.fluxion.script.function

import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionResult
import com.fluxion.script.groovy.GroovyScriptFunction

/**
 * Wrapper that adapts a configuration-published SCRIPT function into a
 * registered [WorkflowFunction] executable by the DAG engine.
 *
 * Purpose: the function-config applier (`FunctionConfigApplier`) receives
 * function snapshots from the config center (Apollo / Nacos / HTTP) — each
 * SCRIPT snapshot carries its own pre-loaded script body, schema and
 * description. Instead of forcing the DAG node to carry the script body in
 * `nodeParams` on every execution (which would waste serialisation / cache
 * bandwidth), we bake the body ONCE into this wrapper at registration time.
 *
 * Execution: when the DAG engine invokes `apply(NodeInput)` we construct a
 * synthetic `NodeInput` that injects the baked-in `script` parameter plus
 * the caller's actual node params, then delegate to the shared
 * [GroovyScriptFunction] singleton so all caching/security behaviour is
 * centralised.
 */
class ScriptWorkflowFunction(
    private val name: String,
    private val scriptBody: String,
    private val paramSchema: String? = null,
    private val outputSchema: String? = null,
    private val description: String? = null,
    private val groovyEngine: GroovyScriptFunction? = null
) : WorkflowFunction<Any> {

    /** Registered function reference (e.g. `script:myTransform`). */
    override val functionName: String = name

    /**
     * Execute the wrapped script.
     *
     * Pre-injects `script=<scriptBody>` into the Groovy engine parameters,
     * preserving any additional node-level parameters passed by the DAG
     * caller (the engine's script impl does a script/scriptRef merge so
     * node-provided overrides still take precedence if set).
     */
    override fun apply(input: NodeInput): FunctionResult<Any> {
        val engineFn = groovyEngine
            ?: throw IllegalStateException("Groovy engine not available for function [$name]")
        val engineInput = NodeInput(
            directInput = input.directInput,
            nodeParams = mapOf("script" to scriptBody) + input.nodeParams,
            declaredDeps = input.declaredDeps,
            workflowInput = input.workflowInput,
            meta = input.meta
        )
        return engineFn.apply(engineInput).uncheckedCast<FunctionResult<Any>>()!!
    }
}
