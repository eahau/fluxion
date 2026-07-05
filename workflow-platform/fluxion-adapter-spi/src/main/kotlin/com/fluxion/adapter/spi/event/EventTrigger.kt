package com.fluxion.adapter.spi.event

/**
 * 事件触发器 SPI — 定义工作流的事件驱动接入能力
 *
 * 实现此接口以支持不同类型的事件源：
 * - 内置事件（工作流完成、节点失败等平台内部事件）
 * - 外部事件（第三方应用事件，如飞书用户创建、企微审批等）
 * - Webhook 事件（HTTP POST 回调）
 *
 * 生命周期：
 * - 工作流发布时调用 [subscribe] 注册事件监听
 * - 工作流下线时调用 [unsubscribe] 取消事件监听
 *
 * 典型实现：
 * - Kafka 消费者：订阅 topic → 触发工作流
 * - Spring ApplicationEvent：监听平台内部事件
 * - HTTP Webhook 端点：接收外部 POST 回调
 */
interface EventTrigger {

    /**
     * 订阅指定事件类型。
     *
     * @param eventType 事件类型标识（如 "user.created"、"workflow.completed"）
     * @param handler   事件处理回调，接收事件数据（Map 格式）
     */
    fun subscribe(eventType: String, handler: (Map<String, Any>) -> Unit)

    /**
     * 取消订阅指定事件类型。
     *
     * @param eventType 事件类型标识
     */
    fun unsubscribe(eventType: String)

    /**
     * 列出当前所有已订阅的事件类型。
     */
    fun listSubscriptions(): List<String>
}

/**
 * 事件触发器注册中心 — 管理多个 [EventTrigger] 实现
 *
 * 按事件类型路由到对应的触发器实现。
 */
class EventTriggerRegistry {

    private val triggers = mutableMapOf<String, EventTrigger>()

    /**
     * 注册事件触发器实现。
     *
     * @param name    触发器名称（如 "kafka"、"spring-event"、"webhook"）
     * @param trigger 触发器实现
     */
    fun register(name: String, trigger: EventTrigger) {
        triggers[name] = trigger
    }

    /**
     * 获取指定名称的触发器。
     */
    fun get(name: String): EventTrigger? = triggers[name]

    /**
     * 列出所有已注册的触发器名称。
     */
    fun listNames(): List<String> = triggers.keys.toList()
}
