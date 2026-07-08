/**
 * Discovery, routing and lifecycle coordinator for available
 * [ExternalFunctionTransport] implementations.
 *
 * Two complementary registration paths are supported so the registry works
 * identically inside and outside a Spring container:
 * 1. **Explicit registration** – Spring beans (or programmatic callers)
 *    pass instances via the constructor or [register].
 * 2. **ServiceLoader discovery** – the default constructor uses the JDK
 *    `ServiceLoader` to find transports declared in
 *    `META-INF/services/...ExternalFunctionTransport`.
 *
 * In hybrid deployments Spring-registered transports always win over
 * ServiceLoader-discovered ones for the same protocol; an INFO/WARN log
 * line documents every override so operators can debug wiring issues.
 */
package com.fluxion.core.function.external

import org.slf4j.*
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Routes per-protocol invocations to the correct transport and drives the
 * prepare/shutdown lifecycle across all registered transports.
 *
 * @param transports pre-wired instances (e.g. Spring beans) registered first
 * @param useServiceLoader when true, classpath-discovered transports are
 *   loaded after the explicit ones
 */
class ExternalFunctionTransportRegistry(
    transports: List<ExternalFunctionTransport> = emptyList(),
    useServiceLoader: Boolean = true
) {

    private val log = LoggerFactory.getLogger(ExternalFunctionTransportRegistry::class.java)

    private val registry: MutableMap<String, ExternalFunctionTransport> = ConcurrentHashMap()
    private val shutdown = AtomicBoolean(false)

    init {
        if (useServiceLoader) {
            loadFromServiceLoader().forEach { register(it, source = "ServiceLoader") }
        }
        transports.forEach { register(it, source = "explicit") }
    }

    /** Registers an additional transport after construction. */
    fun register(transport: ExternalFunctionTransport) {
        register(transport, source = "explicit")
    }

    private fun register(transport: ExternalFunctionTransport, source: String) {
        val protocol = transport.protocol().lowercase()
        val previous = registry.put(protocol, transport)
        if (previous != null) {
            log.warn {
                "ExternalFunctionTransport for protocol '$protocol' has been overridden by $source: " +
                    "${previous.javaClass.name} -> ${transport.javaClass.name}"
            }
        } else {
            log.info {
                "Registered ExternalFunctionTransport for protocol '$protocol' from $source: ${transport.javaClass.name}"
            }
        }
    }

    /** Removes the transport associated with `protocol`, if any. */
    fun unregister(protocol: String): ExternalFunctionTransport? {
        val key = protocol.lowercase()
        val removed = registry.remove(key)
        removed?.let { log.info { "Unregistered ExternalFunctionTransport for protocol '$key'" } }
        return removed
    }

    /** Resolves the transport responsible for a given protocol name. */
    fun resolve(protocol: String?): ExternalFunctionTransport? {
        return protocol?.lowercase()?.let { registry[it] }
    }

    /** Lists every protocol currently backed by a transport. */
    fun protocols(): Set<String> = registry.keys.toSet()

    /** Probes whether a registered transport exists for the given protocol. */
    fun contains(protocol: String?): Boolean = protocol != null && registry.containsKey(protocol.lowercase())

    /** Current number of registered transports, used for admin health endpoints. */
    fun size(): Int = registry.size

    /**
     * Batches pre-flight preparation across every protocol referenced in the
     * supplied configs.
     *
     * Bucketing by protocol ensures each transport only sees its own
     * declarations, letting it build an accurate cache of channels and
     * references. Unknown protocols are logged at DEBUG and skipped rather
     * than failing fast – an operator may deploy workers without every
     * optional transport.
     */
    fun prepare(configs: List<ExternalFunctionConfig>) {
        if (shutdown.get()) {
            log.warn { "Registry is shutdown, skipping prepare for ${configs.size} config(s)" }
            return
        }
        configs.groupBy { it.protocol.lowercase() }
            .forEach { (protocol, protocolConfigs) ->
                val transport = registry[protocol]
                if (transport != null) {
                    log.info { "Preparing $protocol transport with ${protocolConfigs.size} config(s)" }
                    transport.prepare(protocolConfigs)
                } else {
                    log.debug { "No transport registered for protocol '$protocol', skipping prepare" }
                }
            }
    }

    /**
     * Shuts down every registered transport in reverse registration order
     * and prevents subsequent prepare/invoke calls.
     *
     * Idempotent via an `AtomicBoolean` CAS – repeated calls are logged at
     * DEBUG and become no-ops.
     */
    fun shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            log.debug { "ExternalFunctionTransportRegistry already shutdown, skipping" }
            return
        }
        val transports = registry.values.toList()
        registry.clear()
        transports.forEach { transport ->
            try {
                log.info { "Shutting down external function transport: ${transport.protocol()} (${transport.javaClass.name})" }
                transport.shutdown()
            } catch (ex: Exception) {
                log.warn(ex) { "Failed to shutdown transport ${transport.protocol()} (${transport.javaClass.name})" }
            }
        }
        log.info { "ExternalFunctionTransportRegistry shutdown completed" }
    }

    /** True after [shutdown] has completed its CAS transition. */
    fun isShutdown(): Boolean = shutdown.get()

    companion object {

        /** Discovers transports via the JDK ServiceLoader mechanism. */
        @JvmStatic
        fun loadFromServiceLoader(): List<ExternalFunctionTransport> {
            return ServiceLoader.load(ExternalFunctionTransport::class.java).toList()
        }
    }
}
