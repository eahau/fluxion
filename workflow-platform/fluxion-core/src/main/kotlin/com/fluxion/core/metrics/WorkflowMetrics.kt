package com.fluxion.core.metrics

/**
 * 工作流 Metrics SPI — 零依赖接口
 *
 * workflow-core 只定义接口，不引入任何 Metrics 框架。
 * 具体实现由 workflow-decorator-impl 提供：
 *   - MicrometerWorkflowMetrics（Micrometer，接入 Prometheus/OTel）
 *   - noOp()            （默认，不上报，测试/轻量部署使用）
 */
interface WorkflowMetrics {

    /** 计数器递增 */
    fun increment(metricName: String, tags: Map<String, String>)

    /** 节点执行成功上报（计数 + 耗时） */
    fun recordNodeSuccess(nodeId: String, workflowId: String, durationMs: Long)

    /** 工作流整体完成上报（成功/失败 + 总耗时） */
    fun recordWorkflowComplete(workflowId: String, success: Boolean, durationMs: Long)

    /** 重试次数上报 */
    fun recordRetry(nodeId: String, workflowId: String)

    companion object {
        /** No-Op 实现：所有方法空操作，不引入任何外部依赖 */
        @JvmStatic
        fun noOp(): WorkflowMetrics = NoOpWorkflowMetrics
    }

    private object NoOpWorkflowMetrics : WorkflowMetrics {
        override fun increment(metricName: String, tags: Map<String, String>) {}
        override fun recordNodeSuccess(nodeId: String, workflowId: String, durationMs: Long) {}
        override fun recordWorkflowComplete(workflowId: String, success: Boolean, durationMs: Long) {}
        override fun recordRetry(nodeId: String, workflowId: String) {}
    }
}
