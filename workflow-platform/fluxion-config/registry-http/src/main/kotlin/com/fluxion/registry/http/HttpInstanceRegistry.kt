package com.fluxion.registry.http

import com.fluxion.config.core.InstanceInfo
import com.fluxion.config.core.InstanceRegistry
import org.slf4j.LoggerFactory
import org.slf4j.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Worker-side HTTP implementation of [InstanceRegistry] that self-registers this
 * process with the Admin server and sends periodic heartbeats.
 *
 * The Admin HTTP endpoints live under the `/internal/workflow/registry/` prefix and are
 * served by the Admin internal registration controller. Paths below must stay
 * in sync with that controller.
 *
 * Registration is synchronous; heartbeat runs on a dedicated daemon-scheduled
 * thread (10 s interval) so it never blocks JVM exit. Explicit deregistration
 * on shutdown is best-effort — any failure is swallowed because heartbeat
 * records expire automatically on the Admin side after 30 s even without an
 * explicit DELETE.
 */
class HttpInstanceRegistry(
    adminBaseUrl: String
) : InstanceRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    private val adminBaseUrl: String = adminBaseUrl.trimEnd('/')
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "fluxion-http-registry-heartbeat").apply { isDaemon = true }
    }

    @Volatile
    private var registeredInstanceId: String? = null

    override fun register(instance: InstanceInfo) {
        val url = "$adminBaseUrl/internal/workflow/registry/register"
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(instance)))
                .timeout(Duration.ofSeconds(10))
                .build()
            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() !in 200..299) {
                log.warn { "Instance registration failed: status=${resp.statusCode()}, body=${resp.body()}" }
                return
            }
            registeredInstanceId = instance.instanceId
            log.info { "Registered instance ${instance.instanceId} (group=${instance.appGroup}, host=${instance.host}:${instance.port})" }
            startHeartbeat()
        } catch (e: Exception) {
            log.error(e) { "Failed to register instance ${instance.instanceId} with Admin server" }
        }
    }

    override fun deregister(instanceId: String) {
        val url = "$adminBaseUrl/internal/workflow/registry/instances/$instanceId"
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .DELETE()
                .timeout(Duration.ofSeconds(5))
                .build()
            val resp = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            if (resp.statusCode() in 200..299) {
                log.info { "Deregistered instance $instanceId" }
            } else {
                log.warn { "Instance deregistration returned status=${resp.statusCode()}, will rely on heartbeat expiry" }
            }
        } catch (e: Exception) {
            log.warn(e) { "Best-effort deregister failed for instance $instanceId (will expire via heartbeat)" }
        } finally {
            registeredInstanceId = null
        }
    }

    override fun heartbeat(instanceId: String) {
        val url = "$adminBaseUrl/internal/workflow/registry/heartbeat/$instanceId"
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(3))
                .build()
            val resp = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            if (resp.statusCode() == 404) {
                log.warn { "Heartbeat returned 404 for $instanceId — re-registration may be required" }
            }
        } catch (e: Exception) {
            log.debug(e) { "Heartbeat failed transiently for $instanceId (next attempt in 10s)" }
        }
    }

    /**
     * Schedule the fixed-rate heartbeat loop. The loop uses the stored
     * [registeredInstanceId]; runs on a daemon thread so it never blocks the
     * JVM from exiting cleanly.
     */
    private fun startHeartbeat() {
        scheduler.scheduleAtFixedRate({
            registeredInstanceId?.let { heartbeat(it) }
        }, 10, 10, TimeUnit.SECONDS)
    }

    /**
     * Render [InstanceInfo] to JSON manually — avoids pulling Jackson into the
     * tiny registry-http module which intentionally keeps a minimal dependency
     * footprint. Metadata is intentionally serialised as an empty object since
     * the Admin server only uses it for display.
     */
    private fun toJson(instance: InstanceInfo): String =
        """{"instanceId":"${instance.instanceId}","appGroup":"${instance.appGroup}","host":"${instance.host}","port":${instance.port},"metadata":{},"lastHeartbeatMs":${instance.lastHeartbeatMs}}"""
}
