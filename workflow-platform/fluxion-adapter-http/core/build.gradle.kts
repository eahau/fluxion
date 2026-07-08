// fluxion-adapter-http:core - HTTP adapter framework-agnostic core abstractions.
// Defines HttpRequestProcessor, RouteMatch, AbstractRouteRegistry, RouteConfigStore SPI,
// HttpRouteDefinition, and request lifecycle hooks. Zero framework dependencies (pure Kotlin).
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Core base types (zero-framework) - WorkflowContext, node abstractions, JsonUtil.
    api(project(":fluxion-core"))
    // Adapter SPI: UnifiedRequest / WorkflowRouter - HttpRequestProcessor references these.
    api(project(":fluxion-adapter-spi"))

    // Jackson Databind - JSON serialization of RouteDefinition and route registry store values.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Unified logging extensions.
    implementation(project(":fluxion-log"))
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
