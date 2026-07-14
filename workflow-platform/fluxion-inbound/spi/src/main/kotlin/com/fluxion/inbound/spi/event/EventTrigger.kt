package com.fluxion.inbound.spi.event

interface EventTrigger {
    fun subscribe(eventType: String, handler: (Map<String, Any>) -> Unit)
    fun unsubscribe(eventType: String)
    fun listSubscriptions(): List<String>
}

class EventTriggerRegistry {

    private val triggers = mutableMapOf<String, EventTrigger>()

    fun register(name: String, trigger: EventTrigger) {
        triggers[name] = trigger
    }

    fun get(name: String): EventTrigger? = triggers[name]

    fun listNames(): List<String> = triggers.keys.toList()
}
