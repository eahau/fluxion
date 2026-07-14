package com.fluxion.config.apollo

import com.ctrip.framework.apollo.openapi.client.ApolloOpenApiClient
import com.fluxion.config.core.SchemaConfigPublisher
import com.fluxion.config.core.SchemaConfigSnapshot
import com.fluxion.config.core.PublishTarget
import com.fluxion.core.util.JsonUtil
import org.slf4j.*

class ApolloSchemaConfigPublisher(
    private val openApiClient: ApolloOpenApiClient,
    private val appId: String,
    private val env: String,
    private val clusterName: String,
    private val operator: String
) : SchemaConfigPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val NAMESPACE = "workflow-schemas"
    }

    override fun publish(snapshot: SchemaConfigSnapshot) {
        publish(snapshot, PublishTarget.all())
    }

    override fun publish(snapshot: SchemaConfigSnapshot, target: PublishTarget) {
        val json = try {
            JsonUtil.serialize(snapshot)
        } catch (e: Exception) {
            throw RuntimeException("Failed to serialize schema snapshot: ${snapshot.schemaName}", e)
        }

        try {
            publishItemToApollo(
                client = openApiClient,
                appId = appId,
                env = env,
                cluster = clusterName,
                namespace = NAMESPACE,
                key = snapshot.schemaName,
                value = json,
                comment = "schema-config format=${snapshot.schemaFormat} enabled=${snapshot.enabled}",
                releaseTitle = "Publish schema: ${snapshot.schemaName}",
                operator = operator
            )
            log.info { "Published schema config to Apollo: key=${snapshot.schemaName}" }
        } catch (e: Exception) {
            throw RuntimeException("Failed to publish schema config to Apollo: ${snapshot.schemaName}", e)
        }
    }

    override fun unpublish(schemaName: String) {
        try {
            unpublishItemFromApollo(
                client = openApiClient,
                appId = appId,
                env = env,
                cluster = clusterName,
                namespace = NAMESPACE,
                key = schemaName,
                releaseTitle = "Unpublish schema: $schemaName",
                operator = operator
            )
            log.info { "Removed schema config from Apollo: key=$schemaName" }
        } catch (e: Exception) {
            throw RuntimeException("Failed to unpublish schema from Apollo: $schemaName", e)
        }
    }
}
