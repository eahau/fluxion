package com.fluxion.admin.controller

import com.fluxion.admin.security.DebugSecurityGuard
import com.fluxion.admin.service.WfDefinitionService
import com.fluxion.admin.service.WfExecutionSnapshotService
import com.fluxion.core.debug.DebugService
import com.fluxion.core.debug.DebugSnapshot
import com.fluxion.core.engine.ExecutionTracker
import com.fluxion.core.mock.MockConfig
import com.fluxion.core.mock.MockRule
import com.fluxion.core.signal.Signal
import com.fluxion.core.signal.SignalBroker
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import com.fluxion.core.engine.DagExecutor

/**
 * Debug / 重放 REST 控制器
 *
 * 接口设计（无服务端 Session）：
 *   POST /api/admin/debug/start           启动调试会话（执行工作流，返回 DebugSnapshot）
 *   POST /api/admin/debug/step            单步执行（body: DebugSnapshot → 返回新 DebugSnapshot）
 *   POST /api/admin/debug/continue        执行到下一个断点（body: DebugSnapshot）
 *   POST /api/admin/debug/mock            设置函数 Mock（body: MockRequest → 返回新 DebugSnapshot）
 *   POST /api/admin/executions/{id}/rerun 从指定节点重放（body: RerunRequest）
 *   GET  /api/admin/executions            列出执行快照（?workflowId=&page=&size=）
 *   GET  /api/admin/executions/{id}       查看执行快照详情
 */
