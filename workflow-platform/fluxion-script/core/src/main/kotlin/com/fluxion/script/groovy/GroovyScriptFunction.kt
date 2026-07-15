package com.fluxion.script.groovy

import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionResult
import com.fluxion.di.DependencyResolver
import com.fluxion.script.util.BeanNameMap
import com.github.benmanes.caffeine.cache.Cache
import groovy.lang.Binding
import groovy.lang.GroovyShell
import groovy.lang.Script
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.SecureASTCustomizer
import org.slf4j.LoggerFactory

/**
 * Built-in DAG function that executes arbitrary Groovy snippets.
 *
 * Two invocation modes are supported (selected by which node param is set):
 * - **Inline script** (`script` param): the literal Groovy source is passed in
 *   the node's parameters. Used for ad-hoc transforms that live alongside
 *   the workflow definition.
 * - **Named script ref** (`scriptRef` param): a logical identifier that is
 *   resolved through [scriptRefResolver] — deployments typically wire this
 *   through `FunctionConfigSubscriber.get(ref).scriptBody` so the script
 *   lives in the config center and can be updated without touching the
 *   workflow DAG.
 *
 * Compilation caching: the raw source's hashCode is used as the cache key
 * into a bounded Caffeine cache (default 500 entries) of compiled
 * `Class<out Script>` objects. Once warmed, the hot path is a newInstance()
 * + binding assign + run() call — ~zero parser overhead per invocation.
 *
 * Security: a `SecureASTCustomizer` whitelists the import classes the
 * script may use (`java.util.*`, `java.math.*`, `groovy.json.*`). Scripts
 * cannot directly `import` classes outside this list (they CAN still
 * reference fully-qualified names — to fully lock this down replace the
 * customizer with a stricter AST visitor).
 *
 * Target bytecode is set to JDK 17 so modern Java APIs (records, pattern
 * matching, Text Blocks compiled from Groovy 4+) are available to scripts.
 */
class GroovyScriptFunction(
    compileCache: Cache<Int, Class<*>>
) : WorkflowFunction<Any> {

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:groovyScript"

    /** Cache keyed by script source hashCode → compiled Script subclass. */
    private val compileCache = compileCache

    /**
     * Optional resolver that maps a `scriptRef` identifier to the actual
     * Groovy source. Typically wired in Spring Boot to look up the ref
     * against the `FunctionConfigSubscriber` snapshot store.
     */
    var scriptRefResolver: ((String) -> String?)? = null

    /**
     * Optional DI resolver; when set exposes bean-lookup helpers in the
     * script's Groovy binding (see [buildBinding]).
     */
    var dependencyResolver: DependencyResolver? = null

    private val shell: GroovyShell

    init {
        val config = CompilerConfiguration()
        config.targetBytecode = CompilerConfiguration.JDK17

        // Restrict script imports — prevents naive usage of unsafe classes.
        // NOTE: fully-qualified classnames bypass this check; for full
        // sandboxing add a second AST customizer that blocks class literals.
        val secure = SecureASTCustomizer()
        secure.allowedImports = listOf("java.util.*", "java.math.*", "groovy.json.*")
        config.addCompilationCustomizers(secure)

        this.shell = GroovyShell(Binding(), config)
    }

    /**
     * Run a script invocation.
     *
     * Source resolution order:
     * 1. `input.param("script")` — inline body from node params.
     * 2. `input.param("scriptRef")` → [scriptRefResolver] lookup.
     * 3. If both are absent/empty → fail fast with `WorkflowNodeException`.
     *
     * The cached Script class is instantiated fresh per invocation so the
     * same compiled class can be called concurrently (Scripts are NOT
     * thread-safe; the Binding is per-instance).
     */
    override fun apply(input: NodeInput): FunctionResult<Any> {
        var rawScript: String? = input.param("script")
        if (rawScript.isNullOrBlank()) {
            val scriptRef = input.param<String>("scriptRef")
            if (!scriptRef.isNullOrBlank()) {
                rawScript = scriptRefResolver?.invoke(scriptRef) ?: scriptRef
            }
        }
        if (rawScript.isNullOrBlank()) {
            throw WorkflowNodeException(functionName,
                IllegalArgumentException("Either 'script' or 'scriptRef' param is required"))
        }

        val script: String = rawScript
        val scriptHash = script.hashCode()

        val binding = buildBinding(input)
        try {
            @Suppress("UNCHECKED_CAST")
            val scriptClass: Class<out Script> = compileCache.get(scriptHash) {
                try {
                    shell.parse(script, "WorkflowScript_$scriptHash").javaClass
                } catch (ex: CompilationFailedException) {
                    throw RuntimeException("Script compilation failed: ${ex.message}", ex)
                }
            } as Class<out Script>

            val instance = scriptClass.getDeclaredConstructor().newInstance()
            instance.binding = binding
            val result: Any? = instance.run()
            return FunctionResult.success(result).uncheckedCast<FunctionResult<Any>>()!!
        } catch (ex: RuntimeException) {
            if (ex.cause is CompilationFailedException) {
                throw WorkflowNodeException(functionName, ex.cause)
            }
            throw WorkflowNodeException(functionName, ex)
        } catch (ex: Exception) {
            throw WorkflowNodeException(functionName, ex)
        }
    }

    /**
     * Build the Groovy script binding — exposes the DAG context as script variables.
     *
     * Variables always available:
     * - `input`    — [NodeInput.directInput] (the DAG-level resolved dependency payload).
     * - `params`   — [NodeInput.nodeParams]   (raw node-level configuration params).
     * - `deps`     — [NodeInput.declaredDeps] (raw dependency-output map before merging).
     * - `wfInput`  — [NodeInput.workflowInput] (the original top-level workflow request params).
     * - `meta`     — [NodeInput.meta] (workflow id, tenant, trace id …).
     *
     * Variables available only when [dependencyResolver] is configured:
     * - `bean(name)`              → lookup bean by name.
     * - `beanByType(Class)`       → lookup bean by runtime class.
     * - `beanType(String)`        → lookup by FQCN string (convenience for scripts).
     * - `beans`                   → [BeanNameMap] proxy for `beans.foo` property access.
     */
    private fun buildBinding(input: NodeInput): Binding {
        val binding = Binding()
        binding.setVariable("input", input.directInput)
        binding.setVariable("params", input.nodeParams)
        binding.setVariable("deps", input.declaredDeps)
        binding.setVariable("wfInput", input.workflowInput)
        binding.setVariable("meta", input.meta)

        dependencyResolver?.let { resolver ->
            binding.setVariable("bean") { name: String -> resolver.resolve(name) }
            binding.setVariable("beanByType") { type: Class<*> -> resolver.resolveByType(type) }
            binding.setVariable("beanType") { className: String ->
                resolver.resolveByType(Class.forName(className))
            }
            binding.setVariable("beans", BeanNameMap(resolver))
        }

        return binding
    }
}
