package com.fluxion.config.core

import com.fluxion.adapter.spi.registry.InstanceDiscovery
import com.fluxion.adapter.spi.registry.InstanceInfo
import com.fluxion.adapter.spi.registry.PublishTarget
import com.fluxion.adapter.spi.registry.TargetType
import org.slf4j.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * HTTP 推送客户端 — 把 JSON body 并发推送到一组 Worker 实例。
 *
 * 用于 HTTP 默认实现中 Admin → Worker 的配置分发。
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
     * 把 [body] 推送到 [instances] 列表中的每个实例。
     *
     * @param instances 目标实例
     * @param pushPath  推送路径（如 `/internal/workflow/function/push`）
     * @param body      JSON 字符串
     * @param label     日志标签（如 functionName / workflowId）
     * @return Pair(成功数, 失败数)
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
     * 异步发送一次推送请求；返回是否 2xx。
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
         * 根据 [PublishTarget] 从 [InstanceDiscovery] 解析目标实例列表。
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
