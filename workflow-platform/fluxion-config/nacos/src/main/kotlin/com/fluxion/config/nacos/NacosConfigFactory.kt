/**
 * Factory for building Nacos-backed [KeyedConfigPublisher] and
 * [KeyedConfigSubscriber] instances with a consistent [group] prefix.
 *
 * The Nacos adapter ships both keyed (multi-key index-driven) and single-key
 * subscriber/publisher variants; callers normally construct one factory bean
 * in the auto-config layer and then use the typed helpers to bind publishers
 * and subscribers for each config resource (function, schema, workflow
 * definitions) instead of manually wiring data-IDs and groups repeatedly.
 *
 * Default group is `WORKFLOW` which matches the convention used by the
 * Apollo and HTTP push adapters so config migrations between backends are
 * transparent to application code.
 */
package com.fluxion.config.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.fluxion.adapter.spi.config.ConfigSubscriber
import com.fluxion.adapter.spi.config.KeyedConfigPublisher
import com.fluxion.adapter.spi.config.KeyedConfigSubscriber
import com.fluxion.core.util.JsonUtil

/**
 * Builds Nacos adapter instances sharing a common [configService] handle and
 * [group] namespace.
 *
 * @property configService Nacos SDK handle provided by the host application
 * @property group Nacos group prefix, defaults to `WORKFLOW`
 */
class NacosConfigFactory(
    private val configService: ConfigService,
    private val group: String = "WORKFLOW"
) {

    /**
     * Builds a keyed publisher that writes `{prefix}/{key}` entries plus
     * maintains an optional index data-id listing all known keys.
     *
     * @param dataIdPrefix data-id prefix that prepends the per-key suffix
     * @param indexDataId optional central index data-id (set to `null` if the
     *   caller manages key discovery out-of-band)
     * @param serializer serialiser from `T` → JSON string; defaults to Jackson
     */
    fun <T> keyedPublisher(
        dataIdPrefix: String,
        indexDataId: String? = null,
        serializer: (T) -> String = { JsonUtil.serialize(it) }
    ): KeyedConfigPublisher<T> =
        NacosKeyedConfigPublisher(configService, group, dataIdPrefix, indexDataId, serializer)

    /**
     * Builds a keyed subscriber that uses the index data-id to discover keys
     * and then pulls each individual entry through the user-supplied mapper.
     *
     * @param dataIdPrefix shared data-id prefix used by the publisher side
     * @param indexDataId central index listing every active key (required)
     * @param mapper per-key content mapper — receives the raw JSON string and
     *   returns `null` to signal a parse failure that should be logged and skipped
     */
    fun <T : Any> keyedSubscriber(
        dataIdPrefix: String,
        indexDataId: String,
        mapper: (key: String, content: String) -> T?
    ): KeyedConfigSubscriber<T> =
        NacosKeyedConfigSubscriber(configService, group, dataIdPrefix, indexDataId, mapper)

    /**
     * Builds a single-value (non-keyed) subscriber for static, single-entry
     * config such as rate-limit rules or shared feature flags.
     *
     * @param dataId Nacos data-id of the single config entry
     * @param mapper raw JSON content → typed object converter
     * @param default fallback value returned if the mapper throws on load
     */
    fun <T> subscriber(
        dataId: String,
        mapper: (content: String) -> T,
        default: T? = null
    ): ConfigSubscriber<T> =
        NacosConfigSubscriber(configService, group, dataId, mapper, default)
}
