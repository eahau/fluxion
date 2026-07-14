/**
 * Worker-side workflow-definition subscriber that pulls
 * [WorkflowDefinitionSnapshot] entries from the Admin server over HTTP and
 * accepts typed push deltas.
 *
 * Part of the zero-config-centre deployment mode. Admin pushes updates via
 * [DefinitionPushController] which calls [onPushReceived] with the published
 * workflow id, snapshot, and change type.
 */
package com.fluxion.config.http

import com.fluxion.core.util.JsonUtil
import com.fluxion.config.core.ChangeType
import com.fluxion.config.core.WorkflowDefinitionSnapshot
import com.fluxion.config.core.AbstractKeyedConfigSubscriber
import org.slf4j.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Pulls workflow definition snapshots from the Admin internal API on worker
 * startup and applies incremental push events forwarded by the Admin server.
 *
 * The degrade cache is updated explicitly here because HTTP transport works
 * with already-typd snapshots rather than raw config-centre key/value pairs.
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
                // Populate degrade cache up front for cold-start resilience.
                for (snap in snapshots) { trackSnapshot(snap.workflowId, snap) }
                log.info { "Loaded ${snapshots.size} workflow definitions from Admin via HTTP" }
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
            log.error(e) { "Failed to get definition for key=$key" }
            null
        }
    }

    /**
     * Push entry point called by [DefinitionPushController] on the Admin
     * server to deliver a single workflow definition delta.
     */
    fun onPushReceived(workflowId: String, snapshot: WorkflowDefinitionSnapshot, changeType: ChangeType) {
        when (changeType) {
            ChangeType.REMOVE -> evictSnapshot(workflowId)
            else -> trackSnapshot(workflowId, snapshot)
        }
        notifyListeners(workflowId, snapshot, changeType)
    }

    // Typed HTTP mode never goes through raw key/content deserialization.
    override fun mapKeyToSnapshot(key: String, content: String): WorkflowDefinitionSnapshot =
        throw UnsupportedOperationException("HTTP mode uses typed push/pull, not raw JSON parsing")
}
