package com.fluxion.config.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.alibaba.nacos.api.config.listener.Listener
import com.fluxion.config.core.ConfigChangeListener
import com.fluxion.config.core.ConfigSubscriber
import com.fluxion.config.core.AbstractConfigSubscriber
import org.slf4j.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
            log.info { "Registered Nacos config listener on dataId=$dataId" }
        }
    }

    private fun parseContent(content: String?): T {
        if (content.isNullOrBlank()) {
            return defaultConfig ?: throw IllegalStateException(
                "Config is empty for dataId=$dataId and no default provided")
        }
        return try {
            mapper(content)
        } catch (e: Exception) {
            log.warn(e) { "Failed to parse config from Nacos: dataId=$dataId" }
            defaultConfig ?: throw e
        }
    }
}
