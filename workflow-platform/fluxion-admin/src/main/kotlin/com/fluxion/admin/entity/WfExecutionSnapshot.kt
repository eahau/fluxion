package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 工作流执行快照实体。
 *
 * 源表：`wf_execution_snapshot`
 */
@Entity
@Table(name = "wf_execution_snapshot")
class WfExecutionSnapshot : BaseEntity() {

    /** 执行唯一 ID（列：`execution_id`） */
    @Column(name = "execution_id", nullable = false, unique = true, length = 64)
    var executionId: String = ""

    /** 工作流 ID（列：`workflow_id`） */
    @Column(name = "workflow_id", nullable = false, length = 64)
    var workflowId: String = ""

    /** 执行时的工作流版本（列：`workflow_version`） */
    @Column(name = "workflow_version", nullable = false)
    var workflowVersion: Int = 1

    /** 执行是否成功（列：`success`） */
    @Column(name = "success", nullable = false)
    var success: Boolean = true

    /** 工作流原始入参（Map<String,Object> JSON）（列：`inputs_json`） */
    @Column(name = "inputs_json", columnDefinition = "MEDIUMTEXT")
    var inputsJson: String? = null

    /** 各节点输出快照（Map<nodeId,output> JSON）（列：`node_outputs_json`） */
    @Column(name = "node_outputs_json", columnDefinition = "MEDIUMTEXT")
    var nodeOutputsJson: String? = null

    /** 节点执行轨迹（List<NodeExecutionRecord> JSON）（列：`trace_json`） */
    @Column(name = "trace_json", columnDefinition = "MEDIUMTEXT")
    var traceJson: String? = null

    /** 错误信息（列：`error_msg`） */
    @Column(name = "error_msg", length = 512)
    var errorMsg: String? = null
}
