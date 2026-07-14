package com.fluxion.script.config

import com.fluxion.config.core.ChangeType
import com.fluxion.config.core.FunctionChangeListener
import com.fluxion.config.core.FunctionConfigSnapshot
import com.fluxion.config.core.FunctionConfigSubscriber
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.value.FunctionMeta
import com.fluxion.outbound.OutboundConfig
import com.fluxion.outbound.OutboundConfigParser
import com.fluxion.outbound.OutboundTransportRegistry
import com.fluxion.script.function.ExternalWorkflowFunction
import com.fluxion.script.function.ScriptWorkflowFunction
import com.fluxion.script.groovy.GroovyScriptFunction
import org.slf4j.*
/**
 * Worker-side bridge between the config center (Apollo / Nacos / in-memory)
 * and the engine's live [FunctionRegistry] — converts push-based
 * `FunctionConfigSnapshot` events into register / unregister calls.
 *
 * Invocation flow:
 * 1. At startup `init()` is called (by the Spring Boot auto-config or test harness).
 * 2. We pull all initial snapshots via [FunctionConfigSubscriber.loadAll].
 * 3. Each snapshot is converted to a concrete `WorkflowFunction` + `FunctionMeta`
 *    and registered with [registry] keyed by `version` so hot-redeploy
 *    retains previous versions for in-flight workflows.
 * 4. After the initial load, `OutboundTransportRegistry.prepare` is
 *    called with all external configs so HTTP/Dubbo/gRPC transport
 *    implementations can pre-build connection pools / stubs.
 * 5. We then call [FunctionConfigSubscriber.watch] and any subsequent
 *    push-based updates arrive through [onChange].
 *
 * Supported `functionType` values:
 * - `SCRIPT_GROOVY` / `GROOVY` → wraps as [ScriptWorkflowFunction] (delegates
 *   to the single shared [GroovyScriptFunction] engine at execution time).
 * - `EXTERNAL` → wraps as [ExternalWorkflowFunction] (delegates to the
 *   transport-specific `OutboundTransport` resolved by protocol).
 * - Unknown values are logged and skipped (keeps old clients from breaking
 *   when future function types are introduced).
 *
 * App-group scoping: if `appGroup` is non-null and the snapshot is NOT
 * global, the function is only applied when the snapshot's target-group
 * set contains the configured app group. Allows a single config center
 * namespace to serve multiple fleet partitions (canary / staging / prod).
 */
