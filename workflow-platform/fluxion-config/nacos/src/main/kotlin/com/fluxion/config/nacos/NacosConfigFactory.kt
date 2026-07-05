package com.fluxion.config.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.fluxion.adapter.spi.config.ConfigSubscriber
import com.fluxion.adapter.spi.config.KeyedConfigPublisher
import com.fluxion.adapter.spi.config.KeyedConfigSubscriber
import com.fluxion.core.util.JsonUtil

/**
 * Nacos 配置工厂 — 封装 [ConfigService] + [group] 公共依赖，消除重复参数传递
 *
 * 对标 `NacosConfigs.java` 中 `ConfigProvider` 的声明式注册体验：
 * AutoConfiguration 只需注册一次本工厂，后续新增配置类型仅传业务参数。
 *
 * ```kotlin
 * // AutoConfiguration 中注册工厂（一次性）
 * @Bean fun nacosConfigFactory(cs: ConfigService) = NacosConfigFactory(cs)
 *
 * // 新增 keyed 配置（不再重复 ConfigService / group）
 * @Bean fun myPublisher(f: NacosConfigFactory) =
 *     f.keyedPublisher<MySnapshot>("my.config.", "my.config.__index__")
 *
 * @Bean fun mySubscriber(f: NacosConfigFactory) =
 *     f.keyedSubscriber<MySnapshot>("my.config.", "my.config.__index__",
 *         mapper = { _, json -> MySnapshot.fromJson(json) })
 *
 * // 新增单值配置
 * @Bean fun mySingle(f: NacosConfigFactory) =
 *     f.subscriber("workflow.idempotency",
 *         mapper = { JsonUtil.deserialize(it, IdempotencyConfig::class.java) },
 *         default = IdempotencyConfig())
 * ```
 *
 * @param configService Nacos 配置服务（Spring Bean）
 * @param group         Nacos 分组名（默认 "WORKFLOW"）
 */
class NacosConfigFactory(
    private val configService: ConfigService,
    private val group: String = "WORKFLOW"
) {

    /**
     * 创建多键配置发布器
     *
     * @param dataIdPrefix 单个配置项的 dataId 前缀（如 `"workflow.definition."`）
     * @param indexDataId  索引 dataId；传 null 则不维护索引
     * @param serializer   快照 → JSON 字符串的序列化函数（默认使用 [JsonUtil]）
     */
    fun <T> keyedPublisher(
        dataIdPrefix: String,
        indexDataId: String? = null,
        serializer: (T) -> String = { JsonUtil.serialize(it) }
    ): KeyedConfigPublisher<T> =
        NacosKeyedConfigPublisher(configService, group, dataIdPrefix, indexDataId, serializer)

    /**
     * 创建多键配置订阅器
     *
     * @param dataIdPrefix 单个配置项的 dataId 前缀
     * @param indexDataId  索引 dataId
     * @param mapper       原始 JSON → 类型化快照的转换函数
     */
    fun <T : Any> keyedSubscriber(
        dataIdPrefix: String,
        indexDataId: String,
        mapper: (key: String, content: String) -> T?
    ): KeyedConfigSubscriber<T> =
        NacosKeyedConfigSubscriber(configService, group, dataIdPrefix, indexDataId, mapper)

    /**
     * 创建单值配置订阅器
     *
     * @param dataId  配置 dataId（如 `"workflow.idempotency"`）
     * @param mapper  原始 JSON 字符串 → 类型化配置对象的转换函数
     * @param default 解析失败或缺失时的默认值（可选）
     */
    fun <T> subscriber(
        dataId: String,
        mapper: (content: String) -> T,
        default: T? = null
    ): ConfigSubscriber<T> =
        NacosConfigSubscriber(configService, group, dataId, mapper, default)
}
