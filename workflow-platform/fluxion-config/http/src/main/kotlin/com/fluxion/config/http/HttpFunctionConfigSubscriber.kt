/**
 * Worker-side function config subscriber that pulls [FunctionConfigSnapshot]
 * entries from the Admin server over HTTP and handles typed push deltas.
 *
 * HTTP mode is the config-centre free deployment path — Admin acts both as
 * the source of truth and the push bus. The class collaborates with
 * [FunctionPushController] on the Admin side which calls [onPushReceived]
 * whenever a function definition is published / updated / removed.
 */
package com.fluxion.config.http

import com.fluxion.core.util.JsonUtil
import com.fluxion.config.core.ChangeType
import com.fluxion.config.core.FunctionConfigSnapshot
import com.fluxion.config.core.AbstractKeyedConfigSubscriber
import org.slf4j.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Pulls function snapshots from the Admin internal API and accepts push
 * notifications delivered directly by the Admin controller.
 *
 * Bulk [loadAll] populates the degrade cache (via [trackSnapshot]) so the
 * engine can still function even if schema pushes are delayed. Push events
 * from [FunctionPushController] apply incremental updates without requiring
 * a full reload cycle.
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
                log.info { "Loaded ${snapshots.size} function configs from Admin via HTTP" }
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
            log.error(e) { "Failed to get function config for key=$key" }
            null
        }
    }

    /**
     * Push entry point invoked by [FunctionPushController] on the Admin side
     * whenever a function definition changes.
     */
    fun onPushReceived(snapshot: FunctionConfigSnapshot, changeType: ChangeType) {
        when (changeType) {
            ChangeType.REMOVE -> evictSnapshot(snapshot.functionName)
            else -> trackSnapshot(snapshot.functionName, snapshot)
        }
        notifyListeners(snapshot.functionName, snapshot, changeType)
    }

    // Typed HTTP mode never goes through raw key/content deserialization.
    override fun mapKeyToSnapshot(key: String, content: String): FunctionConfigSnapshot =
        throw UnsupportedOperationException("HTTP mode uses typed push/pull, not raw JSON parsing")
}
