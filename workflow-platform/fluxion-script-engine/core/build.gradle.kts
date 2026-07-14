// fluxion-script-engine:core - Embedded script execution engine core module (zero Spring).
// Supports Groovy dynamic script evaluation with Caffeine-backed compilation cache for
// performance. Implements ScriptFunctionComponent extending FunctionComponent SPI.
plugins {
    kotlin("jvm")
}

dependencies {
    // Core base types: NodeInput, FunctionResult, WorkflowContext.
    implementation(project(":fluxion-core"))
    // DI module: FunctionInstanceProvider + DependencyResolver for script bean resolution.
    implementation(project(":fluxion-di"))
    // Adapter SPI: ScriptConfigSubscriber / dynamic script refresh change events.
    implementation(project(":fluxion-config:core"))
    // External function SPI: scripts may invoke other external functions.
    implementation(project(":fluxion-outbound"))

    // Apache Groovy - Groovy language runtime + GroovyClassLoader compilation engine.
    implementation("org.apache.groovy:groovy")
    // Caffeine - high-performance in-memory cache for compiled Groovy Class objects.
    implementation("com.github.ben-manes.caffeine:caffeine")
    // Jackson Databind - script argument / result JSON serialization utilities.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Unified logging extensions.
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // JSON Schema validator used for script output schema validation in tests.
    testImplementation("com.networknt:json-schema-validator")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
