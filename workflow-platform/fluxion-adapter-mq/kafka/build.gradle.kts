// fluxion-adapter-mq:kafka - Apache Kafka MQ protocol adapter implementation (zero Spring).
// Implements MQ publish/subscribe SPI via native Kafka Clients library (Consumer / Producer APIs),
// enabling asynchronous workflow invocation through Kafka topic messages.
plugins {
    kotlin("jvm")
}

dependencies {
    // MQ adapter SPI: message publish / subscribe contracts from fluxion-adapter-spi module.
    implementation(project(":fluxion-adapter-spi"))

    // Native Apache Kafka Clients library - Producer API + Consumer API (zero Spring dependency).
    implementation("org.apache.kafka:kafka-clients")

    // Jackson Databind + Kotlin module for JSON serialization of MQ message bodies.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // SLF4J logging facade.
    implementation("org.slf4j:slf4j-api")

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
}
