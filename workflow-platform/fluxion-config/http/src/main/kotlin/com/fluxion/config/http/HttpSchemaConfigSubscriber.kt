/**
 * Worker-side schema config subscriber that pulls snapshots from the Admin
 * server via HTTP endpoints and accepts push notifications.
 *
 * HTTP transport is the "zero-config-centre" mode: the Admin server publishes
 * changes through its own REST push endpoints rather than via Apollo/Nacos.
 * Unlike the config-centre subscribers, the HTTP path never deserialises raw
 * key/value strings into snapshots — it always works with pre-serialised typed
 * [SchemaConfigSnapshot] payloads, so [mapKeyToSnapshot] is not supported.
 */
package com.fluxion.config.http

import com.fluxion.config.core.ChangeType
import com.fluxion.config.core.SchemaConfigSnapshot
import com.fluxion.config.core.AbstractKeyedConfigSubscriber
import com.fluxion.core.util.JsonUtil
import org.slf4j.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Pulls schemas from the Admin server's internal HTTP API on the worker side.
 *
 * Two refresh mechanisms are combined:
 * 1. Bulk [loadAll] is called during startup to seed the local cache.
 * 2. Push events are delivered by the Admin server through
 *    [SchemaPushController] which calls [onPushReceived] with a typed delta.
 *
 * The degrade/resolution cache is updated explicitly at both entry points via
 * [trackSnapshot] / [evictSnapshot] because the class bypasses the standard
 * `mapKeyToSnapshot` pipeline.
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
                log.info { "Loaded ${snapshots.size} schema configs from Admin via HTTP" }
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
            log.error(e) { "Failed to get schema config for key=$key" }
            null
        }
    }

    /**
     * Entry point invoked by [SchemaPushController] when the Admin server
     * pushes a schema change event directly to this worker instance.
     *
     * REMOVE clears the degrade cache; PUBLISH/UPDATE records a fresh degrade
     * snapshot before notifying registered listeners.
     */
    fun onPushReceived(snapshot: SchemaConfigSnapshot, changeType: ChangeType) {
        when (changeType) {
            ChangeType.REMOVE -> evictSnapshot(snapshot.schemaName)
            else -> trackSnapshot(snapshot.schemaName, snapshot)
        }
        notifyListeners(snapshot.schemaName, snapshot, changeType)
    }

    // Typed HTTP mode never goes through raw key/content deserialization.
    override fun mapKeyToSnapshot(key: String, content: String): SchemaConfigSnapshot =
        throw UnsupportedOperationException("HTTP mode uses typed push/pull, not raw JSON parsing")
}
