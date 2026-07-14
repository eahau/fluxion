package com.fluxion.config.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.fluxion.config.core.DefinitionConfigPublisher
import org.slf4j.*

/**
 * Nacos implementation - workflow definition publisher (Admin side).
 */
class NacosDefinitionConfigPublisher(
    private val configService: ConfigService
) : DefinitionConfigPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val GROUP = "WORKFLOW"
        private const val DATA_ID_PREFIX = "workflow.definition."
    }

    override fun publish(workflowId: String, definitionJson: String, version: Int) {
        val dataId = DATA_ID_PREFIX + workflowId
        val success = try {
            configService.publishConfig(dataId, GROUP, definitionJson)
        } catch (e: Exception) {
            throw RuntimeException("Failed to publish workflow definition to Nacos: $dataId", e)
        }
        if (success) {
            log.info { "Published workflow definition to Nacos: dataId=$dataId version=$version" }
        } else {
            throw RuntimeException("Nacos publishConfig returned false for dataId=$dataId")
        }
    }

    override fun unpublish(workflowId: String) {
        val dataId = DATA_ID_PREFIX + workflowId
        val success = try {
            configService.removeConfig(dataId, GROUP)
        } catch (e: Exception) {
            throw RuntimeException("Failed to remove workflow definition from Nacos: $dataId", e)
        }
        if (success) {
            log.info { "Removed workflow definition from Nacos: dataId=$dataId" }
        } else {
            log.warn { "Nacos removeConfig returned false (may not exist): dataId=$dataId" }
        }
    }
}
