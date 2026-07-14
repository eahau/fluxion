// fluxion-outbound:mq - MQ-backed external function transport + producer SPI.
// Hosts two things: (1) the transport-agnostic MqPublisher / MqPublishResult SPI that concrete
// producer stacks (Kafka, RocketMQ, etc.) implement, and (2) the ExternalFunctionTransport
// implementation (protocol="mq") that serialises EXTERNAL-node inputs into messages and sends
// them via the injected MqPublisher. Zero Spring dependency so the core transport works in any
// hosting environment.
plugins {
    kotlin("jvm")
    `java-library`
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}

dependencies {
    // External function SPI: ExternalFunctionTransport abstraction + ServiceLoader registry.
    implementation(project(":fluxion-outbound"))
    // Core base types (shared value types, JsonUtil helper) - api so callers that compileOnly
    // us still see MqPublishResult fields correctly.
    api(project(":fluxion-core"))
    // Jackson Databind for JSON serialisation of outgoing MQ payloads.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // SLF4J logging for transport-level error reporting.
    implementation("org.slf4j:slf4j-api")
    // Unified logging extensions (lazy lambda wrappers over SLF4J).
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}

