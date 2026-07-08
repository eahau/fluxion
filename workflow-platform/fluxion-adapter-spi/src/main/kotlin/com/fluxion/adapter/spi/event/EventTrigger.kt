package com.fluxion.adapter.spi.event

/**
 * Event-driven workflow trigger SPI — defines the contract for event-source
 * integration capabilities.
 *
 * Implement this interface to support different kinds of event sources:
 * - Internal events (workflow completion, node failure, platform lifecycle)
 * - External business events (third-party app events)
 * - HTTP Webhook callbacks (REST POST payloads from external systems)
 *
 * Lifecycle contract:
 * - Call [subscribe] when a workflow is published to start listening
 * - Call [unsubscribe] when a workflow is unpublished to stop listening
 */

/**
 * Core event trigger interface — subscribe / unsubscribe / list active subscriptions.
 */
interface EventTrigger {

    /**
     * Subscribe to a specific event type.
     *
     * When the event fires, [handler] is invoked with the event payload
     * represented as a String-keyed Map (JSON-decoded for external sources,
     * synthetic Map for internal sources).
     *
     * @param eventType Event type identifier (e.g. "user.created", "workflow.completed")
     * @param handler   Callback invoked with each event's data payload
     */
    fun subscribe(eventType: String, handler: (Map<String, Any>) -> Unit)

    /**
     * Unsubscribe from a previously-subscribed event type.
     *
     * No-op if the event type was never subscribed on this trigger.
     *
     * @param eventType Event type to stop listening to
     */
    fun unsubscribe(eventType: String)

    /**
     * List all currently-subscribed event types on this trigger.
     *
     * @return Snapshot of subscribed event type identifiers
     */
    fun listSubscriptions(): List<String>
}

/**
 * Central registry that manages multiple [EventTrigger] implementations
 * and routes lookups by trigger name.
 *
 * Workflow Engine or Admin-side code can retrieve a specific trigger by
 * name and use it to subscribe / unsubscribe on workflow publish.
 */
class EventTriggerRegistry {

    private val triggers = mutableMapOf<String, EventTrigger>()

    /**
     * Register a named event trigger implementation.
     *
     * Typical names: "kafka", "spring-event", "webhook".
     *
     * @param name    Unique trigger name
     * @param trigger Trigger implementation
     */
    fun register(name: String, trigger: EventTrigger) {
        triggers[name] = trigger
    }

    /**
     * Look up a trigger by name.
     *
     * @param name Trigger name used during [register]
     * @return The trigger, or null if not registered
     */
    fun get(name: String): EventTrigger? = triggers[name]

    /**
     * List the names of all currently-registered triggers.
     *
     * @return List of trigger names
     */
    fun listNames(): List<String> = triggers.keys.toList()
}