@RestController
@RequestMapping("/api/admin")
class DebugController(
    private val debugService: DebugService,
    private val snapshotService: WfExecutionSnapshotService,
    private val definitionService: WfDefinitionService,
    private val securityGuard: DebugSecurityGuard,
    private val dagExecutor: DagExecutor,
    private val signalBroker: SignalBroker,
    private val executionTracker: ExecutionTracker
) {

    // ─── Debug Session ────────────────────────────────────────────

    /**
     * 启动调试会话：执行一次完整工作流，返回初始 DebugSnapshot
     * Body: { workflowId, params?, breakpoints? }
     */
    @PostMapping("/debug/start")
    suspend fun debugStart(
        @RequestBody req: DebugStartRequest,
        httpReq: HttpServletRequest
    ): ResponseEntity<ApiResponse<DebugSnapshot>> {
        securityGuard.checkAndAcquire(httpReq)
        return try {
            val def = definitionService.get(req.workflowId)
                ?: return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Workflow not found: ${req.workflowId}"))

            val result = dagExecutor.execute(def, req.params ?: emptyMap())

            // 持久化快照
            snapshotService.save(result, def.id, def.version)

            val snap = debugService.createSnapshot(
                result, def.id, def.version, req.breakpoints?.toSet()
            )
            ResponseEntity.ok(ApiResponse.ok(snap))
        } finally {
            securityGuard.release()
        }
    }

    /**
     * 单步执行：执行快照中的下一个节点
     * Body: DebugSnapshot（客户端保持状态）
     */
    @PostMapping("/debug/step")
    suspend fun debugStep(
        @RequestBody snap: DebugSnapshot,
        httpReq: HttpServletRequest
    ): ResponseEntity<ApiResponse<DebugSnapshot>> {
        securityGuard.checkAndAcquire(httpReq)
        return try {
            ResponseEntity.ok(ApiResponse.ok(debugService.step(snap)))
        } finally {
            securityGuard.release()
        }
    }

    /**
     * 继续执行到下一个断点（或工作流末尾）
     * Body: DebugSnapshot
     */
    @PostMapping("/debug/continue")
    suspend fun debugContinue(
        @RequestBody snap: DebugSnapshot,
        httpReq: HttpServletRequest
    ): ResponseEntity<ApiResponse<DebugSnapshot>> {
        securityGuard.checkAndAcquire(httpReq)
        return try {
            ResponseEntity.ok(ApiResponse.ok(debugService.continueToBreakpoint(snap)))
        } finally {
            securityGuard.release()
        }
    }

    /**
     * 设置/更新 Mock（在已有 DebugSnapshot 上注入 Mock 规则并返回新 Snapshot）
     * Body: { snapshot, rule }
     */
    @PostMapping("/debug/mock")
    fun debugMock(
        @RequestBody req: MockRequest,
        httpReq: HttpServletRequest
    ): ResponseEntity<ApiResponse<DebugSnapshot>> {
        securityGuard.checkAndAcquire(httpReq)
        return try {
            val snap = req.snapshot
            val def = definitionService.get(snap.workflowId)
                ?: return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Workflow not found: ${snap.workflowId}"))

            val ctx = DebugSnapshot.restore(snap, def)
            ctx.addMockRule(req.rule)
            val newSnap = ctx.toSnapshot(snap.workflowId, snap.workflowVersion, snap.pausedAtNodeId)
            ResponseEntity.ok(ApiResponse.ok(newSnap))
        } finally {
            securityGuard.release()
        }
    }

    // ─── Rerun ───────────────────────────────────────────────────

    /**
     * 从指定节点重放执行
     * Body: { nodeId, overrideInput? }
     */
    @PostMapping("/executions/{executionId}/rerun")
    suspend fun rerun(
        @PathVariable executionId: String,
        @RequestBody req: RerunRequest,
        httpReq: HttpServletRequest
    ): ResponseEntity<ApiResponse<RerunResultDto>> {
        securityGuard.checkAndAcquire(httpReq)
        return try {
            val result = debugService.rerunFromNode(executionId, req.nodeId, req.overrideInput)
            ResponseEntity.ok(ApiResponse.ok(
                RerunResultDto(
                    nodeCount  = result.trace.size,
                    success    = result.trace.none { it.status.name == "FAILED" },
                    traceIds   = result.trace.map { it.nodeId }
                )
            ))
        } finally {
            securityGuard.release()
        }
    }

    // ─── 执行记录查询 ─────────────────────────────────────────────

    // ─── Signal/Query ──────────────────────────────────────────

    /**
     * 发送信号到运行中的工作流执行
     * POST /api/admin/executions/{executionId}/signals
     * Body: { signalName, payload? }
     */
    @PostMapping("/executions/{executionId}/signals")
    fun sendSignal(
        @PathVariable executionId: String,
        @RequestBody req: SignalRequest
    ): ResponseEntity<ApiResponse<Any>> {
        val delivered = signalBroker.send(executionId, Signal(req.signalName, req.payload))
        return if (delivered) {
            ResponseEntity.ok(ApiResponse.ok(mapOf("status" to "delivered")))
        } else {
            ResponseEntity.ok(ApiResponse.error("Execution not found: $executionId"))
        }
    }

    /**
     * 查询运行中工作流的状态
     * GET /api/admin/executions/{executionId}/query
     */
    @GetMapping("/executions/{executionId}/query")
    fun queryExecution(
        @PathVariable executionId: String
    ): ResponseEntity<ApiResponse<Any>> {
        val running = executionTracker.getRunning(executionId)
        if (running == null) {
            // 已完成，查快照
            val snap = snapshotService.findByExecutionIdRaw(executionId)
                ?: return ResponseEntity.notFound().build()
            return ResponseEntity.ok(ApiResponse.ok(mapOf(
                "executionId" to executionId,
                "status" to if (snap.success) "COMPLETED" else "FAILED",
                "workflowId" to snap.workflowId,
                "workflowVersion" to snap.workflowVersion,
                "startTime" to snap.createdAt,
                "completedNodeIds" to (snap.nodeOutputsJson?.let {
                    try { com.fluxion.core.util.JsonUtil.toMap(it).keys.toList() } catch (_: Exception) { emptyList<String>() }
                } ?: emptyList<String>()),
                "inputs" to (snap.inputsJson?.let {
                    try { com.fluxion.core.util.JsonUtil.toMap(it) } catch (_: Exception) { emptyMap<String, Any>() }
                } ?: emptyMap<String, Any>())
            )))
        }
        return ResponseEntity.ok(ApiResponse.ok(mapOf(
            "executionId" to executionId,
            "status" to "RUNNING",
            "workflowId" to running.workflowId,
            "workflowName" to running.workflowName,
            "workflowVersion" to running.version,
            "startTime" to running.startTime,
            "inputs" to running.inputs,
            "nodeIds" to running.nodeIds,
            "waitingSignals" to signalBroker.waitingSignals(executionId),
            "bufferedSignals" to signalBroker.pendingSignals(executionId)
        )))
    }

    /**
     * 列出所有运行中的执行
     * GET /api/admin/executions/running
     */
    @GetMapping("/executions/running")
    fun listRunningExecutions(): ResponseEntity<ApiResponse<Any>> {
        val running = executionTracker.listRunning()
        return ResponseEntity.ok(ApiResponse.ok(running.map { mapOf(
            "executionId" to it.executionId,
            "workflowId" to it.workflowId,
            "workflowName" to it.workflowName,
            "version" to it.version,
            "startTime" to it.startTime,
            "waitingSignals" to signalBroker.waitingSignals(it.executionId)
        )}))
    }

    // ─── 执行记录查询（原有） ─────────────────────────────────────

    /**
     * 分页查询执行快照列表
     * ?workflowId=xxx&page=0&size=20
     */
    @GetMapping("/executions")
    fun listExecutions(
        @RequestParam(required = false) workflowId: String?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<Any>> {
        val pageable = PageRequest.of(page, size.coerceAtMost(100),
            Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = if (workflowId.isNullOrBlank()) {
            snapshotService.findAll(pageable)
        } else {
            snapshotService.findSnapshots(workflowId, pageable)
        }
        return ResponseEntity.ok(ApiResponse.ok(mapOf(
            "total"   to result.totalElements,
            "page"    to result.number,
            "records" to result.content.map { it.toSummaryDto() }
        )))
    }

    /**
     * 查看单次执行快照详情
     */
    @GetMapping("/executions/{executionId}")
    fun getExecution(
        @PathVariable executionId: String
    ): ResponseEntity<ApiResponse<Any>> {
        val snap = snapshotService.findByExecutionIdRaw(executionId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ApiResponse.ok(snap.toDetailDto()))
    }

    // ─── DTO ─────────────────────────────────────────────────────

    /** 启动调试会话请求体 */
    data class DebugStartRequest(
        val workflowId: String,
        val params: Map<String, Any>? = null,
        val breakpoints: List<String>? = null
    )

    /** 设置/更新 Mock 规则请求体 */
    data class MockRequest(
        val snapshot: DebugSnapshot,
        val rule: MockRule
    )

    /** 从指定节点重放请求体 */
    data class RerunRequest(
        val nodeId: String,
        val overrideInput: Any? = null
    )

    /** 重放执行结果 DTO */
    data class RerunResultDto(
        val nodeCount: Int,
        val success: Boolean,
        val traceIds: List<String>
    )

    /** 发送信号请求体 */
    data class SignalRequest(
        val signalName: String,
        val payload: Any? = null
    )

    /** 统一 API 响应包装（success + data/error） */
    data class ApiResponse<T>(
        val success: Boolean,
        val data: T? = null,
        val error: String? = null
    ) {
        companion object {
            fun <T> ok(data: T) = ApiResponse(true, data)
            fun <T> error(msg: String) = ApiResponse<T>(false, error = msg)
        }
    }

    // ─── Entity → DTO 扩展 ───────────────────────────────────────

    /** 快照实体 → 列表摘要 DTO（仅含关键字段） */
    private fun com.fluxion.admin.entity.WfExecutionSnapshot.toSummaryDto() = mapOf(
        "executionId"     to executionId,
        "workflowId"      to workflowId,
        "workflowVersion" to workflowVersion,
        "success"         to success,
        "errorMsg"        to errorMsg,
        "createdAt"       to createdAt
    )

    /** 快照实体 → 详情 DTO（含完整 JSON 字段） */
    private fun com.fluxion.admin.entity.WfExecutionSnapshot.toDetailDto() = mapOf(
        "executionId"     to executionId,
        "workflowId"      to workflowId,
        "workflowVersion" to workflowVersion,
        "success"         to success,
        "inputsJson"      to inputsJson,
        "nodeOutputsJson" to nodeOutputsJson,
        "traceJson"       to traceJson,
        "errorMsg"        to errorMsg,
        "createdAt"       to createdAt
    )
}
