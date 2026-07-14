package com.fluxion.core.engine

import com.fluxion.decorator.decorator.DecoratorRegistry
import com.fluxion.core.enums.ErrorStrategy
import com.fluxion.core.enums.NodeStatus
import com.fluxion.core.exception.*
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.FunctionResolver
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.metrics.WorkflowMetrics
import com.fluxion.core.model.*
import com.fluxion.core.retry.RetryScheduler
import com.fluxion.schema.json.SchemaValidator
import com.fluxion.core.value.*
import com.fluxion.decorator.engine.TaskInterceptor
import com.fluxion.schema.api.SchemaBackedMap
import com.fluxion.schema.api.SchemaDataProvider
import com.fluxion.schema.api.SchemaDataProviderRegistry
import com.fluxion.schema.api.SchemaManager
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
 * Core workflow engine: executes individual nodes and enforces the contracts
 * around decorators, schema validation, timeouts, retries and error
 * strategies.
 *
 * This class is intentionally *not* responsible for walking the DAG (see
 * [DagExecutor]) or the Saga compensation chain (see [SagaExecutor]); it
 * focuses on the per-node lifecycle and is wired up through
 * [FluxionCoreAutoConfiguration] in the Spring Boot starter.
 */
class WorkflowEngine(
    val functionRegistry: FunctionRegistry,
    private val schemaValidator: SchemaValidator,
    private val schemaManager: SchemaManager? = null,
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
        /**
         * Dedicated virtual-thread executor used by [withTimeoutBlocking] for
         * the retry path. Java 21+ (virtual threads) is required.
         */
        private val TIMEOUT_EXECUTOR: ExecutorService = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("fluxion-timeout", 0).factory()
        )
    }

    // ---- Schema validation (workflow-level) --------------------------------

    /**
     * Validate workflow-level input against [WorkflowDefinition.inputSchema].
     *
     * Always enforced at the start of DAG execution regardless of the
     * per-node `paramValidate` flag, because the input contract is owned by
     * the workflow author, not the individual nodes.
     *
     * The schema format is derived from [WorkflowDefinition.inputSchemaFormat];
     * `null` falls back to the built-in JSON Schema validator.
     */
    fun validateWorkflowInput(def: WorkflowDefinition, input: Any?) {
        val format = SchemaFormat.fromCodeOrNull(def.inputSchemaFormat)
        schemaValidator.validateStrict(def.inputSchema, input, format)
    }

    /**
     * Validate workflow-level output against [WorkflowDefinition.outputSchema].
     *
     * Invoked by [DagExecutor] after the last node completes. Enforces the
     * external contract that callers rely on and makes mismatches visible
     * early instead of silently propagating garbage downstream.
     */
    fun validateWorkflowOutput(def: WorkflowDefinition, output: Any?) {
        val format = SchemaFormat.fromCodeOrNull(def.outputSchemaFormat)
        schemaValidator.validateStrict(def.outputSchema, output, format)
    }

    // ---- Public node execution entry point (called by DagExecutor / DebugService) --

    /**
     * Execute a single workflow node and return its [NodeExecutionRecord].
     *
     * High-level flow:
     *  1. Build [NodeInput] aggregating direct input, declared dependencies,
     *     workflow input and node params.
     *  2. Resolve the function and wrap it with every configured decorator.
     *  3. Validate input against the function-declared schema ref (when enabled).
     *  4. Apply the per-node timeout and invoke the function.
     *  5. Validate output and record metrics, or delegate to [handleNodeError]
     *     which applies SKIP / FALLBACK / RETRY / FAIL per [ErrorStrategy].
     *
     * @param node node definition being executed
     * @param directInput input forwarded from the previous node (may be null
     *   when the node receives all its data via declared deps)
     * @param state immutable execution snapshot used to resolve deps and meta
     * @param functionRegistry resolver override (mostly for tests)
     * @param schemaFormat optional schema format hint forwarded to [NodeInput]
     * @return record capturing output (or error), status and wall-clock duration
     */
    suspend fun executeNode(
        node: WorkflowNode,
        directInput: Any?,
        state: ImmutableExecutionState,
        functionRegistry: FunctionResolver = this.functionRegistry,
        schemaFormat: String? = null
    ): NodeExecutionRecord {
        val startNano = System.nanoTime()
        return try {
            val nodeInput = buildNodeInput(node, directInput, state, schemaFormat)

            var function = functionRegistry.resolve(node.functionRef, node.type)

            // Wrap function with each configured decorator in declaration order.
            node.decorators?.forEach { decoratorRef ->
                val decorator = decoratorRegistry.resolve(decoratorRef)
                function = decorator.decorate(function, node)
            }

            // Input schema validation. Schema refs on FunctionMeta are resolved
            // through the optional SchemaManager; missing schemas log a warn
            // but skip validation (useful while schemas are being onboarded).
            val meta = this.functionRegistry.getMeta(node.functionRef) ?: InlineMeta(node.functionRef)
            val sm = schemaManager
            if (validateNodeInput && meta.inputSchemaRef.isNotBlank() && sm != null) {
                val schema = sm.resolveRef(meta.inputSchemaRef)
                if (schema != null) {
                    val iv = sm.validate(schema, nodeInput.directInput)
                    if (!iv.valid) {
                        throw SchemaValidationException(
                            "Node [${node.name}] input schema mismatch: ${iv.errors}", iv.errors
                        )
                    }
                } else {
                    log.warn { "Input schema ref [${meta.inputSchemaRef}] not resolved, skip validation" }
                }
            }

            // Execute the (decorated) function with per-node timeout enforced.
            val finalFunction = function
            val result = withTimeout(node.timeoutMs) { finalFunction.apply(nodeInput) }

            // Output schema validation (mirrors input validation above).
            if (validateNodeOutput && meta.outputSchemaRef.isNotBlank() && sm != null) {
                val schema = sm.resolveRef(meta.outputSchemaRef)
                if (schema != null) {
                    val ov = sm.validate(schema, result.output)
                    if (!ov.valid) {
                        metrics.increment(
                            "workflow.node.schema.output.mismatch",
                            mapOf("node" to node.id, "workflow" to node.workflowId)
                        )
                        throw SchemaValidationException(
                            "Node [${node.name}] output schema mismatch: ${ov.errors}", ov.errors
                        )
                    }
                } else {
                    log.warn { "Output schema ref [${meta.outputSchemaRef}] not resolved, skip validation" }
                }
            }

            metrics.recordNodeSuccess(node.id, node.workflowId, elapsedMs(startNano))
            NodeExecutionRecord.successWithInput(node, result.output, nodeInput.directInput, elapsedMs(startNano))

        } catch (ex: Exception) {
            handleNodeError(node, directInput, state, ex, elapsedMs(startNano), schemaFormat)
        }
    }

    // ---- NodeInput construction helpers ------------------------------------

    /**
     * Assemble the [NodeInput] structure passed into a function call.
     *
     * Declared dependencies are pulled from the shared immutable state and
     * wrapped via [SchemaBackedMap] when a format-aware provider is
     * available; this transparently turns raw Map inputs into typed
     * Protobuf/Avro accessors for functions that opt in.
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

    /** Resolve the optional [SchemaDataProvider] for a given schema format code. */
    private fun resolveProvider(schemaFormat: String?): SchemaDataProvider? {
        val registry = providerRegistry ?: return null
        val format = SchemaFormat.fromCodeOrNull(schemaFormat) ?: return null
        return try {
            registry.getProvider(format)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    // ---- Error handling / retry -------------------------------------------

    /**
     * Apply the node's [ErrorStrategy] after a failed function invocation.
     *  - SKIP: warn and return a SKIPPED record (output stays null)
     *  - FALLBACK: invoke [WorkflowFunction.fallback]; if *that* also fails
     *    enqueue the DLQ entry and fall back to FAILED
     *  - RETRY: delegate to [executeWithRetry] for exponential-backoff retries
     *  - else (FAIL): enqueue DLQ and return FAILED (which bubbles up to
     *    cancel the DAG through the error strategy)
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
     * Entry point for RETRY error strategy.
     *
     * Defaults to `retryCount=3` and `retryBaseMs=100ms` when the node does
     * not specify values, then hands off to [scheduleRetryAttempt] which
     * schedules retries through [RetryScheduler] and blocks on the
     * resulting [CompletableFuture.join] to bridge async retry scheduling
     * into the suspend call chain.
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
     * Schedule (or re-schedule) a single retry attempt with exponential backoff.
     *
     * Delay for attempt `n` is `min(baseMs * 2^(n-1), 30s)` and is scheduled
     * via [RetryScheduler]. On success the record is returned directly; on
     * failure [exceptionallyCompose] recursively schedules the next attempt
     * (or finally gives up after [maxRetries]).
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

    // ---- Timeouts ---------------------------------------------------------

    /**
     * Suspend-friendly timeout wrapper around a block.
     *
     * Delegates to Kotlin's `withTimeout` on the intercepted function
     * dispatcher so that Thread-local / MDC / tracing state set up by
     * [TaskInterceptor] is preserved. Throws a typed
     * [WorkflowNodeException.timeout] on expiry.
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
     * Blocking timeout wrapper used on the retry path.
     *
     * Retries are scheduled by [RetryScheduler] which returns
     * [CompletableFuture]s, so we cannot use the suspend-friendly helper
     * there. This variant runs the supplier on the shared virtual-thread
     * [TIMEOUT_EXECUTOR] and waits up to [timeoutMs] on [Future.get].
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

    // ---- Partial-replay support (used by DebugService) -------------------

    /**
     * Re-run a subset of nodes in order ("partial replay") for the debug UI.
     *
     * Walks the supplied [nodesToRerun] sequentially using the
     * [ImmutableExecutionState] snapshot as the baseline. Each node's
     * output becomes the `directInput` of the next node and the state is
     * updated after every non-skipped node, mirroring the normal linear
     * execution semantics of the original design.
     *
     * @return [RerunResult] carrying the last output, the updated state and
     *   the full ordered execution trace for UI display.
     */
    suspend fun executePartial(
        nodesToRerun: List<WorkflowNode>,
        directInput: Any?,
        replayState: ImmutableExecutionState,
        functionRegistry: FunctionResolver = this.functionRegistry
    ): RerunResult {
        val nodeIndex = buildNodeIndex(nodesToRerun)
        val nodeListIndex = buildNodeListIndex(nodesToRerun)
        val trace = mutableListOf<NodeExecutionRecord>()
        var state = replayState
        var current: WorkflowNode? = nodesToRerun[0]
        var input = directInput

        while (current != null) {
            val record = executeNode(current, input, state, functionRegistry)
            trace.add(record)
            if (record.status != NodeStatus.SKIPPED) {
                state = state.withNodeOutput(current.id, record.output)
                input = record.output
            }
            current = resolveNextNode(current, input, state, nodeIndex, nodeListIndex, nodesToRerun)
        }
        return RerunResult(input, state, trace)
    }

    // ---- Next-node resolution (used by partial replay) --------------------

    /**
     * Decide which node runs after [current] during partial replay.
     *
     * Priority: conditional-next rules (matched via [RuleEvaluator] or
     * expression evaluation) > explicit `next` pointer > sequential order
     * in `nodeList`. Returns `null` when the workflow should terminate.
     */
    private fun resolveNextNode(
        current: WorkflowNode,
        output: Any?,
        state: ImmutableExecutionState,
        nodeIndex: Map<String, WorkflowNode>,
        nodeListIndex: Map<String, Int>,
        nodeList: List<WorkflowNode>
    ): WorkflowNode? {
        // 1. Conditional edges first (evaluate rules or expressions).
        current.conditionalNexts?.forEach { cn ->
            val cnRules = cn.rules
            val rawMatched = when {
                !cnRules.isNullOrEmpty() -> RuleEvaluator.evalFilterRules(
                    rules = cnRules,
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
        // 2. Explicit next pointer.
        current.next?.let { return nodeIndex[it] }
        // 3. Fallback: linear position in the replayed node list.
        val idx = nodeListIndex[current.id] ?: -1
        return if (idx in 0 until nodeList.size - 1) nodeList[idx + 1] else null
    }

    /** Build `nodeId -> WorkflowNode` map; throws on duplicate IDs. */
    private fun buildNodeIndex(nodes: List<WorkflowNode>): Map<String, WorkflowNode> {
        val index = linkedMapOf<String, WorkflowNode>()
        for (node in nodes) {
            if (index.containsKey(node.id)) throw DuplicateNodeIdException(node.id)
            index[node.id] = node
        }
        return index
    }

    /** Build `nodeId -> list position` map for ordered fallback traversal. */
    private fun buildNodeListIndex(nodes: List<WorkflowNode>): Map<String, Int> =
        nodes.withIndex().associate { (i, node) -> node.id to i }

    /** Helper: nanoseconds -> milliseconds since a captured start time. */
    private fun elapsedMs(startNano: Long): Long = (System.nanoTime() - startNano) / 1_000_000L

    // ---- Dead-letter queue helpers ----------------------------------------

    /**
     * Best-effort enqueue of a failure entry into the configured DLQ.
     *
     * Any exception raised by the DLQ itself is swallowed and logged (never
     * propagated) so that operator-configured DLQ outages cannot mask the
     * original workflow failure.
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
