// fluxion-test - Shared test fixtures and in-memory SPI implementations.
// Provides base abstract test classes, JUnit 5 extensions, in-memory config stores,
// helper utilities, and shared dependencies used as testImplementation across modules.
// Key plugins: Kotlin JVM + java-library (api exposes test deps transitively to consumers).
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Core SPI modules that tests commonly exercise and stub.
    api(project(":fluxion-core"))
    api(project(":fluxion-config:core"))
    api(project(":fluxion-inbound:spi"))

    // JUnit 5 Jupiter (api) so consumers inherit test framework without redeclaration.
    api("org.junit.jupiter:junit-jupiter:5.10.2")
    // Kotlin coroutines test helpers (runTest, TestDispatcher) via api for shared test code.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}
