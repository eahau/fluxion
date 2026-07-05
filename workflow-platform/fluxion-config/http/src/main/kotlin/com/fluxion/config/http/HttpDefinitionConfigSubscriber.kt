package com.fluxion.config.http

import com.fluxion.core.util.JsonUtil
import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.WorkflowDefinitionSnapshot
import com.fluxion.config.core.AbstractKeyedConfigSubscriber
import org.slf4j.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * HTTP 默认实现 — Worker 侧定义订阅器
 *
 * HTTP 模式下配置由 Admin 主动推送（[onPushReceived]）或全量拉取（[loadAll]），
 * 不经过 `mapKeyToSnapshot` 原始 JSON 解析管线。
 * 降级追踪通过 [trackSnapshot] 在各入口显式调用。
 */
class HttpDefinitionConfigSubscriber(
    adminBaseUrl: String
) : AbstractKeyedConfigSubscriber<WorkflowDefinitionSnapshot>() {

    private val adminBaseUrl: String = adminBaseUrl.trimEnd('/')
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override fun loadAll(): List<WorkflowDefinitionSnapshot> {
        val url = "$adminBaseUrl/internal/workflow/definition/all"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build()

            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() in 200..299) {
                val snapshots: List<WorkflowDefinitionSnapshot> = JsonUtil.deserializeList(
                    resp.body(),
                    WorkflowDefinitionSnapshot::class.java
                )
                // 记录降级缓存
                for (snap in snapshots) { trackSnapshot(snap.workflowId, snap) }
                log.info("Loaded ${snapshots.size} workflow definitions from Admin via HTTP")
                snapshots
            } else {
                throw RuntimeException("Load all definitions failed: status=${resp.statusCode()}")
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Failed to load all definitions from Admin: $url", e)
        }
    }

    override fun get(key: String): WorkflowDefinitionSnapshot? {
        val url = "$adminBaseUrl/internal/workflow/definition/$key"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build()

            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() == 200) {
                val snap = JsonUtil.deserialize(resp.body(), WorkflowDefinitionSnapshot::class.java)
                trackSnapshot(key, snap)
                snap
            } else {
                null
            }
        } catch (e: Exception) {
            log.error("Failed to get definition for key=$key", e)
            null
        }
    }

    /**
     * 由 DefinitionPushController 调用，当 Admin 推送定义到本实例时触发。
     *
     * REMOVE 事件清理降级缓存；PUBLISH/UPDATE 记录降级缓存。
     */
    fun onPushReceived(workflowId: String, snapshot: WorkflowDefinitionSnapshot, changeType: ChangeType) {
        when (changeType) {
            ChangeType.REMOVE -> evictSnapshot(workflowId)
            else -> trackSnapshot(workflowId, snapshot)
        }
        notifyListeners(workflowId, snapshot, changeType)
    }

    /** HTTP 模式不经过原始 JSON 解析管线 */
    override fun mapKeyToSnapshot(key: String, content: String): WorkflowDefinitionSnapshot =
        throw UnsupportedOperationException("HTTP mode uses typed push/pull, not raw JSON parsing")
}
