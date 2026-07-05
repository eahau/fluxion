package com.fluxion.core.value

/**
 * 执行元信息 — 类比 CPU 的 Thread-Local Storage
 * 绑定到单次工作流执行，贯穿所有节点（只读）
 */
data class ExecutionMeta(
    /** 工作流定义 ID */
    val workflowId: String,
    /** 工作流名称（日志/Metrics 用） */
    val workflowName: String,
    /** 当前执行的工作流版本号 */
    val version: Int,
    /** 所属应用分组（对应 WorkflowDefinition.appGroup） */
    val appGroup: String? = null,
    /** 本次执行唯一 ID（UUID） */
    val executionId: String,
    /** 执行开始时间（毫秒时间戳） */
    val startTime: Long
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        var workflowId: String = ""
        var workflowName: String = ""
        var version: Int = 0
        var appGroup: String? = null
        var executionId: String = ""
        var startTime: Long = 0L

        fun workflowId(v: String) = apply { workflowId = v }
        fun workflowName(v: String) = apply { workflowName = v }
        fun version(v: Int) = apply { version = v }
        fun appGroup(v: String?) = apply { appGroup = v }
        fun executionId(v: String) = apply { executionId = v }
        fun startTime(v: Long) = apply { startTime = v }

        fun build() = ExecutionMeta(
            workflowId = workflowId,
            workflowName = workflowName,
            version = version,
            appGroup = appGroup,
            executionId = executionId,
            startTime = startTime
        )
    }
}
