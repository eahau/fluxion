package com.fluxion.config.http

import com.fluxion.core.util.JsonUtil
import com.fluxion.registry.core.InstanceInfo
import com.fluxion.registry.core.InstanceRegistry
import org.slf4j.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class HttpInstanceRegistry(adminBaseUrl: String) : InstanceRegistry {

    private val log = LoggerFactory.getLogger(javaClass)
    private val adminBaseUrl: String = adminBaseUrl.trimEnd('/')
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    @Volatile
    private var registeredInstanceId: String? = null
    private val heartbeatIntervalMs = AtomicLong(10000)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        val t = Thread(it, "http-instance-registry-heartbeat")
        t.isDaemon = true
        t
    }
    private val heartbeatErrorCount = AtomicLong(0)

    override fun register(instance: InstanceInfo) {
        val url = "$adminBaseUrl/internal/workflow/instance/register"
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.serialize(instance)))
                .timeout(Duration.ofSeconds(10))
                .build()

            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() in 200..299) {
                registeredInstanceId = instance.instanceId
                log.info { "Registered instance ${instance.instanceId} (group=${instance.appGroup}, host=${instance.host}:${instance.port})" }
                startHeartbeat(instance.instanceId)
            } else {
                throw RuntimeException("Register failed: status=${resp.statusCode()}, body=${resp.body()}")
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to register instance ${instance.instanceId}" }
        }
    }

    override fun deregister(instanceId: String) {
        val url = "$adminBaseUrl/internal/workflow/instance/deregister/$instanceId"
        scheduler.shutdownNow()
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .DELETE()
                .timeout(Duration.ofSeconds(5))
                .build()

            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() in 200..299) {
                log.info { "Deregistered instance $instanceId" }
            } else {
                log.warn { "Deregister failed: status=${resp.statusCode()}" }
            }
        } catch (e: Exception) {
            log.warn(e) { "Failed to deregister instance $instanceId (best-effort)" }
        } finally {
            registeredInstanceId = null
        }
    }

    override fun heartbeat(instanceId: String) {
        val url = "$adminBaseUrl/internal/workflow/instance/heartbeat/$instanceId"
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build()

            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() in 200..299) {
                heartbeatErrorCount.set(0)
            } else {
                log.warn { "Heartbeat failed for $instanceId: status=${resp.statusCode()}" }
                heartbeatErrorCount.incrementAndGet()
            }
        } catch (e: Exception) {
            heartbeatErrorCount.incrementAndGet()
            if (heartbeatErrorCount.get() <= 3) {
                log.warn(e) { "Heartbeat failed for $instanceId (attempt ${heartbeatErrorCount.get()})" }
            }
        }
    }

    private fun startHeartbeat(instanceId: String) {
        scheduler.scheduleAtFixedRate(
            { heartbeat(instanceId) },
            heartbeatIntervalMs.get(),
            heartbeatIntervalMs.get(),
            TimeUnit.MILLISECONDS
        )
    }
}