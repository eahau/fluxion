// fluxion-runtime:spring-boot - Spring Boot auto-configuration for runtime execution plane.
// Wires runtime core beans into the ApplicationContext, bridges config center + adapter SPI
// lifecycle events, and assembles all capability domain spring-boot starters for the runtime.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
}

dependencies {
    // Runtime core (api - downstream consumers get transitive DAG executor types).
    api(project(":fluxion-runtime:core"))
    // Core engine Spring Boot starter (assembles all capability domain beans).
    implementation(project(":fluxion-core:spring-boot"))
    // Adapter SPI (api - downstream consumers get router / definition provider types transitively).
    api(project(":fluxion-inbound:spi"))
    // HTTP adapter core (RouteConfigStore, HttpRouteDefinition, RouteChangeListener).
    api(project(":fluxion-inbound:http:core"))
    // Config core (api - provides DefinitionConfigSubscriber, WorkflowDefinitionSnapshot, ChangeType).
    api(project(":fluxion-config:core"))
    // Registry core (api - provides InstanceRegistry, InstanceDiscovery, InstanceInfo).
    api(project(":fluxion-registry:core"))
    // Config center Spring Boot starter (runtimeOnly - pulled in automatically but not required for compile).
    runtimeOnly(project(":fluxion-config:spring-boot"))

    // Spring Boot AutoConfigure mechanism + conditional bean registration.
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Jackson Kotlin module + Kotlin reflection for runtime DTO handling.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Unified logging extensions.
    implementation(project(":fluxion-log"))
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