class FunctionConfigApplier(
    private val subscriber: FunctionConfigSubscriber,
    private val registry: FunctionRegistry,
    private val groovyEngine: GroovyScriptFunction? = null,
    private val appGroup: String? = null,
    private val transportRegistry: OutboundTransportRegistry = OutboundTransportRegistry()
) : FunctionChangeListener {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Load the full snapshot set from [subscriber], register every function
     * into [registry], pre-warm external transports, and start the
     * push-based watcher.
     *
     * MUST be called exactly once after construction; the Spring Boot
     * auto-config handles this automatically.
     */
    fun init() {
        val snapshots = subscriber.loadAll()
        val externalConfigs = mutableListOf<OutboundConfig>()
        for (snap in snapshots) {
            applySnapshot(snap, ChangeType.PUBLISH, externalConfigs)
        }
        transportRegistry.prepare(externalConfigs)
        log.info { "FunctionConfigApplier initialized with ${snapshots.size} functions" }
        subscriber.watch(this)
    }

    /**
     * Single push-event handler — applies a single snapshot diff.
     *
     * REMOVE events go straight to [removeFunction]. PUBLISH / UPDATE
     * events pass through [applySnapshot] (which handles scope + enabled
     * checks internally) and then force [transportRegistry.prepare] so
     * newly-introduced outbound protocols get their connection pools
     * pre-warmed.
     */
    override fun onChange(functionName: String, snapshot: FunctionConfigSnapshot, changeType: ChangeType) {
        when (changeType) {
            ChangeType.PUBLISH, ChangeType.UPDATE -> {
                val externalConfigs = mutableListOf<OutboundConfig>()
                applySnapshot(snapshot, changeType, externalConfigs)
                transportRegistry.prepare(externalConfigs)
            }
            ChangeType.REMOVE -> removeFunction(functionName)
        }
    }

    /**
     * Apply a single snapshot to the local registry.
     *
     * Pipeline (short-circuits on each condition):
     * 1. Scope check — if app group filtering is on and the function is
     *    pinned to other groups, skip entirely.
     * 2. Enabled check — if `snapshot.enabled=false` the function should
     *    NOT be callable; forward to [removeFunction] for cleanup.
     * 3. Normalise name by function-type prefix (`script:` / `external:`).
     * 4. Build [FunctionMeta] from description + param/output schemas.
     * 5. Build concrete `WorkflowFunction` implementation by type switch.
     * 6. Register with version so in-flight workflows retain pinned versions.
     *
     * @param snapshot         Current config-snapshot pushed by the subscriber.
     * @param changeType       PUBLISH / UPDATE (used only for structured logging).
     * @param externalConfigs  Accumulator — EXTERNAL snapshots append their
     *                         parsed config to this list so the caller can
     *                         batch-invoke `transportRegistry.prepare` after
     *                         the loop (single bulk warmup).
     */
    private fun applySnapshot(
        snapshot: FunctionConfigSnapshot,
        changeType: ChangeType,
        externalConfigs: MutableList<OutboundConfig> = mutableListOf()
    ) {
        // Skip when worker's app-group is not in the snapshot's target set.
        if (appGroup != null && !snapshot.isGlobal()) {
            if (snapshot.targetGroups?.contains(appGroup) != true) {
                log.debug { "Skipping function ${snapshot.functionName} — targetGroups ${snapshot.targetGroups} does not include appGroup [$appGroup]" }
                return
            }
        }

        // Disabled entries behave exactly like REMOVE (unregister live versions).
        if (!snapshot.enabled) {
            removeFunction(snapshot.functionName)
            return
        }

        val functionName = normalizeName(snapshot.functionName, snapshot.functionType)
        val meta = FunctionMeta.builder(functionName)
            .description(snapshot.description ?: functionName)
            .apply {
                snapshot.paramSchema?.let { paramSchema(it) }
                snapshot.outputSchema?.let { outputSchema(it) }
            }
            .build()

        val function = when (snapshot.functionType.uppercase()) {
            "SCRIPT_GROOVY", "GROOVY" -> {
                val script = snapshot.scriptBody
                if (script.isNullOrBlank()) {
                    log.warn { "Skipping script function [$functionName] — scriptBody is empty" }
                    return
                }
                ScriptWorkflowFunction(
                    name = functionName,
                    scriptBody = script,
                    paramSchema = snapshot.paramSchema,
                    outputSchema = snapshot.outputSchema,
                    description = snapshot.description,
                    groovyEngine = groovyEngine
                )
            }
            "EXTERNAL" -> {
                val externalConfig = OutboundConfigParser.parse(snapshot.config)
                externalConfigs.add(externalConfig)
                ExternalWorkflowFunction(
                    name = functionName,
                    config = externalConfig,
                    transportRegistry = transportRegistry,
                    paramSchema = snapshot.paramSchema,
                    outputSchema = snapshot.outputSchema,
                    description = snapshot.description
                )
            }
            else -> {
                log.warn { "Unsupported function type [${snapshot.functionType}] for [$functionName], skipping" }
                return
            }
        }

        registry.register(functionName, snapshot.version, meta, function)
        log.info { "Applied function [$functionName] type=${snapshot.functionType} version=${snapshot.version} change=$changeType" }
    }

    /**
     * Remove a function (both bare name and common type-prefixed variants).
     *
     * We unregister both the raw name AND `script:`/`external:` prefixed
     * forms to be robust against operators accidentally registering with
     * or without the prefix at different points in time.
     */
    private fun removeFunction(functionName: String) {
        registry.unregister(functionName)
        registry.unregister("script:`$functionName")
        registry.unregister("external:`$functionName")
        log.info { "Removed function [$functionName] from registry" }
    }

    /**
     * Ensure published function names carry a type-specific prefix so two
     * functions (e.g. a script "userLookup" and an external "userLookup")
     * don't collide in the shared registry namespace.
     */
    private fun normalizeName(functionName: String, functionType: String): String {
        return when (functionType.uppercase()) {
            "SCRIPT_GROOVY", "GROOVY" -> {
                if (functionName.startsWith("script:")) functionName else "script:$functionName"
            }
            "EXTERNAL" -> {
                if (functionName.startsWith("external:")) functionName else "external:$functionName"
            }
            else -> functionName
        }
    }
}
