package com.fluxion.config.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.fluxion.adapter.spi.config.KeyedConfigPublisher
import com.fluxion.core.util.JsonUtil
import org.slf4j.*

/**
 * Nacos 通用多键配置发布器 — 封装 索引 + 独立 dataId 模式的公共发布逻辑
 *
 * 本类为具体类，可直接实例化使用。与 [NacosKeyedConfigSubscriber] 完全对称，
 * 新增 keyed 配置类型只需在 AutoConfiguration 中一行注册：
 *
 * ```kotlin
 * @Bean
 * fun myConfigPublisher(cs: ConfigService) =
 *     NacosKeyedConfigPublisher<MySnapshot>(
 *         configService = cs, group = "WORKFLOW",
 *         dataIdPrefix = "my.config.", indexDataId = "my.config.__index__")
 * ```
 *
 * @param T             配置快照类型
 * @param configService Nacos 配置服务
 * @param group         Nacos 分组名
 * @param dataIdPrefix  单个配置项的 dataId 前缀
 * @param indexDataId   索引 dataId；传 null 则不维护索引
 * @param serializer    快照 → JSON 字符串的序列化函数（默认使用 [JsonUtil]）
 */
open class NacosKeyedConfigPublisher<T>(
    private val configService: ConfigService,
    private val group: String,
    private val dataIdPrefix: String,
    private val indexDataId: String? = null,
    private val serializer: (T) -> String = { JsonUtil.serialize(it) }
) : KeyedConfigPublisher<T> {

    protected val log: Logger = LoggerFactory.getLogger(javaClass)

    /** 索引更新锁 — 防止并发 publish/unpublish 导致 read-modify-write 竞态 */
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
        log.info("Published config to Nacos: dataId=$dataId")
    }

    override fun unpublish(key: String) {
        val dataId = dataIdPrefix + key
        val success = try {
            configService.removeConfig(dataId, group)
        } catch (e: Exception) {
            throw RuntimeException("Failed to remove config from Nacos: dataId=$dataId", e)
        }
        if (success) {
            log.info("Removed config from Nacos: dataId=$dataId")
        } else {
            log.warn("Nacos removeConfig returned false (may not exist): dataId=$dataId")
        }
        updateIndex(key, add = false)
    }

    // ─── 索引管理 ──────────────────────────────────────────────────

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
                    log.info("Updated Nacos config index: key=$key add=$add")
                }
            } catch (e: Exception) {
                log.error("Failed to update Nacos config index for key=$key", e)
            }
        }
    }
}
