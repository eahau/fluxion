package com.fluxion.core.metrics

/**
 * Observability SPI for the Fluxion engine.
 *
 * Core calls the recorder methods at well-defined points (node completion,
 * workflow end, retry).  The module intentionally exposes *no* framework
 * dependency so implementations can plug any backend.
 *
 * Out-of-the-box variants:
 *  - [WorkflowMetrics.noOp] — default used when no metrics module is on
 *    the classpath; zero-allocation, does nothing.
 *  - `MicrometerWorkflowMetrics` (in `fluxion-decorator`) — bridges to
 *    Micrometer, surfacing counters and timers to Prometheus / OTel / etc.
 */
interface WorkflowMetrics {

    /**
     * Generic counter increment for ad-hoc metrics emitted by decorators
     * or extensions.  Tag map is pass-through; implementations normalise
     * to their backend's tag/cardinality model.
     */
    fun increment(metricName: String, tags: Map<String, String>)

    /** A node completed successfully with the given wall-clock duration. */
    fun recordNodeSuccess(nodeId: String, workflowId: String, durationMs: Long)

    /** A whole workflow finished; `success` distinguishes normal vs error termination. */
    fun recordWorkflowComplete(workflowId: String, success: Boolean, durationMs: Long)

    /** A retry has been scheduled for a failing node. */
    fun recordRetry(nodeId: String, workflowId: String)

    companion object {
        /**
         * Zero-allocation no-op recorder returned by the core auto-config
         * when no Micrometer-backed implementation is wired in.
         */
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
