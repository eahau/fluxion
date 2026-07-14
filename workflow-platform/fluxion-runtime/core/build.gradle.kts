// fluxion-runtime:core - Runtime execution plane framework-agnostic core module.
// Provides DagExecutor entry point, WorkflowRouter orchestration, DefinitionProvider abstraction,
// and suspend-based workflow execution primitives using Kotlin coroutines. Zero Spring dependencies.
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Core base types + JsonUtil utilities (api - downstream modules inherit core types).
    api(project(":fluxion-core"))
    // DAG execution engine (api - downstream modules inherit engine types).
    api(project(":fluxion-engine"))
    // Inbound SPI (api - UnifiedRequest / InboundRouter contracts).
    api(project(":fluxion-inbound:spi"))
    // Config core (api - provides DefinitionConfigSubscriber, WorkflowDefinitionSnapshot, ChangeType).
    api(project(":fluxion-config:core"))

    // Kotlin coroutines core - DagExecutor entry is a suspend function.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    // Unified logging extensions.
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // Shared test fixtures module.
    testImplementation(project(":fluxion-test"))
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
