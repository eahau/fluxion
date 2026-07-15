package com.fluxion.schema.api

import com.fluxion.schema.model.Schema
import org.slf4j.LoggerFactory
import org.slf4j.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ExternalSchemaRegistryPoller(
    private val registry: ExternalSchemaRegistry,
    private val intervalMs: Long = 30000,
    private val listener: SchemaRegistryListener? = null
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "schema-registry-poller").apply { isDaemon = true }
    }
    private val running = AtomicBoolean(false)
    private val lastKnownSchemas = mutableMapOf<String, String>()

    fun start() {
        if (running.compareAndSet(false, true)) {
            log.info { "Starting schema registry poller with interval=${intervalMs}ms" }
            executor.scheduleAtFixedRate({ poll() }, 0, intervalMs, TimeUnit.MILLISECONDS)
        }
    }

    fun stop() {
        if (running.compareAndSet(true, false)) {
            log.info { "Stopping schema registry poller" }
            executor.shutdown()
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun poll() {
        try {
            val currentSchemas = registry.list().associateBy({ it.name ?: "" }, { it.raw })

            for ((name, raw) in currentSchemas) {
                if (!lastKnownSchemas.containsKey(name)) {
                    val schema = registry.get(name)
                    schema?.let {
                        log.info { "Detected new schema: $name" }
                        listener?.onSchemaRegistered(name, it)
                    }
                } else if (lastKnownSchemas[name] != raw) {
                    val schema = registry.get(name)
                    schema?.let {
                        log.info { "Detected updated schema: $name" }
                        listener?.onSchemaUpdated(name, it)
                    }
                }
            }

            for (name in lastKnownSchemas.keys - currentSchemas.keys) {
                log.info { "Detected deleted schema: $name" }
                listener?.onSchemaDeleted(name)
            }

            lastKnownSchemas.clear()
            lastKnownSchemas.putAll(currentSchemas)
        } catch (e: Exception) {
            log.warn(e) { "Schema registry poll failed" }
        }
    }
}
