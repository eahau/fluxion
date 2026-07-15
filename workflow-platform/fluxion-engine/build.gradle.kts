// fluxion-engine - DAG (Directed Acyclic Graph) workflow execution engine.
// Implements node scheduling, dependency resolution, idempotency enforcement, error handling,
// and suspend-based concurrent execution using Kotlin coroutines.
// Key plugins: Kotlin JVM + java-library.
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Cross-cutting decorator SPI (intercepts node execution for metrics/tracing/caching).
    api(project(":fluxion-decorator"))
    // Function abstraction (FunctionComponent, WorkflowFunction, FunctionResult types).
    api(project(":fluxion-function"))
    // Core base types and utilities (NodeInput, WorkflowContext, JsonUtil, AviatorScript).
    api(project(":fluxion-core"))

    // Unified logging extensions.
    implementation(project(":fluxion-log"))
    implementation(project(":fluxion-schema:json"))
    // Fluxion cache - configurable cache for idempotency store implementation.
    implementation(project(":fluxion-cache"))
    // AviatorScript expression engine for ExpressionEvaluator-based expression nodes.
    implementation("com.googlecode.aviator:aviator:5.4.3")

    // ===== Testing Dependencies =====
    testImplementation(project(":fluxion-mock"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}

sourceSets {
    main { kotlin { srcDirs("src/main/kotlin") } }
}

tasks.withType<Test> { useJUnitPlatform() }
