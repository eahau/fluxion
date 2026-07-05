package com.fluxion.config.apollo

import com.ctrip.framework.apollo.openapi.client.ApolloOpenApiClient
import com.ctrip.framework.apollo.openapi.dto.NamespaceReleaseDTO
import com.ctrip.framework.apollo.openapi.dto.OpenItemDTO

/**
 * Apollo 发布器内部共享工具 — 消除 OpenItemDTO / NamespaceReleaseDTO 构建重复
 *
 * 三个 Apollo 发布器（DefinitionPublisher、FunctionPublisher、InstanceRegistry）
 * 共享相同的 createOrUpdateItem + publishNamespace 模式。
 * 这些 internal 函数将 DTO 构建和 API 调用收敛到一处。
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
 * 完整的 item 写入 + namespace 发布管线。
 *
 * 将 createOrUpdateItem → publishNamespace 的两步操作封装为单一函数，
 * 供所有 Apollo 发布器的 publish/unpublish 方法复用。
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
 * 完整的 item 删除 + namespace 发布管线。
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
