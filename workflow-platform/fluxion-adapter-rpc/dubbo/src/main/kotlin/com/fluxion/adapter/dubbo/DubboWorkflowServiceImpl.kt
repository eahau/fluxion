package com.fluxion.adapter.dubbo

import com.fluxion.adapter.spi.UnifiedRequest
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.core.exception.WorkflowException
import org.apache.dubbo.rpc.RpcContext
import org.slf4j.*
/**
 * Apache Dubbo implementation of [DubboWorkflowApi] — zero Spring dependency.
 *
 * Converts Dubbo invocations (params map + attachments) into
 * [UnifiedRequest] objects and delegates to the protocol-agnostic
 * [WorkflowRouter].
 *
 * **Protocol marker on UnifiedRequest:**
 * - Direct execution via `_workflowId` → `protocol="INTERNAL"` (same marker
 *   used by HTTP when the caller sets `X-Workflow-Id` header).
 * - Bind-key routing via `x-service-key`/`_serviceKey` →
 *   `protocol="DUBBO:<serviceKey>"` so the runtime's route store can do a
 *   per-adapter lookup if a given service key has multiple transport bindings.
 *
 * **Error-surfacing strategy (deliberate design):**
 * `WorkflowException` (logical failure) and generic `Exception`
 * (infrastructure failure) are both caught and returned as a plain
 * `{success:false, errorCode, message}` map rather than being rethrown as a
 * Dubbo RpcException. This keeps the client-side deserialisation contract
 * stable across SDK languages — all consumers can branch on `$.success`
 * instead of language-specific exception-unwrapping.
 */
class DubboWorkflowServiceImpl(
    private val workflowRouter: WorkflowRouter
) : DubboWorkflowApi {

    companion object {
        /** Presence of this key in params forces direct execution by workflow id. */
        const val PARAM_WORKFLOW_ID = "_workflowId"
        /** Fallback compatibility routing key inside the payload. */
        const val PARAM_SERVICE_KEY = "_serviceKey"
        /** Preferred routing key carried in Dubbo per-RPC attachments. */
        const val ATTACHMENT_SERVICE_KEY = "x-service-key"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Single RPC entry point.
     *
     * Routing resolution order (highest → lowest):
     * 1. `params[PARAM_WORKFLOW_ID]` → direct
     * 2. `attachments[ATTACHMENT_SERVICE_KEY]` → bind-key (preferred transport-level)
     * 3. `params[PARAM_SERVICE_KEY]` → bind-key (payload fallback)
     *
     * The routing markers are *stripped* from `cleanParams` before passing to
     * the router so workflow authors see ONLY their own business keys in the
     * `params` map (no framework-prefixed surprises inside DAG functions).
     */
    override fun execute(params: Map<String, Any>): Any? {
        val attachments = extractDubboAttachments()
        val workflowId = params[PARAM_WORKFLOW_ID] as? String

        val serviceKey = attachments[ATTACHMENT_SERVICE_KEY]?.takeIf { it.isNotBlank() }
            ?: (params[PARAM_SERVICE_KEY] as? String)?.takeIf { it.isNotBlank() }

        val cleanParams = params
            .let { if (workflowId != null) it - PARAM_WORKFLOW_ID else it }
            .let { if (serviceKey != null && it.containsKey(PARAM_SERVICE_KEY)) it - PARAM_SERVICE_KEY else it }

        val request = when {
            workflowId != null -> UnifiedRequest.withSchemaFromHeaders("INTERNAL", workflowId, attachments, cleanParams)
            serviceKey != null -> UnifiedRequest.withSchemaFromHeaders("DUBBO:$serviceKey", null, attachments, cleanParams)
            else -> throw IllegalArgumentException(
                "Dubbo workflow routing requires either '_workflowId' in params " +
                    "or 'x-service-key' in Dubbo attachments / '_serviceKey' in params"
            )
        }

        val target = workflowId ?: serviceKey

        return try {
            workflowRouter.execute(request).data
        } catch (ex: WorkflowException) {
            log.warn { "Dubbo workflow error [${ex.errorCode}] for [$target]: ${ex.message}" }
            errorBody(ex.errorCode, ex.message)
        } catch (ex: Exception) {
            log.error(ex) { "Dubbo unexpected error for [$target]: ${ex.message}" }
            errorBody("WF-INTERNAL", ex.message)
        }
    }

    /** Build the canonical error-response envelope shared across all transport adapters. */
    private fun errorBody(errorCode: String, message: String?): Map<String, Any> =
        mapOf(
            "success"   to false,
            "errorCode" to errorCode,
            "message"   to (message ?: "Workflow execution failed")
        )

    /**
     * Extract Dubbo's implicit per-RPC attachments into a string map (used as
     * [UnifiedRequest.headers]).
     *
     * Includes propagated fields the caller may have set (trace-id, user-id,
     * tenant-id, x-service-key, x-workflow-id …) without requiring a schema
     * change on the Dubbo service interface. Swallows exceptions defensively
     * because Dubbo's RpcContext can throw in embedded/test scenarios.
     */
    private fun extractDubboAttachments(): Map<String, String> {
        return try {
            val context = RpcContext.getServiceContext()
            @Suppress("DEPRECATION")
            context.attachments?.mapValues { it.value ?: "" } ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
