package com.fluxion.adapter.dubbo

/**
 * Dubbo 工作流服务接口（API 契约）
 * 消费方通过此接口调用工作流
 *
 * 路由规则：
 *   - 直调模式：params 中携带 `_workflowId` 键时跳过路由，直接按 ID 执行
 *   - 普通调用：Dubbo attachments 中携带 `x-service-key` 时按 bind_key 路由
 *   - 兼容方式：params 中携带 `_serviceKey` 时按 bind_key 路由
 */
interface DubboWorkflowApi {

    /**
     * 执行工作流
     *
     * @param params 请求参数 Map；路由标识通过 `_workflowId`、`_serviceKey` 或
     *               Dubbo attachments 中的 `x-service-key` 传递
     * @return 工作流执行结果 data；异常时返回包含 errorCode/message 的 Map
     */
    fun execute(params: Map<String, Any>): Any?
}
