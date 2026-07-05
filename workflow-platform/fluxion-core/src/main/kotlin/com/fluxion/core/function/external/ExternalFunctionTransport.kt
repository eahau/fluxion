package com.fluxion.core.function.external

/**
 * 外部函数调用传输层 SPI。
 *
 * 各协议（HTTP/gRPC/Dubbo 等）实现该接口并注册到 [ExternalFunctionTransportRegistry]，
 * 由 [com.fluxion.script.function.ExternalWorkflowFunction] 按 protocol 路由。
 *
 * 生命周期：
 * 1. 服务启动 / 函数配置热更新时，框架调用 [prepare] 传入该协议下的全部配置，
 *    transport 可在此阶段预建远程端点（如 gRPC Channel、Dubbo Reference），实现 fail-fast。
 * 2. 运行时每次调用走 [invoke]，应复用 [prepare] 阶段已构建的端点，避免在 invoke 中首次建连。
 * 3. 应用停止 / transport 被注销时，框架调用 [shutdown]，transport 应在此释放所有远程端点资源。
 */
interface ExternalFunctionTransport {

    /** 返回支持的协议名，如 http、grpc、dubbo */
    fun protocol(): String

    /**
     * 服务启动或配置变更时预建远程端点。
     *
     * 默认空实现，保持向后兼容。需要预建端点的 transport（如 gRPC/Dubbo）应重写此方法。
     *
     * @param configs 当前已加载的该协议函数配置列表
     */
    fun prepare(configs: List<ExternalFunctionConfig>) {}

    /**
     * 执行外部调用。
     *
     * 实现类应为同步阻塞或快速返回；涉及 IO 的场景由调用方线程模型决定。
     * 远程端点应在 [prepare] 阶段构建，[invoke] 中只做路由和调用。
     */
    fun invoke(request: ExternalFunctionRequest): ExternalFunctionResponse

    /**
     * 应用停止或 transport 被注销时释放远程端点资源。
     *
     * 默认空实现，保持向后兼容。持有远程端点的 transport（如 gRPC/Dubbo）必须重写此方法，
     * 关闭 ManagedChannel、销毁 ReferenceConfig 等，避免泄漏。
     *
     * 实现需保证幂等：重复调用不应抛出异常或产生副作用。
     */
    fun shutdown() {}
}
