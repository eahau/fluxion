/**
 * Shared JSON deserialisation helpers for config-centre snapshots.
 *
 * Each backend (Apollo, Nacos, internal HTTP push) ships a raw JSON text
 * payload; instead of duplicating `objectMapper.readValue` + try/catch in
 * every subscriber implementation, the platform centralises the logic here
 * so parsing error logs share a consistent tag vocabulary and failure
 * handling is uniform across backends.
 */
package com.fluxion.config.core

import com.fluxion.config.core.FunctionConfigSnapshot
import com.fluxion.core.util.JsonUtil
import org.slf4j.*

/**
 * Stateless parser converting raw config-centre JSON payloads into strongly
 * typed snapshot records.
 */
object SnapshotParser {

    /**
     * Parses a function config snapshot JSON payload.
     *
     * Deserialisation failures are logged at ERROR level with the offending
     * `functionName` tag and `null` is returned so callers can continue
     * processing other entries rather than failing the whole push batch.
     *
     * @param json raw JSON string from the config centre
     * @param functionName owning function key, used only for error tagging
     * @param log logger to report parse errors against
     * @return parsed snapshot or `null` on failure
     */
    fun parseFunctionSnapshot(
        json: String,
        functionName: String,
        log: Logger
    ): FunctionConfigSnapshot? {
        return try {
            JsonUtil.deserialize(json, FunctionConfigSnapshot::class.java)
        } catch (e: Exception) {
            log.error(e) { "Failed to parse function snapshot: functionName=$functionName" }
            null
        }
    }

    /**
     * Parses a Nacos-style function-name index set (JSON array of strings).
     *
     * Nacos backends list available function keys in a dedicated index entry
     * before pulling each individual snapshot; this helper returns the
     * empty set on parse failure so subscribers can fall back to full
     * enumeration rather than crashing.
     *
     * @param content JSON array text
     * @param log logger to report parse errors against
     * @return set of function keys or empty set on failure
     */
    fun parseFunctionIndex(
        content: String,
        log: Logger
    ): Set<String> {
        return try {
            JsonUtil.deserializeSet(content, String::class.java)
        } catch (e: Exception) {
            log.error(e) { "Failed to parse function index" }
            emptySet()
        }
    }
}
