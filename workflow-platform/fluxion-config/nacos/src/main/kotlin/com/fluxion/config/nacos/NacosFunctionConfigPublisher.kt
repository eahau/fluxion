package com.fluxion.config.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.fluxion.adapter.spi.config.FunctionConfigPublisher
import com.fluxion.core.util.JsonUtil
import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.adapter.spi.registry.PublishTarget
import org.slf4j.*

/**
 * Nacos 实现 — 函数配置发布器（Admin 侧）
 */
class NacosFunctionConfigPublisher(
    private val configService: ConfigService
) : FunctionConfigPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val GROUP = "WORKFLOW"
        private const val DATA_ID_PREFIX = "workflow.function."
        const val INDEX_DATA_ID = "workflow.function.__index__"
    }

    /**
     * 索引更新锁 — 防止并发 publish/unpublish 导致 read-modify-write 竞态。
     *
     * 典型场景：两个线程同时读取索引 → 各自添加不同函数 → 后写者覆盖前者，
     * 导致某个函数从索引中丢失。
     */
    private val indexLock = Any()

    override fun publish(snapshot: FunctionConfigSnapshot) {
        publish(snapshot, PublishTarget.all())
    }

    override fun publish(snapshot: FunctionConfigSnapshot, target: PublishTarget) {
        val dataId = DATA_ID_PREFIX + snapshot.functionName
        val json = try {
            JsonUtil.serialize(snapshot)
        } catch (e: Exception) {
            throw RuntimeException("Failed to serialize function snapshot: ${snapshot.functionName}", e)
        }

        val success = try {
            configService.publishConfig(dataId, GROUP, json)
        } catch (e: Exception) {
            throw RuntimeException("Failed to publish function config to Nacos: $dataId", e)
        }
        if (!success) {
            throw RuntimeException("Nacos publishConfig returned false for function dataId=$dataId")
        }
        updateIndex(snapshot.functionName, add = true)
        log.info { "Published function config to Nacos: dataId=$dataId" }
    }

    override fun unpublish(functionName: String) {
        val dataId = DATA_ID_PREFIX + functionName
        val success = try {
            configService.removeConfig(dataId, GROUP)
        } catch (e: Exception) {
            throw RuntimeException("Failed to remove function config from Nacos: $dataId", e)
        }
        updateIndex(functionName, add = false)
        if (success) {
            log.info { "Removed function config from Nacos: dataId=$dataId" }
        } else {
            log.warn { "Nacos removeConfig returned false (may not exist): dataId=$dataId" }
        }
    }

    private fun updateIndex(functionName: String, add: Boolean) {
        synchronized(indexLock) {
            try {
                val current = configService.getConfig(INDEX_DATA_ID, GROUP, 5000)
                val index: MutableSet<String> = if (current.isNullOrBlank()) {
                    mutableSetOf()
                } else {
                    JsonUtil.deserializeSet(current, String::class.java).toMutableSet()
                }
                val changed = if (add) index.add(functionName) else index.remove(functionName)
                if (changed) {
                    val newIndex = JsonUtil.serialize(index)
                    configService.publishConfig(INDEX_DATA_ID, GROUP, newIndex)
                    log.info { "Updated Nacos function index: functionName=$functionName add=$add" }
                }
            } catch (e: Exception) {
                log.error(e) { "Failed to update Nacos function index for $functionName" }
            }
        }
    }
}
