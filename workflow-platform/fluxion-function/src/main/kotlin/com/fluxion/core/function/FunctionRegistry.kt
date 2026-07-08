/**
 * Concrete, in-memory implementation of both [FunctionResolver] and
 * [FunctionRegistrar] backing the engine at runtime.
 *
 * The registry is concurrency safe: all mutations go through a
 * `ConcurrentHashMap` and per-function [VersionedFunction] slots use atomic
 * references for active/retiring swap. This is critical because config-center
 * subscribers may hot-reload registrations while the DAG executor is
 * concurrently resolving references for in-flight workflow instances.
 *
 * Lookup order for a bare `functionRef` is:
 * 1. Exact key match in the internal map.
 * 2. Namespace prefixed match using the `nodeType` hint
 *    (`builtin:`, `script:`, `external:`).
 * 3. Throw `FunctionNotFoundException` so callers fail fast.
 *
 * Metadata is kept in a secondary map keyed by the same canonical name so
 * the admin console and observability tooling can introspect registered
 * functions without walking version slots.
 */
package com.fluxion.core.function

import com.fluxion.core.enums.NodeType
import com.fluxion.core.exception.FunctionNotFoundException
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.InlineMeta
import org.slf4j.*
import org.slf4j.debug
import org.slf4j.info
import java.util.concurrent.ConcurrentHashMap

/**
 * Default registry combining the resolver and registrar roles with in-memory
 * versioned storage.
 */
class FunctionRegistry : FunctionResolver, FunctionRegistrar {

    private val log = LoggerFactory.getLogger(javaClass)

    private val functions = ConcurrentHashMap<String, VersionedFunction>()

    private val metaRegistry = ConcurrentHashMap<String, FunctionMeta>()

    override fun register(name: String, meta: FunctionMeta, function: WorkflowFunction<*>) {
        val versioned = functions.computeIfAbsent(name) { VersionedFunction(name) }
        versioned.add(FunctionVersion(0, function.uncheckedCast<WorkflowFunction<Any>>()!!, meta))
        metaRegistry[name] = meta
        log.debug { "Registered workflow function: $name" }
    }

    override fun register(name: String, function: WorkflowFunction<*>) {
        register(name, InlineMeta(name), function)
    }

    fun registerAll(functions: Map<String, WorkflowFunction<*>>) {
        functions.forEach { (name, fn) -> register(name, fn) }
    }

    override fun register(function: WorkflowFunction<*>) {
        register(function.functionName, function)
    }

    /**
     * Registers a named, versioned function with metadata for hot-reload flows.
     *
     * A strictly positive `version` flips the previous active slot into the
     * retiring state so in-flight invocations can drain before cleanup.
     *
     * @param name canonical lookup key
     * @param version monotonic version marker; use `0` for code-first registrations
     * @param meta operator-facing metadata
     * @param function implementation to register
     */
    fun register(name: String, version: Long, meta: FunctionMeta, function: WorkflowFunction<*>) {
        val versioned = functions.computeIfAbsent(name) { VersionedFunction(name) }
        versioned.add(FunctionVersion(version, function.uncheckedCast<WorkflowFunction<Any>>()!!, meta))
        metaRegistry[name] = meta
        log.info { "Registered workflow function: $name version=$version" }
    }

    /**
     * Drops a function entry entirely – called during config-centre rollback
     * or when a plugin is unloaded.
     *
     * @param name canonical lookup key previously used with [register]
     */
    fun unregister(name: String) {
        functions.remove(name)?.remove()
        metaRegistry.remove(name)
        log.info { "Unregistered workflow function: $name" }
    }

    override fun resolve(functionRef: String, nodeType: NodeType?): WorkflowFunction<Any> {
        return findVersioned(functionRef, nodeType)?.resolve()
            ?: throw FunctionNotFoundException(functionRef)
    }

    override fun resolve(functionRef: String): WorkflowFunction<Any> = resolve(functionRef, null)

    override fun resolve(functionRef: String, nodeType: NodeType?, version: Long): WorkflowFunction<Any> {
        return findVersioned(functionRef, nodeType)?.resolve(version)
            ?: throw FunctionNotFoundException("$functionRef@$version")
    }

    private fun findVersioned(functionRef: String, nodeType: NodeType?): VersionedFunction? {
        functions[functionRef]?.let { return it }

        val prefixed = when (nodeType) {
            NodeType.BUILTIN -> "builtin:$functionRef"
            NodeType.SCRIPT -> "script:$functionRef"
            NodeType.EXTERNAL -> "external:$functionRef"
            else -> null
        }
        prefixed?.let { functions[it]?.let { v -> return v } }

        return null
    }

    fun contains(functionRef: String): Boolean = functions.containsKey(functionRef)

    fun listAll(): List<FunctionMeta> = metaRegistry.values.toList()

    fun getMeta(functionRef: String): FunctionMeta? = metaRegistry[functionRef]

    /**
     * Atomically replaces metadata for an existing entry.
     *
     * Called by the config-centre binder when it receives a metadata-only
     * push (documentation/schema edits without a function code change).
     *
     * @param name canonical lookup key
     * @param meta replacement metadata to publish
     */
    fun updateMeta(name: String, meta: FunctionMeta) {
        metaRegistry[name] = meta
    }

    fun size(): Int = functions.size

    fun names(): Collection<String> = functions.keys

    fun activeVersion(functionRef: String): Long? = functions[functionRef]?.activeVersion()

    fun retiringVersion(functionRef: String): Long? = functions[functionRef]?.retiringVersion()

    fun purgeRetiring(functionRef: String): Boolean = functions[functionRef]?.purgeRetiring() ?: false

    /**
     * Scans every registered entry and drops retiring slots whose in-flight
     * counter has reached zero.
     *
     * Invoked periodically by the admin-side housekeeping job to reclaim heap
     * after hot-reload events.
     *
     * @return number of entries whose retiring slot was actually removed
     */
    fun purgeAllRetiring(): Int {
        var count = 0
        functions.values.forEach { if (it.purgeRetiring()) count++ }
        return count
    }
}
