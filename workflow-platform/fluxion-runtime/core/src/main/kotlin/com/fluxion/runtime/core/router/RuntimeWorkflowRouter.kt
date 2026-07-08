package com.fluxion.runtime.core.router

import com.fluxion.adapter.spi.UnifiedRequest
import com.fluxion.adapter.spi.UnifiedResponse
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.core.engine.DagExecutor
import com.fluxion.core.exception.WorkflowNotFoundException
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.value.EngineResult
import com.fluxion.runtime.core.provider.DefinitionProvider
import com.fluxion.runtime.core.spi.ExecutionSnapshotStore
import kotlinx.coroutines.runBlocking
import org.slf4j.*
/**
 * Runtime-side workflow router — worker (data-plane) entry point.
 *
 * Unlike the Admin control-plane router, this implementation has no direct DB
 * dependency. It resolves workflow definitions exclusively via
 * [DefinitionProvider] (config-center cache on Worker) and delegates actual
 * DAG execution to the shared [DagExecutor] from `fluxion-core`.
 *
 * Execution path contract:
 * - [executeSuspend] is the primary path — it suspends instead of blocking the
 *   calling Tomcat/WebFlux thread so concurrent throughput stays high.
 * - [execute] is a backwards-compatible blocking bridge used by adapters that
 *   don't yet speak coroutines (Dubbo, gRPC sync stubs, Kafka consumer loops).
 *
 * @param definitionProvider Source of truth for published workflow definitions
 * @param dagExecutor        Core DAG execution engine
 * @param snapshotStore      Optional persistence for execution results
 */
class RuntimeWorkflowRouter(
    private val definitionProvider: DefinitionProvider,
    private val dagExecutor: DagExecutor,
    private val snapshotStore: ExecutionSnapshotStore? = null
) : WorkflowRouter {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Blocking bridge for non-coroutine adapters (Dubbo/gRPC/Kafka consumer).
     * Bridges the call onto the coroutine dispatcher via [runBlocking].
     */
    override fun execute(request: UnifiedRequest): UnifiedResponse = runBlocking {
        executeSuspend(request)
    }

    /**
     * Primary coroutine-native routing path. Suspends instead of blocking
     * the calling thread, which is essential for WebFlux / virtual-thread pools.
     */
    override suspend fun executeSuspend(request: UnifiedRequest): UnifiedResponse {
        val definition = resolveDefinition(request)
            ?: throw WorkflowNotFoundException(
                request.workflowId ?: request.protocol
            )

        log.debug { "Runtime routing to workflow [${definition.id}] via protocol [${request.protocol}]" }

        val result = dagExecutor.execute(definition, request.params)

        try {
            snapshotStore?.save(result, definition.id, definition.version)
        } catch (ex: Exception) {
            log.warn(ex) { "Failed to save execution snapshot executionId=${result.executionId}" }
        }

        return UnifiedResponse.from(result).copy(
            outputSchema = definition.outputSchema,
            outputSchemaFormat = definition.outputSchemaFormat
        )
    }

    /**
     * Resolve the [WorkflowDefinition] targeted by the request.
     *
     * Resolution order (first match wins):
     * 1. Explicit `workflowId` on the request (direct Admin-call path).
     * 2. Protocol binding encoded in `request.protocol` as `"TYPE:bindKey"`
     *    (e.g. `"HTTP:GET:/api/users"`, `"KAFKA:order.created"`).
     *
     * @return Resolved definition, or null if no match exists
     */
    private fun resolveDefinition(request: UnifiedRequest): WorkflowDefinition? {
        if (!request.workflowId.isNullOrBlank()) {
            return definitionProvider.get(request.workflowId!!)
        }

        val protocol = request.protocol
        val parts = protocol.split(":", limit = 2)
        if (parts.size < 2) return null

        val protocolType = parts[0]
        val bindKey = parts[1]

        val workflowId = definitionProvider.findByBinding(protocolType, bindKey) ?: return null
        return definitionProvider.get(workflowId)
    }
}
