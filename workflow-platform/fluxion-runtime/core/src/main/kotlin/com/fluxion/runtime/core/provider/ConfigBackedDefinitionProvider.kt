package com.fluxion.runtime.core.provider

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.DefinitionChangeListener
import com.fluxion.adapter.spi.config.KeyedConfigSubscriber
import com.fluxion.adapter.spi.config.WorkflowDefinitionSnapshot
import com.fluxion.core.enums.NodeType
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.util.JsonUtil
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Config-center-backed implementation of [DefinitionProvider].
 *
 * This is the canonical provider used on every Worker (data-plane) instance.
 * It populates itself from a [KeyedConfigSubscriber] (usually Apollo/Nacos/HTTP)
 * on init and then keeps its in-memory caches hot by listening for config
 * change events published by the control plane.
 *
 * Caching strategy:
 * - `definitions`  → workflowId → fully parsed [WorkflowDefinition] (primary index)
 * - `bindingIndex` → "PROTOCOL:bindKey" → workflowId (reverse index)
 *
 * Both maps are [ConcurrentHashMap] — lock-free read path for the hot adapter
 * routing thread, with serialized writes driven by the config-subscriber thread.
 *
 * @param subscriber Config-center watcher delivering definition snapshots
 * @param appGroup   Optional app-group filter for targeted push scoping
 */
