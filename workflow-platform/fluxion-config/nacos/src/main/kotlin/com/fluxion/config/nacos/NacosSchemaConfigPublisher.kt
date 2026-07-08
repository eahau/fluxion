package com.fluxion.config.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.fluxion.adapter.spi.config.SchemaConfigPublisher
import com.fluxion.adapter.spi.config.SchemaConfigSnapshot
import com.fluxion.adapter.spi.registry.PublishTarget
import com.fluxion.core.util.JsonUtil
import org.slf4j.*

/**
 * Nacos 瀹炵幇 鈥?Schema 閰嶇疆鍙戝竷鍣紙Admin 渚э級
 */
class NacosSchemaConfigPublisher(
    private val configService: ConfigService
) : SchemaConfigPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val GROUP = "WORKFLOW"
        private const val DATA_ID_PREFIX = "workflow.schema."
        const val INDEX_DATA_ID = "workflow.schema.__index__"
    }

    /** 绱㈠紩鏇存柊閿?鈥?闃叉骞跺彂 publish/unpublish 瀵艰嚧 read-modify-write 绔炴€?*/
    private val indexLock = Any()

    override fun publish(snapshot: SchemaConfigSnapshot) {
        publish(snapshot, PublishTarget.all())
    }

    override fun publish(snapshot: SchemaConfigSnapshot, target: PublishTarget) {
        val dataId = DATA_ID_PREFIX + snapshot.schemaName
        val json = try {
            JsonUtil.serialize(snapshot)
        } catch (e: Exception) {
            throw RuntimeException("Failed to serialize schema snapshot: ${snapshot.schemaName}", e)
        }

        val success = try {
            configService.publishConfig(dataId, GROUP, json)
        } catch (e: Exception) {
            throw RuntimeException("Failed to publish schema config to Nacos: $dataId", e)
        }
        if (!success) {
            throw RuntimeException("Nacos publishConfig returned false for schema dataId=$dataId")
        }
        updateIndex(snapshot.schemaName, add = true)
        log.info { "Published schema config to Nacos: dataId=$dataId" }
    }

    override fun unpublish(schemaName: String) {
        val dataId = DATA_ID_PREFIX + schemaName
        val success = try {
            configService.removeConfig(dataId, GROUP)
        } catch (e: Exception) {
            throw RuntimeException("Failed to remove schema config from Nacos: $dataId", e)
        }
        updateIndex(schemaName, add = false)
        if (success) {
            log.info { "Removed schema config from Nacos: dataId=$dataId" }
        } else {
            log.warn { "Nacos removeConfig returned false (may not exist): dataId=$dataId" }
        }
    }

    private fun updateIndex(schemaName: String, add: Boolean) {
        synchronized(indexLock) {
            try {
                val current = configService.getConfig(INDEX_DATA_ID, GROUP, 5000)
                val index: MutableSet<String> = if (current.isNullOrBlank()) {
                    mutableSetOf()
                } else {
                    JsonUtil.deserializeSet(current, String::class.java).toMutableSet()
                }
                val changed = if (add) index.add(schemaName) else index.remove(schemaName)
                if (changed) {
                    val newIndex = JsonUtil.serialize(index)
                    configService.publishConfig(INDEX_DATA_ID, GROUP, newIndex)
                    log.info { "Updated Nacos schema index: schemaName=$schemaName add=$add" }
                }
            } catch (e: Exception) {
                log.error(e) { "Failed to update Nacos schema index for $schemaName" }
            }
        }
    }
}
