package com.fluxion.runtime.core.provider

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.DefinitionChangeListener
import com.fluxion.adapter.spi.config.KeyedConfigSubscriber
import com.fluxion.adapter.spi.config.WorkflowDefinitionSnapshot
import com.fluxion.core.enums.NodeType
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.util.JsonUtil
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 配置中心驱动的 DefinitionProvider — 业务实例侧核心组件
 *
 * 从 [KeyedConfigSubscriber] 加载工作流定义并监听变更，
 * 本地维护 [ConcurrentHashMap] 缓存，供路由器快速查找。
 */
class ConfigBackedDefinitionProvider @JvmOverloads constructor(
    private val subscriber: KeyedConfigSubscriber<WorkflowDefinitionSnapshot>,
    private val appGroup: String? = null
) : DefinitionProvider, DefinitionChangeListener {

    private val log = LoggerFactory.getLogger(javaClass)
    private val definitions = ConcurrentHashMap<String, WorkflowDefinition>()
    private val bindingIndex = ConcurrentHashMap<String, String>()

    fun init() {
        // 先注册监听器，后续推送走回调管线；消除 loadAll→watch 之间的竞态窗口
        subscriber.watch(this)

        // 初始加载作为首次快照，走同一条 applySnapshot 管线
        val snapshots = subscriber.loadAll()
        for (snap in snapshots) {
            applySnapshot(snap)
        }
        log.info("ConfigBackedDefinitionProvider initialized with ${definitions.size} definitions")
    }

    override fun get(key: String): WorkflowDefinition? = definitions[key]

    override fun findByBinding(protocol: String, bindKey: String): String? =
        bindingIndex["${protocol.uppercase()}:$bindKey"]

    fun loadAll(): List<WorkflowDefinition> = definitions.values.toList()

    override fun onChange(key: String, config: WorkflowDefinitionSnapshot, changeType: ChangeType) {
        when (changeType) {
            ChangeType.PUBLISH, ChangeType.UPDATE -> applySnapshot(config)
            ChangeType.REMOVE -> removeDefinition(key)
        }
        log.info("Definition change applied: workflowId=$key type=$changeType")
    }

    private fun applySnapshot(snap: WorkflowDefinitionSnapshot?) {
        if (snap == null) return
        val definitionJson = snap.definitionJson
        if (definitionJson.isNullOrBlank()) return

        // 订阅端过滤
        if (appGroup != null && !snap.isGlobal()) {
            if (snap.targetGroups?.contains(appGroup) != true) {
                log.debug("Skipping definition ${snap.workflowId} — targetGroups ${snap.targetGroups} does not include appGroup [$appGroup]")
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
            log.error("Failed to parse definition snapshot for workflowId=${snap.workflowId}: ${e.message}", e)
        }
    }

    private fun removeDefinition(workflowId: String) {
        definitions.remove(workflowId)
        bindingIndex.entries.removeIf { it.value == workflowId }
    }

    /**
     * 归一化节点 Map 的 type 字段：兼容 FunctionNodeType（如 DATA_QUERY）→ 核心 NodeType。
     * 前端可能发送 12 种 FunctionNodeType 值，但核心 WorkflowNode.type 仅接受 BUILTIN/CUSTOM/SCRIPT/EXTERNAL。
     */
    private val coreNodeTypeValues = NodeType.entries.map { it.name }.toSet()

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

    fun size(): Int = definitions.size
}
