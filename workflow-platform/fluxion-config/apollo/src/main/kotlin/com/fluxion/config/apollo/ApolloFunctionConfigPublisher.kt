package com.fluxion.config.apollo

import com.ctrip.framework.apollo.openapi.client.ApolloOpenApiClient
import com.fluxion.adapter.spi.config.FunctionConfigPublisher
import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.adapter.spi.registry.PublishTarget
import com.fluxion.core.util.JsonUtil
import org.slf4j.*

/**
 * Apollo 瀹炵幇 鈥?鍑芥暟閰嶇疆鍙戝竷鍣紙Admin 渚э級
 */
class ApolloFunctionConfigPublisher(
    private val openApiClient: ApolloOpenApiClient,
    private val appId: String,
    private val env: String,
    private val clusterName: String,
    private val operator: String
) : FunctionConfigPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val NAMESPACE = "workflow-functions"
    }

    override fun publish(snapshot: FunctionConfigSnapshot) {
        publish(snapshot, PublishTarget.all())
    }

    override fun publish(snapshot: FunctionConfigSnapshot, target: PublishTarget) {
        val json = try {
            JsonUtil.serialize(snapshot)
        } catch (e: Exception) {
            throw RuntimeException("Failed to serialize function snapshot: ${snapshot.functionName}", e)
        }

        try {
            publishItemToApollo(
                client       = openApiClient,
                appId        = appId,
                env          = env,
                cluster      = clusterName,
                namespace    = NAMESPACE,
                key          = snapshot.functionName,
                value        = json,
                comment      = "function-config enabled=${snapshot.enabled}",
                releaseTitle = "Publish function: ${snapshot.functionName}",
                operator     = operator
            )
            log.info { "Published function config to Apollo: key=${snapshot.functionName}" }
        } catch (e: Exception) {
            throw RuntimeException("Failed to publish function config to Apollo: ${snapshot.functionName}", e)
        }
    }

    override fun unpublish(functionName: String) {
        try {
            unpublishItemFromApollo(
                client       = openApiClient,
                appId        = appId,
                env          = env,
                cluster      = clusterName,
                namespace    = NAMESPACE,
                key          = functionName,
                releaseTitle = "Unpublish function: $functionName",
                operator     = operator
            )
            log.info { "Removed function config from Apollo: key=$functionName" }
        } catch (e: Exception) {
            throw RuntimeException("Failed to unpublish function from Apollo: $functionName", e)
        }
    }
}
