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
 * Runtime 侧工作流路由器。
 *
 * 与控制面解耦，仅依赖 [DefinitionProvider] 获取工作流定义，
 * 通过 [DagExecutor] 执行，并可选择将结果持久化到 [ExecutionSnapshotStore]。
 *
 * 协程链路：
 *   [executeSuspend] 直接挂起调用 DagExecutor，不阻塞 Tomcat 线程。
 *   [execute] 为向后兼容的阻塞式桥接（供非协程调用方使用）。
 */
class RuntimeWorkflowRouter(
    private val definitionProvider: DefinitionProvider,
    private val dagExecutor: DagExecutor,
    private val snapshotStore: ExecutionSnapshotStore? = null
) : WorkflowRouter {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 阻塞式桥接（供 Dubbo/gRPC/Kafka 等非协程适配器使用） */
    override fun execute(request: UnifiedRequest): UnifiedResponse = runBlocking {
        executeSuspend(request)
    }

    /** 协程原生执行路径（不阻塞调用线程） */
    override suspend fun executeSuspend(request: UnifiedRequest): UnifiedResponse {
        val definition = resolveDefinition(request)
            ?: throw WorkflowNotFoundException(
                request.workflowId ?: request.protocol
            )

        log.debug { "Runtime routing to workflow [${definition.id}] via protocol [${request.protocol}]" }

        // 直接挂起执行，不阻塞 Tomcat 线程
        val result = dagExecutor.execute(definition, request.params)

        // 同步保存快照（失败不影响主流程）
        try {
            snapshotStore?.save(result, definition.id, definition.version)
        } catch (ex: Exception) {
            log.warn { "Failed to save execution snapshot executionId=${result.executionId}: ${ex.message}" }
        }

        return UnifiedResponse.from(result).copy(
            outputSchema = definition.outputSchema,
            outputSchemaFormat = definition.outputSchemaFormat
        )
    }

    private fun resolveDefinition(request: UnifiedRequest): WorkflowDefinition? {
        // 1. 直接按 workflowId 查找
        if (!request.workflowId.isNullOrBlank()) {
            return definitionProvider.get(request.workflowId!!)
        }

        // 2. 解析 protocol 字段路由
        // protocol 格式：PROTOCOL:bindKey（如 HTTP:GET:/api/users、KAFKA:workflow.user.create）
        val protocol = request.protocol
        val parts = protocol.split(":", limit = 2)
        if (parts.size < 2) return null

        val protocolType = parts[0]                  // HTTP / KAFKA / DUBBO / GRPC
        val bindKey = parts[1]                       // path / topic / serviceKey

        val workflowId = definitionProvider.findByBinding(protocolType, bindKey) ?: return null
        return definitionProvider.get(workflowId)
    }
}
