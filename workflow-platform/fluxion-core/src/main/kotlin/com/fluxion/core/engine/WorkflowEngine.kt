package com.fluxion.core.engine

import com.fluxion.core.decorator.DecoratorRegistry
import com.fluxion.core.enums.ErrorStrategy
import com.fluxion.core.enums.NodeStatus
import com.fluxion.core.exception.*
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.metrics.WorkflowMetrics
import com.fluxion.core.mock.MockConfig
import com.fluxion.core.mock.MockWorkflowFunction
import com.fluxion.core.model.*
import com.fluxion.core.retry.RetryScheduler
import com.fluxion.core.schema.SchemaValidator
import com.fluxion.core.value.*
import com.fluxion.schema.api.SchemaBackedMap
import com.fluxion.schema.api.SchemaDataProvider
import com.fluxion.schema.api.SchemaDataProviderRegistry
import com.fluxion.schema.model.SchemaFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import org.slf4j.*
import java.util.concurrent.*
import java.util.function.Supplier
import kotlinx.coroutines.withTimeout as coroutineWithTimeout

/**
 * 工作流引擎 — 基于不可变执行状态
 *
 * 纯 Kotlin，零框架依赖。所有依赖通过构造器注入，
 * 由 workflow-admin 的 @Configuration 负责组装。
 */
