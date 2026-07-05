package com.fluxion.core.enums

/** 适配器协议类型（对应 UnifiedRequest.protocol 前缀） */
enum class Protocol {
    /** HTTP REST API */
    HTTP,
    /** Apache Dubbo RPC */
    DUBBO,
    /** gRPC (HTTP/2 + Protobuf) */
    GRPC,
    /** Apache Kafka 消息 */
    KAFKA,
    /** 内部直接调用（跳过路由） */
    INTERNAL
}
