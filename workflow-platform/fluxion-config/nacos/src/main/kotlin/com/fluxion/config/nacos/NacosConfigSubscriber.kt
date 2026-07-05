package com.fluxion.config.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.alibaba.nacos.api.config.listener.Listener
import com.fluxion.adapter.spi.config.ConfigChangeListener
import com.fluxion.adapter.spi.config.ConfigSubscriber
import com.fluxion.config.core.AbstractConfigSubscriber
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Nacos 通用单值配置订阅器 — 封装 dataId 订阅 + JSON 解析的公共逻辑
 *
 * 本类为具体类（非 abstract），可直接实例化使用。
 * 与 [NacosKeyedConfigSubscriber] 命名对称：后者处理多键集合，本类处理单值配置。
 *
 * 新增单值配置只需在 AutoConfiguration 中一行注册：
 *
 * ```kotlin
 * @Bean
 * fun myConfigSubscriber(cs: ConfigService) =
 *     NacosConfigSubscriber<MyConfig>(
 *         configService = cs, group = "WORKFLOW", dataId = "my.config",
 *         mapper = { content -> JsonUtil.deserialize(content, MyConfig::class.java) },
 *         defaultConfig = MyConfig())
 * ```
 *
 * 对标 `NacosConfigs.java` 中 `ConfigProvider<T>` 的声明式模式，
 * 但增加了多后端抽象、Spring DI 集成和类型安全的监听器管线。
 *
 * @param T             配置类型
 * @param configService Nacos 配置服务
 * @param group         Nacos 分组名
 * @param dataId        配置 dataId
 * @param mapper        原始 JSON 字符串 → 类型化配置对象的转换函数
 * @param defaultConfig 解析失败或缺失时的默认值（可选）
 */
open class NacosConfigSubscriber<T>(
    private val configService: ConfigService,
    private val group: String,
    private val dataId: String,
    private val mapper: (content: String) -> T,
    private val defaultConfig: T? = null
) : AbstractConfigSubscriber<ConfigChangeListener<T>>(),
    ConfigSubscriber<T> {

    companion object {
        private const val TIMEOUT_MS = 5_000L
    }

    private val listenerExecutor: Executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("nacos-${javaClass.simpleName}", 0).factory()
    )

    private val listenerRegistered = AtomicBoolean(false)

    override fun load(): T {
        val content = configService.getConfig(dataId, group, TIMEOUT_MS)
        return parseContent(content)
    }

    override fun watch(listener: ConfigChangeListener<T>) {
        addListener(listener)
        if (listenerRegistered.compareAndSet(false, true)) {
            configService.addListener(dataId, group, object : Listener {
                override fun getExecutor(): Executor = listenerExecutor
                override fun receiveConfigInfo(configInfo: String) {
                    notifyListeners { it.onChange(parseContent(configInfo)) }
                }
            })
            log.info("Registered Nacos config listener on dataId=$dataId")
        }
    }

    // ─── 内部方法 ────────────────────────────────────────────────────

    private fun parseContent(content: String?): T {
        if (content.isNullOrBlank()) {
            return defaultConfig ?: throw IllegalStateException(
                "Config is empty for dataId=$dataId and no default provided")
        }
        return try {
            mapper(content)
        } catch (e: Exception) {
            log.warn("Failed to parse config from Nacos: dataId=$dataId", e)
            defaultConfig ?: throw e
        }
    }
}
