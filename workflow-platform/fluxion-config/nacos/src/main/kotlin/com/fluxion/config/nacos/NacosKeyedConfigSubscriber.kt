package com.fluxion.config.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.alibaba.nacos.api.config.listener.Listener
import com.fluxion.config.core.ChangeType
import com.fluxion.config.core.KeyedConfigChangeListener
import com.fluxion.config.core.AbstractKeyedConfigSubscriber
import com.fluxion.config.core.SnapshotParser
import org.slf4j.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

open class NacosKeyedConfigSubscriber<T : Any>(
    protected val configService: ConfigService,
    protected val group: String,
    protected val dataIdPrefix: String,
    private val indexDataId: String,
    private val mapper: ((key: String, content: String) -> T?)? = null
) : AbstractKeyedConfigSubscriber<T>() {

    protected val listenerExecutor: Executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("nacos-${javaClass.simpleName}-listener", 0).factory()
    )

    private val indexListenerRegistered = AtomicBoolean(false)

    override fun mapKeyToSnapshot(key: String, content: String): T? = mapper?.invoke(key, content)

    override fun loadAll(): List<T> {
        val keys = loadIndex()
        val result = keys.mapNotNull { get(it) }
        log.info { "Loaded ${result.size} configs from Nacos [${javaClass.simpleName}]" }
        return result
    }

    override fun get(key: String): T? {
        val dataId = dataIdPrefix + key
        return try {
            val content = configService.getConfig(dataId, group, 5000)
            if (content.isNullOrBlank()) null
            else resolveAndGet(key, content)
        } catch (e: Exception) {
            log.error(e) { "Failed to get config from Nacos: dataId=$dataId" }
            null
        }
    }

    override fun watch(listener: KeyedConfigChangeListener<T>) {
        super.watch(listener)
        if (indexListenerRegistered.compareAndSet(false, true)) {
            registerIndexListener()
        }
    }

    protected open fun handleIndexChange(newIndexContent: String) {
        try {
            val newIds = SnapshotParser.parseFunctionIndex(newIndexContent, log)
            newIds.forEach { key -> get(key)?.let { notifyListeners(key, it, ChangeType.UPDATE) } }
            log.debug { "Processed index change, ${newIds.size} configs refreshed" }
        } catch (e: Exception) {
            log.error(e) { "Failed to handle index change from Nacos" }
        }
    }

    protected fun loadIndex(): List<String> {
        return try {
            val content = configService.getConfig(indexDataId, group, 5000)
            if (content.isNullOrBlank()) emptyList()
            else SnapshotParser.parseFunctionIndex(content, log).toList()
        } catch (e: Exception) {
            throw RuntimeException("Failed to load index from Nacos: $indexDataId", e)
        }
    }

    private fun registerIndexListener() {
        try {
            configService.addListener(indexDataId, group, object : Listener {
                override fun getExecutor(): Executor = listenerExecutor
                override fun receiveConfigInfo(configInfo: String) {
                    handleIndexChange(configInfo)
                }
            })
            log.info { "Registered Nacos listener on index dataId=$indexDataId" }
        } catch (e: Exception) {
            throw RuntimeException("Failed to register Nacos index listener", e)
        }
    }
}
