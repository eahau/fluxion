/**
 * Single-value Apollo config subscriber — wraps namespace subscription and
 * custom parsing logic into the generic [ConfigSubscriber] surface used by
 * the rest of the platform.
 *
 * This is a concrete (non-abstract) class that can be instantiated directly.
 * It pairs with [ApolloKeyedConfigSubscriber] which handles the multi-key
 * index-driven pattern; callers pick whichever variant matches their config
 * shape (monolithic single-namespace configs vs. per-key indexed configs).
 *
 * Typical registration inside a Spring auto-config:
 * ```kotlin
 * @Bean
 * fun myConfigSubscriber() =
 *     ApolloConfigSubscriber<MyConfig>(
 *         namespace = "my-config",
 *         parser = { cfg -> MyConfig(
 *             enabled = cfg.getProperty("enabled", "true").toBoolean(),
 *             maxSize = cfg.getProperty("maxSize", "100").toLong()) })
 * ```
 *
 * The design mirrors `ConfigProvider<T>` in Nacos backends but adds Apollo
 * namespace abstraction and clean Spring DI integration.
 */
package com.fluxion.config.apollo

import com.ctrip.framework.apollo.Config
import com.ctrip.framework.apollo.ConfigService
import com.fluxion.adapter.spi.config.ConfigChangeListener
import com.fluxion.adapter.spi.config.ConfigSubscriber
import com.fluxion.config.core.AbstractConfigSubscriber
import org.slf4j.LoggerFactory
import org.slf4j.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Apollo-based single-namespace [ConfigSubscriber] implementation.
 *
 * @param T parsed config type
 * @property namespace Apollo namespace name to watch and read from
 * @property parser user-supplied function converting an Apollo [Config]
 *   handle to the strongly typed `T` object; implementations typically
 *   chain multiple `getProperty(...)` calls with sensible defaults
 * @property defaultConfig fallback returned when `parser` throws during
 *   the initial load (useful when a namespace is missing in lower envs)
 */
open class ApolloConfigSubscriber<T>(
    private val namespace: String,
    private val parser: (Config) -> T,
    private val defaultConfig: T? = null
) : AbstractConfigSubscriber<ConfigChangeListener<T>>(),
    ConfigSubscriber<T> {

    private val config: Config by lazy { ConfigService.getConfig(namespace) }
    private val listenerAdded = AtomicBoolean(false)

    override fun load(): T = parseConfig()

    override fun watch(listener: ConfigChangeListener<T>) {
        addListener(listener)
        if (listenerAdded.compareAndSet(false, true)) {
            config.addChangeListener { notifyListeners { it.onChange(parseConfig()) } }
            log.info { "Registered Apollo config listener on namespace [$namespace]" }
        }
    }

    private fun parseConfig(): T = try {
        parser(config)
    } catch (e: Exception) {
        log.warn(e) { "Failed to parse config from Apollo namespace [$namespace]" }
        defaultConfig ?: throw e
    }
}
