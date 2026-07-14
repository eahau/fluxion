// fluxion-outbound:http - HTTP-based external function transport implementation.
// Implements ExternalFunctionTransport SPI using OkHttp client to invoke remote workflow functions
// over REST/HTTP. Reuses builtin HTTP function HttpClientAdapter SPI for connection pooling.
plugins {
    kotlin("jvm")
}

dependencies {
    // External function SPI: ExternalFunctionTransport abstraction + ServiceLoader registry.
    implementation(project(":fluxion-outbound"))
    // Reuses HTTP client adapter SPI + built-in HTTP call implementation for connection management.
    implementation(project(":fluxion-function:builtin"))

    // Jackson Databind for JSON serialization of remote function payloads.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Kotlin reflection for dynamic request/response class mapping.
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Unified logging.
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation(project(":fluxion-function:builtin"))
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}
