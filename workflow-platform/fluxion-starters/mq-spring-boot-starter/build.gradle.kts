// fluxion-starters:mq-spring-boot-starter - Convenience aggregator starter for Message Queue.
// Combines the MQ inbound consumer stack (Kafka workflow consumer, producer-side DLQ
// publisher) with the MQ outbound external-function transport (EXTERNAL nodes with
// protocol="mq" fire JSON payloads at a topic/queue). The actual MQ client library
// (Kafka/RocketMQ/RabbitMQ) must be supplied by the hosting application.
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Inbound MQ adapter Spring Boot auto-configuration.
    api(project(":fluxion-inbound:mq:spring-boot"))
    // Outbound MQ external-function transport + MqPublisher SPI auto-configuration.
    api(project(":fluxion-outbound:mq:spring-boot"))
}
