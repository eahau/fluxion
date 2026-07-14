package com.fluxion.decorator.metrics

import com.fluxion.core.metrics.WorkflowMetrics
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit

/**
 * Micrometer-backed implementation of [WorkflowMetrics].
 *
 * Emits the standard set of counters + timers documented on the interface;
 * unknown / null tag values are normalised to the literal `"unknown"` so
 * dashboards never see missing tag cardinalities.
 */
class MicrometerWorkflowMetrics(private val meterRegistry: MeterRegistry) : WorkflowMetrics {

    override fun increment(metricName: String, tags: Map<String, String>) {
        meterRegistry.counter(metricName, toTags(tags)).increment()
    }

    override fun recordNodeSuccess(nodeId: String, workflowId: String, durationMs: Long) {
        val tags = Tags.of("workflow", safe(workflowId), "node", safe(nodeId))
        meterRegistry.counter("workflow.node.success", tags).increment()
        Timer.builder("workflow.node.duration").tags(tags).register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS)
    }

    override fun recordWorkflowComplete(workflowId: String, success: Boolean, durationMs: Long) {
        val tags = Tags.of("workflow", safe(workflowId), "status", if (success) "SUCCESS" else "FAILED")
        meterRegistry.counter("workflow.executions.total", tags).increment()
        Timer.builder("workflow.executions.duration")
            .tags(Tags.of("workflow", safe(workflowId)))
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS)
    }

    override fun recordRetry(nodeId: String, workflowId: String) {
        val tags = Tags.of("workflow", safe(workflowId), "node", safe(nodeId))
        meterRegistry.counter("workflow.retry.total", tags).increment()
    }

    private fun toTags(tagMap: Map<String, String>): Tags {
        val pairs = tagMap.flatMap { (k, v) -> listOf(k, safe(v)) }.toTypedArray()
        return Tags.of(*pairs)
    }

    private fun safe(value: String?): String = value ?: "unknown"
}
