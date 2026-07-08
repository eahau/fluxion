package com.fluxion.core.model

/**
 * Mechanism by which a workflow is triggered.
 *
 * Used by the admin console and the runtime trigger router to classify
 * inbound requests and attach the correct payload adapter.
 */
enum class TriggerType {
    /** Human-initiated via the admin console Run button or equivalent. */
    MANUAL,
    /** Inbound HTTP POST delivered through a webhook endpoint. */
    WEBHOOK,
    /** Event-driven (message bus, CDC, notification system). */
    EVENT,
    /** Programmatic API call (HTTP, Dubbo, gRPC, INTERNAL). */
    API
}

/**
 * Trigger configuration attached to a [WorkflowDefinition].
 *
 * Each definition may expose multiple triggers (e.g. one API endpoint
 * and one event subscription). The runtime multiplexes inbound requests
 * by matching against `type` and [config].
 */
data class WorkflowTrigger(
    /** Stable trigger id (primary key in the admin console). */
    val id: String = "",
    /** Trigger classification — drives adapter selection. */
    val type: TriggerType = TriggerType.MANUAL,
    /**
     * Free-form adapter-specific configuration.
     *
     * Typical layouts:
     * - EVENT: `{ "eventType": "user.created", "source": "authing" }`
     * - WEBHOOK: `{ "path": "/hooks/my-workflow" }`
     * - API: `{ "path": "/api/workflows/{id}/execute" }`
     * - MANUAL: `null`
     */
    val config: Map<String, Any>? = null,
    /** Whether the trigger is currently wired up at runtime. */
    val enabled: Boolean = true
)
