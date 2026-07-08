package com.fluxion.config.apollo

import com.ctrip.framework.apollo.openapi.client.ApolloOpenApiClient
import com.ctrip.framework.apollo.openapi.dto.NamespaceReleaseDTO
import com.ctrip.framework.apollo.openapi.dto.OpenItemDTO

/**
 * Apollo 鍙戝竷鍣ㄥ唴閮ㄥ叡浜伐鍏?鈥?娑堥櫎 OpenItemDTO / NamespaceReleaseDTO 鏋勫缓閲嶅
 *
 * 涓変釜 Apollo 鍙戝竷鍣紙DefinitionPublisher銆丗unctionPublisher銆両nstanceRegistry锛?
 * 鍏变韩鐩稿悓鐨?createOrUpdateItem + publishNamespace 妯″紡銆?
 * 杩欎簺 internal 鍑芥暟灏?DTO 鏋勫缓鍜?API 璋冪敤鏀舵暃鍒颁竴澶勩€?
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
 * 瀹屾暣鐨?item 鍐欏叆 + namespace 鍙戝竷绠＄嚎銆?
 *
 * 灏?createOrUpdateItem 鈫?publishNamespace 鐨勪袱姝ユ搷浣滃皝瑁呬负鍗曚竴鍑芥暟锛?
 * 渚涙墍鏈?Apollo 鍙戝竷鍣ㄧ殑 publish/unpublish 鏂规硶澶嶇敤銆?
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
 * 瀹屾暣鐨?item 鍒犻櫎 + namespace 鍙戝竷绠＄嚎銆?
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
