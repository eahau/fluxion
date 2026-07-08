package com.fluxion.core.enums

/**
 * Communication protocol used to trigger a workflow.
 *
 * Stored in [com.fluxion.core.model.WorkflowDefinition.protocol] and mirrored
 * in the unified runtime request (`UnifiedRequest.protocol`). Each adapter
 * module (HTTP / Dubbo / gRPC / Kafka) maps its native transport to one of
 * these values.
 */
enum class Protocol {
    /** HTTP REST API */
    HTTP,
    /** Apache Dubbo RPC */
    DUBBO,
    /** gRPC (HTTP/2 + Protobuf) */
    GRPC,
    /** Apache Kafka message listener */
    KAFKA,
    /** Internal in-process direct call (no network transport) */
    INTERNAL
}