class WorkflowEngine(
    private val functionRegistry: FunctionRegistry,
    private val schemaValidator: SchemaValidator,
    private val decoratorRegistry: DecoratorRegistry,
    private val metrics: WorkflowMetrics,
    private val retryScheduler: RetryScheduler,
    private val devMode: Boolean,
    private val validateNodeInput: Boolean,
    private val validateNodeOutput: Boolean,
    private val taskInterceptor: TaskInterceptor = TaskInterceptor.NOOP,
    private val functionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val deadLetterQueue: DeadLetterQueue = DeadLetterQueue.NOOP,
    private val providerRegistry: SchemaDataProviderRegistry? = null
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** 超时控制执行器（虚拟线程，Java 21+） */
        private val TIMEOUT_EXECUTOR: ExecutorService = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("fluxion-timeout", 0).factory()
        )
    }

    // ─── Schema 校验 ──────────────────────────────────────────────

    /**
     * 校验工作流入参是否符合 inputSchema 约束。
     * 作为入口契约始终生效，不依赖 DAG 中是否存在 paramValidate 节点。
     *
     * 自动从 [WorkflowDefinition.inputSchemaFormat] 解析 Schema 格式，
     * 为 null 时回退到 JSON Schema（向后兼容）。
     */
    fun validateWorkflowInput(def: WorkflowDefinition, input: Any?) {
        val format = SchemaFormat.fromCodeOrNull(def.inputSchemaFormat)
        schemaValidator.validateStrict(def.inputSchema, input, format)
    }

    /**
     * 校验最终输出是否符合工作流级 outputSchema 约束。
     * 供 DagExecutor 等外部编排器在完成全部节点后调用。
     *
     * 自动从 [WorkflowDefinition.outputSchemaFormat] 解析 Schema 格式。
     */
    fun validateWorkflowOutput(def: WorkflowDefinition, output: Any?) {
        val format = SchemaFormat.fromCodeOrNull(def.outputSchemaFormat)
        schemaValidator.validateStrict(def.outputSchema, output, format)
    }

    // ─── 单节点执行（public：供 DebugService / DagExecutor 跨包调用）───

    /**
     * 执行单个节点并返回执行记录（suspend 函数）。
     *
     * 完整执行链：
     *   构建 NodeInput → 解析函数 → 应用装饰器链 → Mock 包装
     *   → Input Schema 校验 → 带超时执行 → Output Schema 校验
     *   → 记录指标 → 返回 NodeExecutionRecord
     *
     * 异常时委托 [handleNodeError] 按 errorStrategy 处理（SKIP / FALLBACK / RETRY / FAIL）。
     *
     * @param node         待执行的节点定义
     * @param directInput  上一节点的输出（或工作流入参）
     * @param state        当前不可变执行状态快照
     * @param mockConfig   调试 Mock 配置
     * @return 执行记录（含状态、输出、耗时）
     */
    suspend fun executeNode(
        node: WorkflowNode,
        directInput: Any?,
        state: ImmutableExecutionState,
        mockConfig: MockConfig = MockConfig.EMPTY,
        schemaFormat: String? = null
    ): NodeExecutionRecord {
        val startNano = System.nanoTime()
        return try {
            val nodeInput = buildNodeInput(node, directInput, state, schemaFormat)

            var function = functionRegistry.resolve(node.functionRef, node.type)

            // 应用用户配置的装饰器链
            node.decorators?.forEach { decoratorRef ->
                val decorator = decoratorRegistry.resolve(decoratorRef)
                function = decorator.decorate(function, node)
            }

            // 调试 Mock 包装：命中 Mock 时直接返回 Mock 响应，未命中委托真实函数
            if (mockConfig.enabled) {
                function = MockWorkflowFunction(function, { mockConfig }, node.functionRef)
            }

            // 函数级 Input Schema 校验
            val meta = function.meta()
            if (validateNodeInput && meta.inputSchema != null) {
                val iv = schemaValidator.validate(meta.inputSchema, nodeInput.directInput)
                if (!iv.valid) {
                    throw SchemaValidationException(
                        "Node [${node.name}] input schema mismatch: ${iv.errors}", iv.errors
                    )
                }
            }

            // 执行（含超时控制，协程挂起不阻塞线程）
            val finalFunction = function
            val result = withTimeout(node.timeoutMs) { finalFunction.apply(nodeInput) }

            // 函数级 Output Schema 校验
            if (validateNodeOutput && meta.outputSchema != null) {
                val ov = schemaValidator.validate(meta.outputSchema, result.output)
                if (!ov.valid) {
                    metrics.increment(
                        "workflow.node.schema.output.mismatch",
                        mapOf("node" to node.id, "workflow" to node.workflowId)
                    )
                    throw SchemaValidationException(
                        "Node [${node.name}] output schema mismatch: ${ov.errors}", ov.errors
                    )
                }
            }

            metrics.recordNodeSuccess(node.id, node.workflowId, elapsedMs(startNano))
            NodeExecutionRecord.successWithInput(node, result.output, nodeInput.directInput, elapsedMs(startNano))

        } catch (ex: Exception) {
            handleNodeError(node, directInput, state, ex, elapsedMs(startNano), schemaFormat)
        }
    }

    // ─── 构建 NodeInput ───────────────────────────────────────────

    /**
     * 组装节点输入（Schema 感知）。
     *
     * 按依赖声明从 state 中拉取上游节点输出填充 declaredDeps，
     * 用 SchemaBackedMap 包装以支持 Protobuf/Avro 等非 Map 格式。
     * 连同 directInput、workflowInput、nodeParams、executionMeta、
     * schemaFormat、providerRegistry 一起构造不可变 [NodeInput] 传给函数。
     */
    private fun buildNodeInput(
        node: WorkflowNode,
        directInput: Any?,
        state: ImmutableExecutionState,
        schemaFormat: String? = null
    ): NodeInput {
        val provider = resolveProvider(schemaFormat)
        val declaredDeps = linkedMapOf<String, Any>()
        node.dependsOn?.forEach { depNodeId ->
            val depOutput = state.getNodeOutput<Any>(depNodeId)
                ?: throw DependencyNotReadyException(node.id, depNodeId)
            declaredDeps[depNodeId] = SchemaBackedMap.wrap(depOutput, provider)
        }
        return NodeInput(
            directInput = directInput,
            declaredDeps = declaredDeps.toMap(),
            workflowInput = state.inputs,
            nodeParams = node.params ?: emptyMap(),
            meta = state.meta,
            schemaFormat = schemaFormat,
            providerRegistry = providerRegistry
        )
    }

    /**
     * 根据 schema 格式码解析对应的 [SchemaDataProvider]。
     */
    private fun resolveProvider(schemaFormat: String?): SchemaDataProvider? {
        val registry = providerRegistry ?: return null
        val format = SchemaFormat.fromCodeOrNull(schemaFormat) ?: return null
        return try {
            registry.getProvider(format)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    // ─── 错误处理 ─────────────────────────────────────────────────

    /**
     * 按节点的 errorStrategy 分发错误处理：
     * - SKIP：记录 warn 日志，返回 SKIPPED 记录
     * - FALLBACK：调用函数的 [WorkflowFunction.fallback] 降级
     * - RETRY：委托 [executeWithRetry] 指数退避重试
     * - FAIL（默认）：返回 FAILED 记录，由调用方决定是否抛异常
     */
    private fun handleNodeError(
        node: WorkflowNode,
        directInput: Any?,
        state: ImmutableExecutionState,
        ex: Exception,
        durationMs: Long,
        schemaFormat: String? = null
    ): NodeExecutionRecord {
        metrics.increment(
            "workflow.node.error",
            mapOf("node" to node.id, "error" to ex::class.java.simpleName)
        )

        return when (node.errorStrategy) {
            ErrorStrategy.SKIP -> {
                log.warn(ex) { "Node [${node.name}] skipped: ${ex.message}" }
                NodeExecutionRecord.skipped(node, durationMs)
            }
            ErrorStrategy.FALLBACK -> {
                try {
                    val fn = functionRegistry.resolve(node.functionRef, node.type)
                    val nodeInput = buildNodeInput(node, directInput, state, schemaFormat)
                    val fallbackResult = fn.fallback(nodeInput, ex)
                    NodeExecutionRecord.fallback(node, fallbackResult.output, durationMs)
                } catch (fallbackEx: Exception) {
                    enqueueDLQ(state, node.id, node.name, fallbackEx, DeadLetterEntry.FailureKind.NODE_FAILED)
                    NodeExecutionRecord.failed(node, fallbackEx, durationMs)
                }
            }
            ErrorStrategy.RETRY ->
                executeWithRetry(node, directInput, state, ex, durationMs, schemaFormat)
            else -> {
                enqueueDLQ(state, node.id, node.name, ex, DeadLetterEntry.FailureKind.NODE_FAILED)
                NodeExecutionRecord.failed(node, ex, durationMs)
            }
        }
    }

    /**
     * 指数退避重试入口。
     *
     * 从节点配置读取 retryCount（默认 3）和 retryBaseMs（默认 100ms），
     * 计算起始时间后委托 [scheduleRetryAttempt] 递归调度。
     * 使用 [CompletableFuture.join] 阻塞等待最终结果（重试路径非 suspend 上下文）。
     */
    private fun executeWithRetry(
        node: WorkflowNode,
        directInput: Any?,
        state: ImmutableExecutionState,
        firstEx: Exception,
        initialDurationMs: Long,
        schemaFormat: String? = null
    ): NodeExecutionRecord {
        val maxRetries = if (node.retryCount > 0) node.retryCount else 3
        val baseMs = if (node.retryBaseMs > 0) node.retryBaseMs else 100
        val startNano = System.nanoTime() - initialDurationMs * 1_000_000L

        return scheduleRetryAttempt(node, directInput, state, firstEx, startNano, 1, maxRetries, baseMs, schemaFormat).join()
    }

    /**
     * 递归调度单次重试尝试。
     *
     * 延迟 = min(baseMs * 2^(attempt-1), 30s)，通过 [RetryScheduler] 调度执行。
     * 成功 → thenApply 返回 SUCCESS 记录；
     * 失败 → exceptionallyCompose 递归调度下一轮（attempt+1）。
     */
    private fun scheduleRetryAttempt(
        node: WorkflowNode,
        directInput: Any?,
        state: ImmutableExecutionState,
        prevEx: Exception,
        startNano: Long,
        attempt: Int,
        maxRetries: Int,
        baseMs: Int,
        schemaFormat: String? = null
    ): CompletableFuture<NodeExecutionRecord> {
        if (attempt > maxRetries) {
            val totalMs = elapsedMs(startNano)
            log.warn { "Node [${node.name}] exhausted $maxRetries retries" }
            enqueueDLQ(state, node.id, node.name, prevEx, DeadLetterEntry.FailureKind.RETRY_EXHAUSTED)
            return CompletableFuture.completedFuture(NodeExecutionRecord.failed(node, prevEx, totalMs))
        }

        val delayMs = minOf((baseMs * Math.pow(2.0, (attempt - 1).toDouble())).toLong(), 30_000L)

        return retryScheduler.schedule({
            val nodeInput = buildNodeInput(node, directInput, state, schemaFormat)
            val fn = functionRegistry.resolve(node.functionRef, node.type)
            withTimeoutBlocking({ fn.apply(nodeInput) }, node.timeoutMs)
        }, delayMs)
            .thenApply { result ->
                val durationMs = elapsedMs(startNano)
                log.info { "Node [${node.name}] succeeded on retry $attempt/$maxRetries, totalMs=$durationMs" }
                NodeExecutionRecord.successWithInput(node, result.output, directInput, durationMs)
            }
            .exceptionallyCompose { throwable ->
                val retryEx = throwable as? Exception ?: RuntimeException(throwable)
                log.warn(retryEx) { "Node [${node.name}] retry $attempt/$maxRetries failed (delay=${delayMs}ms): ${retryEx.message}" }
                scheduleRetryAttempt(node, directInput, state, retryEx, startNano, attempt + 1, maxRetries, baseMs, schemaFormat)
            }
    }

    // ─── 超时控制 ─────────────────────────────────────────────────

    /**
     * 协程非阻塞超时控制。
     *
     * 挂起等待函数执行完成，不阻塞当前线程。
     * 超时后通过协程取消机制中断执行（虚拟线程上的阻塞 I/O 会收到 InterruptedException）。
     *
     * 仅供 suspend 上下文调用（executeNode 主路径）。
     */
    private suspend fun <T> withTimeout(timeoutMs: Int, block: () -> T): T {
        if (timeoutMs <= 0) {
            return withContext(taskInterceptor.wrap(functionDispatcher)) { block() }
        }
        try {
            return coroutineWithTimeout(timeoutMs.toLong()) {
                withContext(taskInterceptor.wrap(functionDispatcher)) { block() }
            }
        } catch (e: TimeoutCancellationException) {
            throw WorkflowNodeException.timeout("Execution timed out after ${timeoutMs}ms", e)
        }
    }

    /**
     * 阻塞式超时控制（重试路径专用）。
     *
     * 供 [scheduleRetryAttempt] 中 CompletableFuture 链使用，
     * 该路径非 suspend 上下文，无法使用协程挂起。
     */
    private fun <T> withTimeoutBlocking(supplier: Supplier<T>, timeoutMs: Int): T {
        if (timeoutMs <= 0) return supplier.get()
        val wrapped = taskInterceptor.wrap(TIMEOUT_EXECUTOR)
        val future = CompletableFuture<T>()
        wrapped.execute {
            try {
                future.complete(supplier.get())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        return try {
            future.get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw WorkflowNodeException.timeout("Execution timed out after ${timeoutMs}ms", e)
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is RuntimeException) throw cause
            throw WorkflowNodeException.timeout("Execution failed", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw WorkflowNodeException.timeout("Execution interrupted", e)
        }
    }

    // ─── 局部执行（重放专用）─────────────────────────────────────

    /**
     * 从指定节点开始局部重放执行（suspend 函数）。
     *
     * 利用 [ImmutableExecutionState] 的不可变特性，
     * 无需重新执行前置节点即可恢复到任意节点状态，
     * 然后从目标节点开始顺序执行到末尾。
     *
     * @param nodesToRerun  需要重放的节点子列表
     * @param directInput   重放起始输入（通常为前一节点输出）
     * @param replayState   恢复的执行状态快照
     * @param mockConfig    调试 Mock 配置
     * @return 重放结果（含最终输出、更新后的状态、执行轨迹）
     */
    suspend fun executePartial(
        nodesToRerun: List<WorkflowNode>,
        directInput: Any?,
        replayState: ImmutableExecutionState,
        mockConfig: MockConfig = MockConfig.EMPTY
    ): RerunResult {
        val nodeIndex = buildNodeIndex(nodesToRerun)
        val nodeListIndex = buildNodeListIndex(nodesToRerun)
        val trace = mutableListOf<NodeExecutionRecord>()
        var state = replayState
        var current: WorkflowNode? = nodesToRerun[0]
        var input = directInput

        while (current != null) {
            val record = executeNode(current, input, state, mockConfig)
            trace.add(record)
            if (record.status != NodeStatus.SKIPPED) {
                state = state.withNodeOutput(current.id, record.output)
                input = record.output
            }
            current = resolveNextNode(current, input, state, nodeIndex, nodeListIndex, nodesToRerun)
        }
        return RerunResult(input, state, trace)
    }

    // ─── 节点路由 ─────────────────────────────────────────────────

    /**
     * 解析下一执行节点（优先级：条件分支 > 显式 next > 列表顺序）。
     *
     * @return 下一节点，或 null（已到达末尾）
     */
    private fun resolveNextNode(
        current: WorkflowNode,
        output: Any?,
        state: ImmutableExecutionState,
        nodeIndex: Map<String, WorkflowNode>,
        nodeListIndex: Map<String, Int>,
        nodeList: List<WorkflowNode>
    ): WorkflowNode? {
        // 1. 条件分支优先（支持表达式模式或结构化规则模式）
        current.conditionalNexts?.forEach { cn ->
            val rawMatched = when {
                !cn.rules.isNullOrEmpty() -> RuleEvaluator.evalFilterRules(
                    rules = cn.rules,
                    defaultLogic = cn.logic,
                    nodeOutput = output,
                    globalLogicEnabled = cn.rulesGlobalLogicEnabled,
                    globalLogicOperator = cn.rulesGlobalLogicOperator,
                )
                else -> ExpressionEvaluator.evalBoolean(cn.condition, output, state.inputs)
            }
            val matched = if (cn.negate) !rawMatched else rawMatched
            if (matched) {
                return nodeIndex[cn.target]
            }
        }
        // 2. 显式 next
        current.next?.let { return nodeIndex[it] }
        // 3. 列表顺序
        val idx = nodeListIndex[current.id] ?: -1
        return if (idx in 0 until nodeList.size - 1) nodeList[idx + 1] else null
    }

    /** 构建 nodeId → WorkflowNode 索引，检测重复 ID。 */
    private fun buildNodeIndex(nodes: List<WorkflowNode>): Map<String, WorkflowNode> {
        val index = linkedMapOf<String, WorkflowNode>()
        for (node in nodes) {
            if (index.containsKey(node.id)) throw DuplicateNodeIdException(node.id)
            index[node.id] = node
        }
        return index
    }

    /** 构建 nodeId → 列表位置索引，用于按顺序查找下一节点。 */
    private fun buildNodeListIndex(nodes: List<WorkflowNode>): Map<String, Int> =
        nodes.withIndex().associate { (i, node) -> node.id to i }

    /** 将纳秒计时转换为毫秒。 */
    private fun elapsedMs(startNano: Long): Long = (System.nanoTime() - startNano) / 1_000_000L

    // ─── 死信队列辅助方法 ─────────────────────────────────────────

    /**
     * 将失败记录投递到死信队列。
     * DLQ 投递失败仅记录 warn 日志，不影响主流程。
     */
    private fun enqueueDLQ(
        state: ImmutableExecutionState,
        nodeId: String?,
        nodeName: String?,
        error: Throwable?,
        kind: DeadLetterEntry.FailureKind
    ) {
        try {
            deadLetterQueue.enqueue(
                DeadLetterEntry(
                    executionId = state.meta.executionId,
                    workflowId = state.meta.workflowId,
                    workflowName = state.meta.workflowName,
                    nodeId = nodeId,
                    nodeName = nodeName,
                    errorMessage = error?.message,
                    errorType = error?.let { it::class.java.simpleName },
                    failureKind = kind,
                    inputs = state.inputs,
                    meta = state.meta
                )
            )
        } catch (ex: Exception) {
            log.warn(ex) { "Failed to enqueue DLQ entry executionId=${state.meta.executionId} node=$nodeName" }
        }
    }
}
