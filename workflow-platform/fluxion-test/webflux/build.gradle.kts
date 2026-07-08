// fluxion-test:webflux - WebFlux-based test application for reactive HTTP adapter validation.
// Spring Boot executable using WebFlux stack to exercise fluxion-adapter-http:webflux adapter,
// with all runtime components auto-wired through individual spring-boot starters.
// Key plugins: Spring Boot application plugin (runnable), Kotlin JVM + Spring plugin.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // ===== Internal Fluxion Modules =====
    // Runtime core engine: framework-agnostic DAG executor + workflow orchestration.
    implementation(project(":fluxion-runtime:core"))
    // Runtime Spring Boot auto-configuration layer for runtime beans assembly.
    implementation(project(":fluxion-runtime:spring-boot"))
    // Core engine Spring Boot starter (capability domain beans registration).
    implementation(project(":fluxion-core:spring-boot"))
    // Adapter SPI: router / subscriber / definition provider interfaces.
    implementation(project(":fluxion-adapter-spi"))
    // HTTP adapter core abstractions (RouteMatch, HttpRequestProcessor, AbstractRouteRegistry).
    implementation(project(":fluxion-adapter-http:core"))
    // WebFlux reactive HTTP adapter implementation (RouterFunction-based).
    implementation(project(":fluxion-adapter-http:webflux"))
    // Built-in workflow functions + registry auto-configuration.
    implementation(project(":fluxion-function:spring-boot"))
    // Spring DI bridge: ApplicationContext-backed FunctionInstanceProvider.
    implementation(project(":fluxion-di:spring"))
    // Groovy script engine Spring Boot starter + dynamic evaluation.
    implementation(project(":fluxion-script-engine:spring-boot"))
    // Cross-cutting decorator Spring Boot starter (metrics/tracing/cache beans).
    implementation(project(":fluxion-decorator:spring-boot"))
    // Config center Spring Boot auto-configuration.
    implementation(project(":fluxion-config:spring-boot"))
    // HTTP bootstrap config backend (activated when workflow.config.type=http).
    implementation(project(":fluxion-config:http"))
    // Nacos config backend (activated when workflow.config.type=nacos).
    implementation(project(":fluxion-config:nacos"))
    // HTTP-based service registry (bootstrap mode discovery).
    implementation(project(":fluxion-config:registry-http"))
    // Nacos route configuration store (reuses springmvc:nacos implementation for shared Nacos storage).
    implementation(project(":fluxion-adapter-http:springmvc:nacos"))
    // Nacos Client SDK - required for Nacos mode runtime operation.
    implementation("com.alibaba.nacos:nacos-client")

    // ===== Spring Boot Starters =====
    // DevTools for live-reload during local test-application development.
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    // Spring WebFlux (reactive web server) - RouterFunction-based request handling.
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    // Log4j2 logging implementation (root build.gradle.kts excludes default Logback globally).
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    // Jackson YAML dataformat for Log4j2 YAML configuration file parsing.
    runtimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    // Actuator endpoints: health, metrics, info for the WebFlux test application.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // JSR-303 validation (Hibernate Validator engine).
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kotlin Coroutines reactive bridge: mono { } / flux { } coroutine builder support.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // ===== Testing Dependencies =====
    // Spring Boot Test starter (integration test context bootstrap, WebTestClient).
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Local development bootRun customization: JDWP remote debugging on port 5006 (distinct from
// fluxion-admin port 5005) when the `-Pdebug` Gradle project property is provided.
tasks.bootRun {
    if (project.hasProperty("debug")) {
        jvmArgs = listOf(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5006"
        )
    }
}
