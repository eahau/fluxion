package com.fluxion.config.http

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.SchemaConfigSnapshot
import com.fluxion.config.core.AbstractKeyedConfigSubscriber
import com.fluxion.core.util.JsonUtil
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * HTTP Schema 配置订阅器 — Worker 侧
 *
 * HTTP 模式下 Schema 由 Admin 主动推送（[onPushReceived]）或全量拉取（[loadAll]），
 * 不经过 `mapKeyToSnapshot` 原始 JSON 解析管线。
 * 降级追踪通过 [trackSnapshot] 在各入口显式调用。
 */
class HttpSchemaConfigSubscriber(
    adminBaseUrl: String
) : AbstractKeyedConfigSubscriber<SchemaConfigSnapshot>() {

    private val adminBaseUrl: String = adminBaseUrl.trimEnd('/')
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override fun loadAll(): List<SchemaConfigSnapshot> {
        val url = "$adminBaseUrl/internal/workflow/schema/all"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build()

            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() in 200..299) {
                val snapshots: List<SchemaConfigSnapshot> = JsonUtil.deserializeList(
                    resp.body(),
                    SchemaConfigSnapshot::class.java
                )
                for (snap in snapshots) { trackSnapshot(snap.schemaName, snap) }
                log.info("Loaded ${snapshots.size} schema configs from Admin via HTTP")
                snapshots
            } else {
                throw RuntimeException("Load all schemas failed: status=${resp.statusCode()}")
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Failed to load all schemas from Admin: $url", e)
        }
    }

    override fun get(key: String): SchemaConfigSnapshot? {
        val url = "$adminBaseUrl/internal/workflow/schema/$key"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build()

            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() == 200) {
                val snap = JsonUtil.deserialize(resp.body(), SchemaConfigSnapshot::class.java)
                trackSnapshot(key, snap)
                snap
            } else {
                null
            }
        } catch (e: Exception) {
            log.error("Failed to get schema config for key=$key", e)
            null
        }
    }

    /**
     * 由 SchemaPushController 调用，当 Admin 推送 Schema 配置到本实例时触发。
     *
     * REMOVE 事件清理降级缓存；PUBLISH/UPDATE 记录降级缓存。
     */
    fun onPushReceived(snapshot: SchemaConfigSnapshot, changeType: ChangeType) {
        when (changeType) {
            ChangeType.REMOVE -> evictSnapshot(snapshot.schemaName)
            else -> trackSnapshot(snapshot.schemaName, snapshot)
        }
        notifyListeners(snapshot.schemaName, snapshot, changeType)
    }

    /** HTTP 模式不经过原始 JSON 解析管线 */
    override fun mapKeyToSnapshot(key: String, content: String): SchemaConfigSnapshot =
        throw UnsupportedOperationException("HTTP mode uses typed push/pull, not raw JSON parsing")
}
