package com.fluxion.config.core

import com.fluxion.config.core.ChangeType
import com.fluxion.config.core.KeyedConfigChangeListener
import com.fluxion.config.core.KeyedConfigSubscriber
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap


abstract class AbstractKeyedConfigSubscriber<T : Any> :
    AbstractConfigSubscriber<KeyedConfigChangeListener<T>>(),
    KeyedConfigSubscriber<T> {

    private val lastValid = ConcurrentHashMap<String, T>()

    override fun watch(listener: KeyedConfigChangeListener<T>) {
        addListener(listener)
    }

    protected open fun validate(key: String, snapshot: T): Boolean = true

    protected fun resolveAndGet(key: String, rawContent: String): T? {
        val snapshot = try {
            mapKeyToSnapshot(key, rawContent)
        } catch (e: Exception) {
            log.warn { "Config parse failed for key=[$key]: ${e.message}" }
            return fallback(key, ConfigResolveResult.ParseFailed(e))
        }

        if (snapshot == null) {
            log.warn { "Config parse returned null for key=[$key]" }
            return fallback(key, ConfigResolveResult.ParseFailed(
                IllegalStateException("mapKeyToSnapshot returned null")))
        }

        if (!validate(key, snapshot)) {
            log.warn { "Config validation failed for key=[$key]" }
            return fallback(key, ConfigResolveResult.ValidationFailed("validate() returned false"))
        }

        trackSnapshot(key, snapshot)
        return snapshot
    }

    protected fun trackSnapshot(key: String, snapshot: T) {
        lastValid[key] = snapshot
    }

    protected fun evictSnapshot(key: String) {
        lastValid.remove(key)
    }

    protected fun notifyListeners(key: String, snapshot: T, type: ChangeType) {
        notifyListeners { it.onChange(key, snapshot, type) }
    }

    protected abstract fun mapKeyToSnapshot(key: String, content: String): T?

    private fun fallback(key: String, result: ConfigResolveResult<*>): T? {
        val last = lastValid[key]
        when (result) {
            is ConfigResolveResult.ParseFailed ->
                log.warn(result.error) {
                    "Degradation: key=[$key] parse failed, " +
                        "fallback=${if (last != null) "lastValid" else "null(skip)"}"
                }
            is ConfigResolveResult.ValidationFailed ->
                log.warn {
                    "Degradation: key=[$key] validation failed (${result.reason}), " +
                        "fallback=${if (last != null) "lastValid" else "null(skip)"}"
                }
            is ConfigResolveResult.Success -> {}
        }
        return last
    }
}
