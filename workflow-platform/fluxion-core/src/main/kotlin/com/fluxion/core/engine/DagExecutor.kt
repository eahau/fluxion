package com.fluxion.core.engine

import com.fluxion.core.engine.control.LoopExecutor
import com.fluxion.core.engine.control.SubWorkflowExecutor
import com.fluxion.core.engine.control.WaitExecutor
import com.fluxion.core.enums.NodeType
import com.fluxion.core.exception.CyclicDependencyException
import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.mock.MockConfig
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.value.EngineResult
import com.fluxion.core.value.NodeExecutionRecord
import com.fluxion.schema.api.SchemaBackedMap
import com.fluxion.schema.api.SchemaDataProvider
import com.fluxion.schema.api.SchemaDataProviderRegistry
import com.fluxion.schema.model.SchemaFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import org.slf4j.*

/**
 * DAG 并行执行器 — Kotlin Coroutines
 *
 * 无依赖关系的节点自动并行，有依赖的节点等待前置完成。
 *
 * 算法：
 *   1. 拓扑排序检测环（Kahn 算法）
 *   2. 计算每个节点的 in-degree（未满足的依赖数）
 *   3. in-degree=0 的节点入就绪队列，并发执行
 *   4. 任一节点完成 → 更新不可变状态快照 → 后继节点 in-degree--
 *   5. 重复直到所有节点完成
 *
 * 注意：
 *   DagExecutor 与 WorkflowEngine 同包，直接调用 engine.executeNode() 逐节点执行
 */
