// fluxion-script-engine:spring-boot - Spring Boot auto-configuration for script engine module.
// Auto-registers Groovy ScriptEngine, ScriptRegistry bean, configuration subscribers, and
// wires Spring-backed DependencyResolver into script execution context.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // Core base types.
    implementation(project(":fluxion-core"))
    // Script engine core module (api - downstream consumers receive ScriptComponent types transitively).
    api(project(":fluxion-script-engine:core"))

    // Debug module (api - downstream consumers get DI resolver types).
    api(project(":fluxion-debug"))
    // Adapter SPI: ScriptConfigSubscriber + dynamic script change events.
    implementation(project(":fluxion-adapter-spi"))
    // Function SPI: WorkflowFunction + FunctionComponent base types.
    implementation(project(":fluxion-function"))
    // External function SPI (scripts may call remote functions).
    implementation(project(":fluxion-function:external"))

    // Spring Boot AutoConfigure mechanism.
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Jackson Kotlin module + Kotlin reflection for script parameter DTO handling.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // SLF4J logging.
    implementation("org.slf4j:slf4j-api")

    // Spring Boot Test starter for script engine integration tests.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
