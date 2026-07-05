package com.fluxion.admin.mapper

import com.fluxion.admin.entity.WfExecutionSnapshot
import com.fluxion.admin.generated.model.DebugExecutionRecord
import com.fluxion.admin.generated.model.DebugResult
import com.fluxion.admin.generated.model.NodeTrace
import com.fluxion.core.value.EngineResult
import com.fluxion.core.value.NodeExecutionRecord
import com.fluxion.core.value.RerunResult
import org.springframework.stereotype.Component

/**
 * Debug / 执行快照相关映射器
 *
 * 当前采用手写 Spring Component：涉及 EngineResult / RerunResult 到 DTO 的复杂聚合转换，
 * 使用 setter 构建 Java POJO，转换逻辑仍集中在 Mapper 层。
 */
@Component
class DebugMapper(private val jsonMapperHelper: JsonMapperHelper) {

    fun toDebugResult(result: EngineResult): DebugResult = DebugResult().apply {
        status = jsonMapperHelper.booleanToDebugStatus(result.success)
        finalOutput = jsonMapperHelper.anyToStringMap(result.data)?.toMutableMap()
        traces = result.trace.map { toNodeTrace(it) }.toMutableList()
        totalDurationMs = jsonMapperHelper.traceToTotalDuration(result.trace)
    }

    fun toDebugResult(result: RerunResult): DebugResult = DebugResult().apply {
        status = jsonMapperHelper.traceToDebugStatus(result.trace)
        finalOutput = jsonMapperHelper.anyToStringMap(result.finalOutput)?.toMutableMap()
        traces = result.trace.map { toNodeTrace(it) }.toMutableList()
        totalDurationMs = jsonMapperHelper.traceToTotalDuration(result.trace)
    }

    fun toNodeTrace(record: NodeExecutionRecord): NodeTrace = NodeTrace().apply {
        nodeId = record.nodeId
        nodeName = record.nodeName
        status = jsonMapperHelper.nodeStatusToTraceStatus(record.status)
        // inputs 按 OpenAPI 契约以 nodeId 为 key 包装，与前端 trace.inputs[nodeId] 对齐
        val rawInput = jsonMapperHelper.anyToStringMap(record.input)
        inputs = if (rawInput != null) mutableMapOf(record.nodeId to rawInput) else mutableMapOf()
        output = jsonMapperHelper.anyToStringMap(record.output)?.toMutableMap() ?: mutableMapOf()
        error = jsonMapperHelper.nodeRecordToErrorMessage(record)
        durationMs = jsonMapperHelper.longToInt(record.durationMs)
    }

    fun toDebugExecutionRecord(snapshot: WfExecutionSnapshot): DebugExecutionRecord = DebugExecutionRecord().apply {
        id = snapshot.executionId
        workflowId = snapshot.workflowId
        timestamp = jsonMapperHelper.localDateTimeToTimestamp(snapshot.createdAt)
        status = jsonMapperHelper.booleanToDebugRecordStatus(snapshot.success)
        errorMessage = snapshot.errorMsg
    }
}