class DagExecutor(
    private val engine: WorkflowEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val taskInterceptor: TaskInterceptor = TaskInterceptor.NOOP,
    private val executionTracker: ExecutionTracker? = null,
    private val loopExecutor: LoopExecutor? = null,
    private val waitExecutor: WaitExecutor? = null,
    private val subWorkflowExecutor: SubWorkflowExecutor? = null,
    private val workflowDecoratorRegistry: com.fluxion.core.decorator.WorkflowDecoratorRegistry? = null,
    private val providerRegistry: SchemaDataProviderRegistry? = null
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 执行 DAG 工作流（suspend 函数，由协程调用方驱动）。
     *
     * 若 [WorkflowDefinition.workflowDecorators] 配置了工作流级装饰器，则按顺序构建洋葱模型装饰链，
     * 再执行实际 DAG 编排逻辑。
     */
    suspend fun execute(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        mockConfig: MockConfig = MockConfig.EMPTY
    ): EngineResult {
        val doExecute: suspend () -> EngineResult = { executeDecorated(def, rawInput, mockConfig) }
        val decorated = def.workflowDecorators.orEmpty()
            .foldRight(doExecute) { decoratorName, next ->
                val decorator = workflowDecoratorRegistry?.resolve(decoratorName)
                    ?: throw IllegalStateException(
                        "Workflow decorator '$decoratorName' not found for workflow [${def.id}]. " +
                            "Ensure WorkflowDecoratorRegistry is configured."
                    )
                suspend { decorator.decorate(def, rawInput, next) }
            }
        return decorated()
    }

    /**
     * 执行 DAG 工作流核心逻辑（不含工作流级装饰器包装）。
     */
    private suspend fun executeDecorated(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        mockConfig: MockConfig = MockConfig.EMPTY
    ): EngineResult = coroutineScope {

        // wrap dispatcher：参考 OTel Context.wrap(Executor)，在 dispatch 层面传播上下文
        val wrappedDispatcher = taskInterceptor.wrap(dispatcher)
        val lockedNodes = def.nodes.toList()

        // 0. 节点配置合法性校验（包括 retryCount 上限等）
        lockedNodes.forEach { it.validate() }

        // 1. 入口入参 Schema 契约校验（始终生效，不依赖 paramValidate 节点）
        engine.validateWorkflowInput(def, rawInput)

        // 2. Schema 数据访问：根据 inputSchemaFormat 解析 provider，wrap rawInput
        val provider = resolveProvider(def.inputSchemaFormat)
        @Suppress("UNCHECKED_CAST")
        val schemaAwareInput: Map<String, Any> =
            if (provider != null) SchemaBackedMap.wrap(rawInput, provider) as Map<String, Any>
            else rawInput

        // 3. 直接取预计算好的拓扑序；为空且存在节点时说明成环（理论上发布时已拦截）
        val sortedIds = def.sortedNodeIds
        if (sortedIds.isEmpty() && lockedNodes.isNotEmpty()) {
            throw CyclicDependencyException(def.id)
        }

        // 4. 初始不可变状态（inputs 已是 SchemaBackedMap，下游 workflowInput 自动 schema 感知）
        var state = ImmutableExecutionState.start(def, schemaAwareInput)

        // 注册运行态追踪（供 Signal/Query API 查询）
        executionTracker?.trackStart(
            executionId = state.meta.executionId,
            workflowId = def.id,
            workflowName = def.name,
            version = def.version,
            inputs = rawInput,
            nodeIds = lockedNodes.map { it.id }
        )

        try {

        // 4. 计算各节点 in-degree（未满足的依赖数）
        val inDegree = lockedNodes.associate { node ->
            node.id to (node.dependsOn?.size ?: 0)
        }.toMutableMap()

        // 4.1 预计算后继节点邻接表（O(1) 查找，替代原先每次 O(n) 的全量扫描）
        val successorMap: Map<String, List<WorkflowNode>> = buildSuccessorMap(lockedNodes)

        // 5. 初始就绪队列（in-degree = 0 的节点）
        val readyQueue = ArrayDeque(lockedNodes.filter { (inDegree[it.id] ?: 0) == 0 })
        val pendingResults = mutableMapOf<String, Deferred<NodeExecutionRecord>>()
        val executionTrace  = mutableListOf<NodeExecutionRecord>()

        // 异步节点执行记录收集器（线程安全，异步节点在独立协程中执行）
        val asyncTraceRecords = java.util.concurrent.ConcurrentLinkedQueue<NodeExecutionRecord>()

        while (readyQueue.isNotEmpty() || pendingResults.isNotEmpty()) {
            // 6. 并发启动所有就绪节点
            while (readyQueue.isNotEmpty()) {
                val node        = readyQueue.removeFirst()
                val directInput = resolveDirectInput(node, state, provider)
                val capturedState = state  // lambda 捕获当前快照

                if (node.asyncExecution) {
                    // 异步节点：使用 launch，不加入 pendingResults，不阻塞后续节点
                    launch(wrappedDispatcher) {
                        val startNano = System.nanoTime()
                        try {
                            val record = engine.executeNode(node, directInput, capturedState, mockConfig, def.inputSchemaFormat)
                            asyncTraceRecords.add(record)
                            log.debug { "Async DAG node [${record.nodeName}] completed, status=${record.status}, duration=${record.durationMs}ms" }
                        } catch (ex: Exception) {
                            val durationMs = (System.nanoTime() - startNano) / 1_000_000L
                            val failedRecord = NodeExecutionRecord.failed(node, ex, durationMs)
                            asyncTraceRecords.add(failedRecord)
                            log.error(ex) { "Async node [${node.name}] failed (non-blocking): ${ex.message}" }
                        }
                    }
                    // 异步节点视为立即完成，释放后继节点（O(1) 邻接表查找）
                    releaseSuccessors(node.id, successorMap, inDegree, readyQueue)
                } else {
                    pendingResults[node.id] = async(wrappedDispatcher) {
                        executeNodeDispatch(node, directInput, capturedState, mockConfig, lockedNodes, def.inputSchemaFormat)
                    }
                }
            }

            // 若无同步节点等待，继续下一轮
            if (pendingResults.isEmpty()) continue

            // 7. 等待任一节点完成（select first）
            val (completedId, record) = awaitFirst(pendingResults)
            pendingResults.remove(completedId)
            executionTrace.add(record)

            log.debug { "DAG node [${record.nodeName}] completed, status=${record.status}, duration=${record.durationMs}ms" }

            // 8. 更新不可变状态（原子操作，产生新快照）
            if (record.output != null) {
                state = state.withNodeOutput(completedId, record.output)
            }

            // 9. 更新后继节点 in-degree，将就绪节点加入队列（O(1) 邻接表查找）
            releaseSuccessors(completedId, successorMap, inDegree, readyQueue)
        }

        // 合并异步节点执行记录到主执行轨迹
        executionTrace.addAll(asyncTraceRecords)

        val finalOutput = if (sortedIds.isNotEmpty()) state.getNodeOutput<Any?>(sortedIds.last()) else null
        // 最终输出 Schema 约束校验（工作流级 outputSchema 作为对外契约承诺，始终生效）
        engine.validateWorkflowOutput(def, finalOutput)
        EngineResult.success(finalOutput, state).also { it.trace.addAll(executionTrace) }

        } finally {
            executionTracker?.trackEnd(state.meta.executionId)
        }
    }

    // ─── DAG 辅助方法 ─────────────────────────────────────────────

    /**
     * 根据 schema 格式码解析对应的 [SchemaDataProvider]。
     *
     * @return provider（有注册表且有对应格式时）或 null（回退到传统 Map 访问）
     */
    private fun resolveProvider(schemaFormat: String?): SchemaDataProvider? {
        val registry = providerRegistry ?: return null
        val format = SchemaFormat.fromCodeOrNull(schemaFormat) ?: return null
        return try {
            registry.getProvider(format)
        } catch (e: IllegalArgumentException) {
            log.debug("No SchemaDataProvider for format [{}], falling back to Map access", schemaFormat)
            null
        }
    }

    /**
     * 节点执行分发：根据节点类型路由到不同执行器。
     *
     * - LOOP → [LoopExecutor]
     * - WAIT → [WaitExecutor]
     * - SUB_WORKFLOW → [SubWorkflowExecutor]
     * - 其他 → [WorkflowEngine.executeNode]
     */
    private suspend fun executeNodeDispatch(
        node: WorkflowNode,
        directInput: Any?,
        state: ImmutableExecutionState,
        mockConfig: MockConfig,
        allNodes: List<WorkflowNode>,
        schemaFormat: String? = null
    ): NodeExecutionRecord {
        return when (node.type) {
            NodeType.LOOP -> {
                val executor = loopExecutor
                    ?: throw WorkflowNodeException.timeout("LoopExecutor not configured; cannot execute LOOP node [${node.name}]", null)
                executor.execute(node, state, mockConfig, allNodes)
            }
            NodeType.WAIT -> {
                val executor = waitExecutor
                    ?: throw WorkflowNodeException.timeout("WaitExecutor not configured; cannot execute WAIT node [${node.name}]", null)
                executor.execute(node, state)
            }
            NodeType.SUB_WORKFLOW -> {
                val executor = subWorkflowExecutor
                    ?: throw WorkflowNodeException.timeout("SubWorkflowExecutor not configured; cannot execute SUB_WORKFLOW node [${node.name}]", null)
                executor.execute(node, directInput, state, mockConfig)
            }
            else -> engine.executeNode(node, directInput, state, mockConfig, schemaFormat)
        }
    }

    /**
     * 等待 pendingResults 中任意一个 Deferred 完成（select first completed）
     * @return Pair(completedNodeId, NodeExecutionRecord)
     */
    private suspend fun awaitFirst(
        pending: Map<String, Deferred<NodeExecutionRecord>>
    ): Pair<String, NodeExecutionRecord> = select {
        pending.forEach { (id, deferred) ->
            deferred.onAwait { record -> id to record }
        }
    }

    /**
     * 解析节点的直接入参（Schema 感知）。
     * - 有 dependsOn 的节点：directInput = null（由 NodeInput.declaredDeps 提供）
     * - DAG 起点节点：取 rawInput（保存在 state.inputs，已包装为 SchemaBackedMap）
     * - 线性中间节点：取当前 state 中最后一个有值的节点输出，用 SchemaBackedMap 包装
     */
    private fun resolveDirectInput(
        node: WorkflowNode,
        state: ImmutableExecutionState,
        provider: SchemaDataProvider?
    ): Any? {
        if (!node.dependsOn.isNullOrEmpty()) return null
        val raw = state.nodeOutputs.values.lastOrNull() ?: return state.inputs
        return SchemaBackedMap.wrap(raw, provider)
    }

    /**
     * 构建后继节点邻接表（parentId → 依赖该节点的子节点列表）。
     *
     * 时间复杂度 O(n)，一次构建后每次查找 O(1)，
     * 替代原先每轮 O(n) 的 `lockedNodes.filter { ... }` 全量扫描。
     */
    private fun buildSuccessorMap(nodes: List<WorkflowNode>): Map<String, List<WorkflowNode>> {
        val map = mutableMapOf<String, MutableList<WorkflowNode>>()
        for (node in nodes) {
            node.dependsOn?.forEach { parentId ->
                map.getOrPut(parentId) { mutableListOf() }.add(node)
            }
        }
        return map
    }

    /**
     * 释放后继节点：将已完成节点的所有后继 in-degree 减 1，
     * 将就绪（in-degree=0）的后继加入 readyQueue。
     */
    private fun releaseSuccessors(
        completedId: String,
        successorMap: Map<String, List<WorkflowNode>>,
        inDegree: MutableMap<String, Int>,
        readyQueue: ArrayDeque<WorkflowNode>
    ) {
        successorMap[completedId]?.forEach { successor ->
            val newDegree = (inDegree[successor.id] ?: 1) - 1
            inDegree[successor.id] = newDegree
            if (newDegree == 0) readyQueue.add(successor)
        }
    }
}
