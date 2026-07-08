// fluxion-function - Workflow function abstraction core module.
// Defines FunctionComponent, WorkflowFunction, FunctionResult, FunctionRegistry,
// FunctionInstanceProvider, and supporting types. Base module for all built-in, external,
// and script-based function implementations.
// Key plugins: Kotlin JVM + java-library (api for SPI exposure).
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Core base types: NodeInput, NodeType, WorkflowContext, exception types, utilities.
    api(project(":fluxion-core"))
    // Unified logging extensions (via implementation; logging not part of public SPI).
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
