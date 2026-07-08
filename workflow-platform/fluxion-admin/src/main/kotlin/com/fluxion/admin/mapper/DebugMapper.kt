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
 * Mapper layer for debug execution snapshots and engine result transformations.
 *
 * Converts between core engine artifacts (`EngineResult`, `RerunResult`, `NodeExecutionRecord`)
 * and OpenAPI-generated Java DTOs. Complex field coercion (JSON, enums, time) delegates to
 * `JsonMapperHelper` to keep this class focused on structural assembly.
 */
@Component
class DebugMapper(private val jsonMapperHelper: JsonMapperHelper) {

    /**
     * Convert a fresh engine execution result into the debug response DTO.
     */
    fun toDebugResult(result: EngineResult): DebugResult = DebugResult().apply {
        status = jsonMapperHelper.booleanToDebugStatus(result.success)
        finalOutput = jsonMapperHelper.anyToStringMap(result.data)?.toMutableMap()
        traces = result.trace.map { toNodeTrace(it) }.toMutableList()
        totalDurationMs = jsonMapperHelper.traceToTotalDuration(result.trace)
    }

    /**
     * Convert a rerun engine result into the debug response DTO.
     */
    fun toDebugResult(result: RerunResult): DebugResult = DebugResult().apply {
        status = jsonMapperHelper.traceToDebugStatus(result.trace)
        finalOutput = jsonMapperHelper.anyToStringMap(result.finalOutput)?.toMutableMap()
        traces = result.trace.map { toNodeTrace(it) }.toMutableList()
        totalDurationMs = jsonMapperHelper.traceToTotalDuration(result.trace)
    }

    /**
     * Convert a single node execution record into the per-node trace DTO.
     *
     * Note: inputs are re-keyed by `nodeId` to match the frontend contract where
     * `trace.inputs[nodeId]` holds the input payload for each node.
     */
    fun toNodeTrace(record: NodeExecutionRecord): NodeTrace = NodeTrace().apply {
        nodeId = record.nodeId
        nodeName = record.nodeName
        status = jsonMapperHelper.nodeStatusToTraceStatus(record.status)
        val rawInput = jsonMapperHelper.anyToStringMap(record.input)
        inputs = if (rawInput != null) mutableMapOf(record.nodeId to rawInput) else mutableMapOf()
        output = jsonMapperHelper.anyToStringMap(record.output)?.toMutableMap() ?: mutableMapOf()
        error = jsonMapperHelper.nodeRecordToErrorMessage(record)
        durationMs = jsonMapperHelper.longToInt(record.durationMs)
    }

    /**
     * Convert a persisted execution snapshot entity into the debug history list item DTO.
     */
    fun toDebugExecutionRecord(snapshot: WfExecutionSnapshot): DebugExecutionRecord = DebugExecutionRecord().apply {
        id = snapshot.executionId
        workflowId = snapshot.workflowId
        timestamp = jsonMapperHelper.localDateTimeToTimestamp(snapshot.createdAt)
        status = jsonMapperHelper.booleanToDebugRecordStatus(snapshot.success)
        errorMessage = snapshot.errorMsg
    }
}
