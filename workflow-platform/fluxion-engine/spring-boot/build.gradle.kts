// fluxion-engine:spring-boot - Spring Boot auto-configuration for the core engine module.
// Declares auto-configured beans for all major capability domain components (core, functions,
// decorators, mock, engine, debug) and wires adapter SPI implementations into the ApplicationContext.
// Key plugins: Kotlin JVM + Kotlin Spring plugin (supports @Configuration classes without open).
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
}

dependencies {
    // All primary capability modules are exposed via api so downstream consumers pulling this
    // starter receive transitive compile-time access to their public types.
    api(project(":fluxion-engine"))
    api(project(":fluxion-outbound"))
    api(project(":fluxion-decorator"))
    api(project(":fluxion-mock"))
    api(project(":fluxion-debug"))
    // DI SPI core: FunctionInstanceProvider / DependencyResolver were previously bundled
    // inside fluxion-debug; now they live in their own capability-domain module so we
    // expose them here alongside debug so downstream starters continue to compile.
    api(project(":fluxion-di"))
    // Config core SPI: WorkflowDefinitionSnapshot / FunctionConfigSnapshot / Subscriber contracts
    // moved here from adapter-spi.
    implementation(project(":fluxion-config:core"))
    implementation(project(":fluxion-schema:json"))

    // Spring Boot core starter - provides auto-configuration mechanism and core Spring context.
    implementation("org.springframework.boot:spring-boot-starter")

    // Kotlin coroutines - suspend-enabled component initialization support.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // Unified logging extensions.
    implementation(project(":fluxion-log"))
}