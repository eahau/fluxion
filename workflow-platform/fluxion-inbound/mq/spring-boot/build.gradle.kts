// fluxion-inbound:mq:spring-boot - Spring Boot auto-configuration for MQ adapter suite.
// Registers KafkaProducerFactory / KafkaConsumerListener beans conditionally when Kafka clients
// class is detected on classpath. Currently supports Kafka only (RabbitMQ/RocketMQ extendable).
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // Core base types (TriggerFunctionMeta SPI, model classes).
    implementation(project(":fluxion-core"))
    // Native Kafka client implementation (zero Spring).
    implementation(project(":fluxion-inbound:mq:kafka"))
    // InboundRouter SPI.
    implementation(project(":fluxion-inbound:spi"))
    // MqPublisher SPI from outbound.
    implementation(project(":fluxion-outbound:mq"))

    // Spring Boot AutoConfigure mechanism - @AutoConfiguration + @ConditionalOnClass(KafkaProducer::class).
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Native Apache Kafka Clients library (Producer/Consumer bean creation at runtime).
    implementation("org.apache.kafka:kafka-clients")

    // Jackson Kotlin + Databind for JSON message body conversion.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // SLF4J logging.
    implementation("org.slf4j:slf4j-api")

    // Spring Boot Test starter for integration testing with embedded Kafka-compatible test harness.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

