// fluxion-core - Foundation library module providing core workflow abstractions.
// Contains base types (NodeType, NodeInput, FunctionResult, WorkflowContext), SPI interfaces,
// shared utility classes (JsonUtil, idempotency store), and AviatorScript expression engine.
// Key plugins: Kotlin JVM + java-library (exposes api dependencies transitively).
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Kotlin Coroutines core - suspend functions, DistributedLock, and async primitives.
    // Exposed as api because coroutine types appear on public method signatures of base types.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // fluxion-schema modules - data contract abstraction (JSON Schema / Protobuf / Avro support).
    // Exposed as api because schema types (Schema, SchemaValidator) are part of core public API.
    api(project(":fluxion-schema:core"))
    api(project(":fluxion-schema:json"))

    // Jackson JSON serialization stack - consumed internally by JsonUtil utility class.
    // implementation scope avoids forcing Jackson versions on downstream consumers unless needed.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Unified logging extension module - Kotlin SLF4J lazy logger delegates.
    // Exposed as api so every downstream module inherits the logger() extension function.
    api(project(":fluxion-log"))

    // Caffeine - high-performance in-memory cache backing CaffeineIdempotencyStore implementation.
    implementation("com.github.ben-manes.caffeine:caffeine")

    // AviatorScript - lightweight expression evaluator powering Expression nodes and MockEngine.
    implementation("com.googlecode.aviator:aviator:5.4.3")

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    // Log4j2 SLF4J binding for test runtime (unit tests do not pull in Spring Boot starters).
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}

// Explicit Kotlin source set registration (default location; kept for build clarity).
sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}

// Lock test execution engine to JUnit Platform (JUnit 5 Jupiter).
tasks.withType<Test> {
    useJUnitPlatform()
}
