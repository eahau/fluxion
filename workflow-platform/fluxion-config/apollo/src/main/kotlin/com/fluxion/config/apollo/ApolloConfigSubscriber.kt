package com.fluxion.config.apollo

import com.ctrip.framework.apollo.Config
import com.ctrip.framework.apollo.ConfigService
import com.fluxion.adapter.spi.config.ConfigChangeListener
import com.fluxion.adapter.spi.config.ConfigSubscriber
import com.fluxion.config.core.AbstractConfigSubscriber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Apollo 通用单值配置订阅器 — 封装 namespace 订阅 + 自定义解析的公共逻辑
 *
 * 本类为具体类（非 abstract），可直接实例化使用。
 * 与 [ApolloKeyedConfigSubscriber] 命名对称：后者处理多键集合，本类处理单值配置。
 *
 * 新增单值配置只需在 AutoConfiguration 中一行注册：
 *
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
 * 对标 `NacosConfigs.java` 中 `ConfigProvider<T>` 的声明式模式，
 * 但增加了 Apollo namespace 抽象和 Spring DI 集成。
 *
 * @param T         配置类型
 * @param namespace  Apollo namespace 名称
 * @param parser     Apollo [Config] → 类型化配置对象的解析函数；
 *                   通过 Config.getProperty() 读取任意属性
 * @param defaultConfig 解析失败时的默认值（可选）
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
            log.info("Registered Apollo config listener on namespace [$namespace]")
        }
    }

    // ─── 内部方法 ────────────────────────────────────────────────────

    private fun parseConfig(): T = try {
        parser(config)
    } catch (e: Exception) {
        log.warn("Failed to parse config from Apollo namespace [$namespace]", e)
        defaultConfig ?: throw e
    }
}
