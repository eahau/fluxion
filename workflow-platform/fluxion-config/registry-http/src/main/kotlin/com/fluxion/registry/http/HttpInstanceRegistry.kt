package com.fluxion.registry.http

import com.fluxion.adapter.spi.registry.InstanceInfo
import com.fluxion.adapter.spi.registry.InstanceRegistry
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * HTTP 默认实现 — Worker 侧实例注册客户端
 */
class HttpInstanceRegistry(
    adminBaseUrl: String
) : InstanceRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    private val adminBaseUrl: String = adminBaseUrl.trimEnd('/')
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "http-registry-heartbeat").apply { isDaemon = true }
    }

    @Volatile
    private var registeredInstanceId: String? = null

    override fun register(instance: InstanceInfo) {
        val url = "$adminBaseUrl/internal/workflow/registry/register"
        try {
            val body = toJson(instance)
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build()

            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() in 200..299) {
                registeredInstanceId = instance.instanceId
                log.info("Registered to Admin: instanceId=${instance.instanceId} appGroup=${instance.appGroup}")
                startHeartbeat()
            } else {
                log.error("Register failed: status=${resp.statusCode()} body=${resp.body()}")
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to register instance to Admin: $url", e)
        }
    }

    override fun deregister(instanceId: String) {
        scheduler.shutdown()
        val url = "$adminBaseUrl/internal/workflow/registry/deregister?instanceId=$instanceId"
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .DELETE()
                .timeout(Duration.ofSeconds(5))
                .build()
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            log.info("Deregistered from Admin: instanceId=$instanceId")
        } catch (e: Exception) {
            log.warn("Deregister failed (best-effort): ${e.message}", e)
        }
    }

    override fun heartbeat(instanceId: String) {
        val url = "$adminBaseUrl/internal/workflow/registry/heartbeat?instanceId=$instanceId"
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(5))
                .build()
            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() >= 300) {
                log.warn("Heartbeat returned status ${resp.statusCode()}")
            }
        } catch (e: Exception) {
            log.warn("Heartbeat failed: ${e.message}", e)
        }
    }

    private fun startHeartbeat() {
        scheduler.scheduleAtFixedRate({
            registeredInstanceId?.let { heartbeat(it) }
        }, 10, 10, TimeUnit.SECONDS)
    }

    private fun toJson(instance: InstanceInfo): String =
        """{"instanceId":"${instance.instanceId}","appGroup":"${instance.appGroup}","host":"${instance.host}","port":${instance.port},"metadata":{},"lastHeartbeatMs":${instance.lastHeartbeatMs}}"""
}
