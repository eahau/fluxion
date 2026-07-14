package com.fluxion.decorator.tracing

import com.fluxion.decorator.decorator.NodeDecorator
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.value.FunctionResult
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import org.slf4j.*

class TracingDecorator : NodeDecorator {

    private val log = LoggerFactory.getLogger(javaClass)
    private val tracer: Tracer = GlobalOpenTelemetry.getTracer("fluxion-workflow")

    override fun name(): String = "tracing:opentelemetry"

    override fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any> =
        WorkflowFunction { input ->
            val spanName = "${node.workflowId}/${node.name}"
            val span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("workflow.id", node.workflowId)
                .setAttribute("workflow.name", input.meta?.workflowName ?: "")
                .setAttribute("node.id", node.id)
                .setAttribute("node.name", node.name)
                .setAttribute("node.functionRef", node.functionRef)
                .setAttribute("execution.id", input.meta?.executionId ?: "")
                .startSpan()

            var scope: Scope? = null
            try {
                scope = span.makeCurrent()
                val result = function.apply(input)
                span.setStatus(io.opentelemetry.api.trace.StatusCode.OK)
                result
            } catch (e: Exception) {
                span.recordException(e)
                span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.message ?: "")
                throw e
            } finally {
                scope?.close()
                span.end()
            }
        }
}