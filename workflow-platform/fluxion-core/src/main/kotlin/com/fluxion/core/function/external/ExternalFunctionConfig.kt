package com.fluxion.core.function.external

/**
 * 外部函数调用配置。
 *
 * 由管理端在发布 EXTERNAL 函数时写入，Worker 侧反序列化后交给对应 [ExternalFunctionTransport] 执行。
 *
 * 该类为协议无关的公共配置模型，各协议专属字段（如 Dubbo 的 version/group/parameterTypes、
 * gRPC 的 useTls 等）统一通过 [extras] 透传，由各 transport 自行读取。
 */
data class ExternalFunctionConfig(
    /** 调用协议，如 http、grpc、dubbo */
    val protocol: String = "http",
    /** 目标服务标识（HTTP 为 URL，gRPC 为 service 全限定名，Dubbo 为 interface 全限定名） */
    val service: String? = null,
    /** 目标方法/路径 */
    val method: String? = null,
    /** 直连地址，如 host:port；为空时走注册中心/服务发现 */
    val endpoint: String? = null,
    /** 调用超时（毫秒），0 表示使用 transport 默认值 */
    val timeoutMs: Long = 0,
    /** 请求头 / attachment / metadata 等附加参数 */
    val headers: Map<String, String>? = null,
    /** 入参映射：key=目标参数字段，value=源字段路径（支持点号路径） */
    val paramMapping: Map<String, String>? = null,
    /** 透传原始配置项，供各 transport 读取协议专属扩展字段 */
    val extras: Map<String, Any>? = null
) {
    /**
     * 便捷读取 extras 中指定 key 的字符串值。
     */
    fun extraString(key: String): String? = extras?.get(key)?.toString()

    /**
     * 便捷读取 extras 中指定 key 的字符串列表。
     */
    @Suppress("UNCHECKED_CAST")
    fun extraStringList(key: String): List<String>? = extras?.get(key) as? List<String>
}
