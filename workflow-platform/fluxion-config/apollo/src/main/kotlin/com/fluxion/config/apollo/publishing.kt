package com.fluxion.config.apollo

import com.ctrip.framework.apollo.openapi.client.ApolloOpenApiClient
import com.ctrip.framework.apollo.openapi.dto.NamespaceReleaseDTO
import com.ctrip.framework.apollo.openapi.dto.OpenItemDTO

/**
 * Apollo publisher internal shared utilities - eliminate OpenItemDTO/NamespaceReleaseDTO construction duplication.
 *
 * The three Apollo publishers (DefinitionPublisher, FunctionPublisher, InstanceRegistry)
 * all follow the same createOrUpdateItem + publishNamespace pattern.
 * These internal functions consolidate DTO construction and API calls into one place.
 */

internal fun buildItem(
    key: String,
    value: String,
    comment: String,
    operator: String
): OpenItemDTO = OpenItemDTO().apply {
    this.key = key
    this.value = value
    this.comment = comment
    dataChangeCreatedBy = operator
    dataChangeLastModifiedBy = operator
}

internal fun buildRelease(
    title: String,
    operator: String
): NamespaceReleaseDTO = NamespaceReleaseDTO().apply {
    releaseTitle = title
    releasedBy = operator
}

/**
 * Complete item write + namespace release pipeline.
 *
 * Combines the two-step createOrUpdateItem -> publishNamespace operation into a single function,
 * reused by all Apollo publishers' publish/unpublish methods.
 */
internal fun publishItemToApollo(
    client: ApolloOpenApiClient,
    appId: String,
    env: String,
    cluster: String,
    namespace: String,
    key: String,
    value: String,
    comment: String,
    releaseTitle: String,
    operator: String
) {
    val item = buildItem(key, value, comment, operator)
    client.createOrUpdateItem(appId, env, cluster, namespace, item)
    val release = buildRelease(releaseTitle, operator)
    client.publishNamespace(appId, env, cluster, namespace, release)
}

/**
 * Complete item removal + namespace release pipeline.
 */
internal fun unpublishItemFromApollo(
    client: ApolloOpenApiClient,
    appId: String,
    env: String,
    cluster: String,
    namespace: String,
    key: String,
    releaseTitle: String,
    operator: String
) {
    client.removeItem(appId, env, cluster, namespace, key, operator)
    val release = buildRelease(releaseTitle, operator)
    client.publishNamespace(appId, env, cluster, namespace, release)
}
