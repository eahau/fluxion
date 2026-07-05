package com.fluxion.config.http

import com.fluxion.core.util.JsonUtil
import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.config.core.AbstractKeyedConfigSubscriber
import org.slf4j.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * HTTP 默认实现 — Worker 侧函数配置订阅器
 *
 * HTTP 模式下配置由 Admin 主动推送（[onPushReceived]）或全量拉取（[loadAll]），
 * 不经过 `mapKeyToSnapshot` 原始 JSON 解析管线。
 * 降级追踪通过 [trackSnapshot] 在各入口显式调用。
 */
class HttpFunctionConfigSubscriber(
    adminBaseUrl: String
) : AbstractKeyedConfigSubscriber<FunctionConfigSnapshot>() {

    private val adminBaseUrl: String = adminBaseUrl.trimEnd('/')
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override fun loadAll(): List<FunctionConfigSnapshot> {
        val url = "$adminBaseUrl/internal/workflow/function/all"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build()

            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() in 200..299) {
                val snapshots: List<FunctionConfigSnapshot> = JsonUtil.deserializeList(
                    resp.body(),
                    FunctionConfigSnapshot::class.java
                )
                for (snap in snapshots) { trackSnapshot(snap.functionName, snap) }
                log.info("Loaded ${snapshots.size} function configs from Admin via HTTP")
                snapshots
            } else {
                throw RuntimeException("Load all functions failed: status=${resp.statusCode()}")
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Failed to load all functions from Admin: $url", e)
        }
    }

    override fun get(key: String): FunctionConfigSnapshot? {
        val url = "$adminBaseUrl/internal/workflow/function/$key"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build()

            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() == 200) {
                val snap = JsonUtil.deserialize(resp.body(), FunctionConfigSnapshot::class.java)
                trackSnapshot(key, snap)
                snap
            } else {
                null
            }
        } catch (e: Exception) {
            log.error("Failed to get function config for key=$key", e)
            null
        }
    }

    /**
     * 由 FunctionPushController 调用，当 Admin 推送函数配置到本实例时触发。
     *
     * REMOVE 事件清理降级缓存；PUBLISH/UPDATE 记录降级缓存。
     */
    fun onPushReceived(snapshot: FunctionConfigSnapshot, changeType: ChangeType) {
        when (changeType) {
            ChangeType.REMOVE -> evictSnapshot(snapshot.functionName)
            else -> trackSnapshot(snapshot.functionName, snapshot)
        }
        notifyListeners(snapshot.functionName, snapshot, changeType)
    }

    /** HTTP 模式不经过原始 JSON 解析管线 */
    override fun mapKeyToSnapshot(key: String, content: String): FunctionConfigSnapshot =
        throw UnsupportedOperationException("HTTP mode uses typed push/pull, not raw JSON parsing")
}
