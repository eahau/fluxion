package com.fluxion.config.apollo

import com.ctrip.framework.apollo.openapi.client.ApolloOpenApiClient
import com.fluxion.config.core.DefinitionConfigPublisher
import org.slf4j.*

/**
 * Apollo implementation - workflow definition publisher (Admin side).
 */
class ApolloDefinitionConfigPublisher(
    private val openApiClient: ApolloOpenApiClient,
    private val appId: String,
    private val env: String,
    private val clusterName: String,
    private val operator: String
) : DefinitionConfigPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val NAMESPACE = "workflow-definitions"
    }

    override fun publish(workflowId: String, definitionJson: String, version: Int) {
        try {
            publishItemToApollo(
                client      = openApiClient,
                appId       = appId,
                env         = env,
                cluster     = clusterName,
                namespace   = NAMESPACE,
                key         = workflowId,
                value       = definitionJson,
                comment     = "workflow-definition v$version",
                releaseTitle = "Publish workflow: $workflowId v$version",
                operator    = operator
            )
            log.info { "Published workflow definition to Apollo: key=$workflowId version=$version" }
        } catch (e: Exception) {
            throw RuntimeException("Failed to publish workflow definition to Apollo: $workflowId", e)
        }
    }

    override fun unpublish(workflowId: String) {
        try {
            unpublishItemFromApollo(
                client      = openApiClient,
                appId       = appId,
                env         = env,
                cluster     = clusterName,
                namespace   = NAMESPACE,
                key         = workflowId,
                releaseTitle = "Unpublish workflow: $workflowId",
                operator    = operator
            )
            log.info { "Removed workflow definition from Apollo: key=$workflowId" }
        } catch (e: Exception) {
            throw RuntimeException("Failed to unpublish workflow from Apollo: $workflowId", e)
        }
    }
}
