package com.fluxion.config.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.fluxion.config.core.KeyedConfigPublisher
import com.fluxion.core.util.JsonUtil
import org.slf4j.*


open class NacosKeyedConfigPublisher<T>(
    private val configService: ConfigService,
    private val group: String,
    private val dataIdPrefix: String,
    private val indexDataId: String? = null,
    private val serializer: (T) -> String = { JsonUtil.serialize(it) }
) : KeyedConfigPublisher<T> {

    protected val log = LoggerFactory.getLogger(javaClass)

    private val indexLock = Any()

    override fun publish(key: String, snapshot: T) {
        val dataId = dataIdPrefix + key
        val json = try {
            serializer(snapshot)
        } catch (e: Exception) {
            throw RuntimeException("Failed to serialize config for key=$key", e)
        }

        val success = try {
            configService.publishConfig(dataId, group, json)
        } catch (e: Exception) {
            throw RuntimeException("Failed to publish config to Nacos: dataId=$dataId", e)
        }
        if (!success) {
            throw RuntimeException("Nacos publishConfig returned false for dataId=$dataId")
        }
        updateIndex(key, add = true)
        log.info { "Published config to Nacos: dataId=$dataId" }
    }

    override fun unpublish(key: String) {
        val dataId = dataIdPrefix + key
        val success = try {
            configService.removeConfig(dataId, group)
        } catch (e: Exception) {
            throw RuntimeException("Failed to remove config from Nacos: dataId=$dataId", e)
        }
        if (success) {
            log.info { "Removed config from Nacos: dataId=$dataId" }
        } else {
            log.warn { "Nacos removeConfig returned false (may not exist): dataId=$dataId" }
        }
        updateIndex(key, add = false)
    }

    private fun updateIndex(key: String, add: Boolean) {
        val idx = indexDataId ?: return
        synchronized(indexLock) {
            try {
                val current = configService.getConfig(idx, group, 5000)
                val index: MutableSet<String> = if (current.isNullOrBlank()) {
                    mutableSetOf()
                } else {
                    JsonUtil.deserializeSet(current, String::class.java).toMutableSet()
                }
                val changed = if (add) index.add(key) else index.remove(key)
                if (changed) {
                    configService.publishConfig(idx, group, JsonUtil.serialize(index))
                    log.info { "Updated Nacos config index: key=$key add=$add" }
                }
            } catch (e: Exception) {
                log.error(e) { "Failed to update Nacos config index for key=$key" }
            }
        }
    }
}
