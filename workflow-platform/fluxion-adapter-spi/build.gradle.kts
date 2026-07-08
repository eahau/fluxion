// fluxion-adapter-spi - Adapter Service Provider Interface (SPI) abstraction module.
// Defines core contracts for protocol adapters: UnifiedRequest, WorkflowRouter, DefinitionProvider,
// configuration subscribers, schema change listeners, and capability-domain marker interfaces.
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Core base types - WorkflowContext, NodeInput, JsonUtil shared abstractions.
    api(project(":fluxion-core"))
    // SLF4J API for adapter diagnostic logging (consumers provide actual binding).
    implementation("org.slf4j:slf4j-api")
    // Jackson Databind - request/response JSON serialization contracts for adapter SPI.
    implementation("com.fasterxml.jackson.core:jackson-databind")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
