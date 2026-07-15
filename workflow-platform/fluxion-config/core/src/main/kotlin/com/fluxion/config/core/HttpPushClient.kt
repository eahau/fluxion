/**
 * Fan-out HTTP push client used by the Admin to broadcast config changes
 * to every registered Worker instance.
 *
 * Payloads are always JSON strings (serialised by the caller); this client
 * focuses purely on the transport: target resolution, HTTP POST with a
 * configurable timeout, concurrent fan-out via `CompletableFuture`, and
 * aggregated success/failure counters returned to the caller.
 *
 * Instances are lightweight but hold an `HttpClient` and should be treated
 * as singletons; the Spring Boot auto-config module creates one bean per
 * push resource (schema/function/workflow) and injects it into the
 * matching `*ConfigPublisher`.
 */
package com.fluxion.config.core

import com.fluxion.discovery.core.InstanceDiscovery
import com.fluxion.registry.core.InstanceInfo
import com.fluxion.registry.core.PublishTarget
import com.fluxion.registry.core.TargetType
import org.slf4j.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * Concurrent fan-out HTTP push helper.
 *
 * @property log logger used for per-request warnings and errors
 * @property connectTimeoutSeconds TCP connect timeout applied to the underlying client
 * @property requestTimeoutSeconds per-request deadline
 */
class HttpPushClient(
    private val log: Logger,
    connectTimeoutSeconds: Long = 5,
    private val requestTimeoutSeconds: Long = 10
) {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
        .build()

    /**
     * Broadcasts `body` to every supplied instance concurrently and blocks
     * until all requests complete (success or failure).
     *
     * @param instances target worker instances
     * @param pushPath absolute path on the worker (e.g. `/internal/workflow/function/push`)
     * @param body JSON payload string
     * @param label human readable tag used for error attribution (functionName/workflowId)
     * @return pair of (successCount, failureCount)
     */
    fun push(
        instances: List<InstanceInfo>,
        pushPath: String,
        body: String,
        label: String
    ): Pair<Int, Int> {
        if (instances.isEmpty()) {
            log.warn { "No available instances for pushing [$label]" }
            return 0 to 0
        }

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        val futures = instances.map { inst ->
            sendAsync(inst, pushPath, body)
                .thenAccept { isSuccess ->
                    if (isSuccess) successCount.incrementAndGet() else failCount.incrementAndGet()
                }
                .exceptionally { ex ->
                    failCount.incrementAndGet()
                    log.error(ex) { "Push [$label] to ${inst.host}:${inst.port}$pushPath failed: ${ex.message}" }
                    null
                }
        }

        CompletableFuture.allOf(*futures.toTypedArray()).join()
        return successCount.get() to failCount.get()
    }

    /**
     * Fires a single async POST request; completes with `true` iff the
     * response status is in the 2xx range.
     */
    fun sendAsync(
        inst: InstanceInfo,
        pushPath: String,
        body: String
    ): CompletableFuture<Boolean> {
        val url = "http://${inst.host}:${inst.port}$pushPath"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(requestTimeoutSeconds))
            .build()

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { resp ->
                val ok = resp.statusCode() in 200..299
                if (!ok) {
                    log.warn { "Push to $url returned status ${resp.statusCode()}" }
                }
                ok
            }
    }

    companion object {
        /**
         * Resolves the list of target worker instances described by a
         * [PublishTarget] through the supplied [InstanceDiscovery].
         */
        fun resolveTargetInstances(
            target: PublishTarget,
            instanceDiscovery: InstanceDiscovery
        ): List<InstanceInfo> = when (target.type) {
            TargetType.ALL -> instanceDiscovery.getAllInstances()
            TargetType.APP_GROUP -> target.groups.flatMap { instanceDiscovery.getInstancesByGroup(it) }
            TargetType.INSTANCES -> {
                val all = instanceDiscovery.getAllInstances()
                all.filter { it.instanceId in target.instanceIds }
            }
        }
    }
}
