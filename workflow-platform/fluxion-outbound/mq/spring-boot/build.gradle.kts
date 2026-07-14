// fluxion-outbound:mq:spring-boot - Spring Boot auto-configuration for MQ external transport.
// Auto-registers MqExternalFunctionTransport bean when an MqPublisher is available (Kafka,
// RabbitMQ, RocketMQ, etc.) so Spring Boot apps gain EXTERNAL-node protocol=mq capability
// by simply including this starter plus a concrete producer implementation.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
}

dependencies {
    // MQ external transport core implementation + producer SPI (api scope so downstream
    // consumers inherit MqPublisher / MqPublishResult / MqExternalFunctionTransport types).
    api(project(":fluxion-outbound:mq"))
    // External function core SPI: ExternalFunctionTransport abstraction lives here.
    implementation(project(":fluxion-outbound"))

    // Spring Boot AutoConfigure mechanism + conditional bean guards.
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    // Unified logging extensions.
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