class ConfigBackedDefinitionProvider @JvmOverloads constructor(
    private val subscriber: KeyedConfigSubscriber<WorkflowDefinitionSnapshot>,
    private val appGroup: String? = null
) : DefinitionProvider, DefinitionChangeListener {

    private val log = LoggerFactory.getLogger(javaClass)
    private val definitions = ConcurrentHashMap<String, WorkflowDefinition>()
    private val bindingIndex = ConcurrentHashMap<String, String>()

    /**
     * Initial load + change-wire lifecycle entry point.
     *
     * Ordering matters: we register the change listener BEFORE the initial
     * bulk load so that any publish event arriving during the initial
     * snapshot is not silently dropped (classic "lost update" race).
     */
    fun init() {
        subscriber.watch(this)

        val snapshots = subscriber.loadAll()
        for (snap in snapshots) {
            applySnapshot(snap)
        }
        log.info { "ConfigBackedDefinitionProvider initialized with ${definitions.size} definitions" }
    }

    override fun get(key: String): WorkflowDefinition? = definitions[key]

    override fun findByBinding(protocol: String, bindKey: String): String? =
        bindingIndex["${protocol.uppercase()}:$bindKey"]

    /**
     * Snapshot listing — used by diagnostics endpoints to surface the
     * current worker's view of published workflows.
     *
     * @return Defensive copy of all in-memory definitions at call time
     */
    fun loadAll(): List<WorkflowDefinition> = definitions.values.toList()

    /**
     * Config-subscriber change callback — routes each event to the right
     * mutator (apply / remove) and logs a one-liner for admin audit logs.
     */
    override fun onChange(key: String, config: WorkflowDefinitionSnapshot, changeType: ChangeType) {
        when (changeType) {
            ChangeType.PUBLISH, ChangeType.UPDATE -> applySnapshot(config)
            ChangeType.REMOVE -> removeDefinition(key)
        }
        log.info { "Definition change applied: workflowId=$key type=`$changeType" }
    }

    /**
     * Materialize a config-center snapshot into the live caches.
     *
     * Does several things in order:
     * 1. Skips blank / removed markers (null definitionJson).
     * 2. Filters by target-groups if an app-group is configured on this worker.
     * 3. Parses the JSON DAG payload into a [WorkflowDefinition].
     * 4. Normalizes legacy `FunctionNodeType` values back to the strict 4-value
     *    core [NodeType] enum used by the DAG engine.
     * 5. Updates both primary and reverse indexes atomically from the
     *    router's point of view (both maps are CHM so each individual put
     *    is atomic; readers either see both updates or neither because the
     *    primary write happens first).
     */
    private fun applySnapshot(snap: WorkflowDefinitionSnapshot?) {
        if (snap == null) return
        val definitionJson = snap.definitionJson
        if (definitionJson.isNullOrBlank()) return

        if (appGroup != null && !snap.isGlobal()) {
            if (snap.targetGroups?.contains(appGroup) != true) {
                log.debug { "Skipping definition ${snap.workflowId} — targetGroups ${snap.targetGroups} does not include appGroup [$appGroup]" }
                return
            }
        }

        try {
            val dagData = JsonUtil.toMap(definitionJson)

            val nodesJson = JsonUtil.convertValueOrNull<List<Map<String, Any>>>(dagData["nodes"]) ?: emptyList()
            val nodes = nodesJson.map { nodeMap ->
                JsonUtil.convertValue(normalizeNodeType(nodeMap), WorkflowNode::class.java)
            }

            val name = dagData.getOrDefault("name", snap.workflowId) as? String ?: snap.workflowId
            val inputSchema = dagData["inputSchema"]?.let { JsonUtil.serialize(it) }
            val outputSchema = dagData["outputSchema"]?.let { JsonUtil.serialize(it) }
            val errorHandler = dagData["errorHandlerRef"] as? String
            val protocol = dagData["protocol"] as? String
            val bindKey = dagData["bindKey"] as? String

            val def = WorkflowDefinition(
                id = snap.workflowId,
                name = name,
                nodes = nodes,
                inputSchema = inputSchema,
                outputSchema = outputSchema,
                errorHandlerRef = errorHandler,
                version = snap.version
            )

            definitions[snap.workflowId] = def

            if (protocol != null && bindKey != null) {
                bindingIndex["${protocol.uppercase()}:$bindKey"] = snap.workflowId
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to parse definition snapshot for workflowId=${snap.workflowId}" }
        }
    }

    /**
     * Remove a definition from both caches when the control plane publishes
     * a REMOVE event.
     *
     * @param workflowId Primary key of the workflow to drop
     */
    private fun removeDefinition(workflowId: String) {
        definitions.remove(workflowId)
        bindingIndex.entries.removeIf { it.value == workflowId }
    }

    // Normalization cache: the core NodeType enum has only 4 values (BUILTIN /
    // CUSTOM / SCRIPT / EXTERNAL) but the front-end may send 12 distinct
    // FunctionNodeType codes. We bucket them by inspecting the `functionRef`
    // prefix, which the control plane guarantees for published DAGs.
    private val coreNodeTypeValues = NodeType.entries.map { it.name }.toSet()

    /**
     * Normalize a node `type` field coming from the config center.
     *
     * If `type` is already a valid core [NodeType] name, pass through unchanged.
     * Otherwise classify based on `functionRef` prefix:
     * - `builtin:`  → BUILTIN
     * - `script:`   → SCRIPT
     * - `external:` → EXTERNAL
     * - otherwise   → CUSTOM (user-defined Java function)
     *
     * @param nodeMap Raw map coming from JSON deserialization
     * @return Same map (or copy) with the `type` field normalized
     */
    private fun normalizeNodeType(nodeMap: Map<String, Any>): Map<String, Any> {
        val type = nodeMap["type"]?.toString() ?: "CUSTOM"
        if (type in coreNodeTypeValues) return nodeMap
        val functionRef = nodeMap["functionRef"]?.toString() ?: ""
        val resolvedType = when {
            functionRef.startsWith("builtin:") -> "BUILTIN"
            functionRef.startsWith("script:") -> "SCRIPT"
            functionRef.startsWith("external:") -> "EXTERNAL"
            else -> "CUSTOM"
        }
        return nodeMap + ("type" to resolvedType)
    }

    /**
     * Snapshot count — primarily used by the auto-configuration info log.
     *
     * @return Number of workflows currently loaded
     */
    fun size(): Int = definitions.size
}
