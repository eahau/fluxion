// fluxion-runtime - Workflow runtime execution plane (Spring Boot deployable application).
// Full-featured runtime sidecar/executable assembling all capability domains (HTTP/RPC/MQ adapters,
// script engine, external function transports, decorators, DI bridge, config backends) into a
// single Spring Boot application.
// Key plugins: Spring Boot application plugin (bootJar/bootRun), Kotlin + Spring plugin.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // Runtime Spring Boot auto-configuration layer.
    implementation(project(":fluxion-runtime:spring-boot"))

    // ===== Protocol Adapters =====
    // HTTP (Spring MVC) adapter Spring Boot starter (required default adapter).
    implementation(project(":fluxion-adapter-http:springmvc:spring-boot"))
    // RPC adapter Spring Boot starter (Dubbo + gRPC, compiled in; runtime activated conditionally).
    implementation(project(":fluxion-adapter-rpc:spring-boot"))
    // MQ adapter Spring Boot starter (Kafka-backed).
    implementation(project(":fluxion-adapter-mq:spring-boot"))

    // ===== Function Capability Domain =====
    // Built-in + external function registry Spring Boot starter.
    implementation(project(":fluxion-function:spring-boot"))
    // Groovy script engine Spring Boot starter (dynamic script evaluation).
    implementation(project(":fluxion-script-engine:spring-boot"))
    // External function transport Spring Boot starters (Dubbo / gRPC / HTTP).
    implementation(project(":fluxion-function:external:dubbo:spring-boot"))
    implementation(project(":fluxion-function:external:grpc:spring-boot"))
    implementation(project(":fluxion-function:external:http:spring-boot"))

    // ===== Cross-cutting Capability Domain =====
    // Decorator Spring Boot starter (metrics/tracing/caching decorators).
    implementation(project(":fluxion-decorator:spring-boot"))

    // ===== Dependency Injection Bridge =====
    // Spring Framework-based DI bridge (ApplicationContext -> FunctionInstanceProvider).
    implementation(project(":fluxion-di:spring"))

    // ===== Logging =====
    // Unified logging extensions module.
    implementation(project(":fluxion-log"))

    // ===== Configuration / Registry =====
    // Config center Spring Boot auto-configuration.
    implementation(project(":fluxion-config:spring-boot"))
    // HTTP bootstrap config backend (default: workflow.config.type=http).
    implementation(project(":fluxion-config:http"))
    // HTTP-based lightweight service registry (bootstrap mode default).
    implementation(project(":fluxion-config:registry-http"))

    // ===== Spring Boot Starters =====
    // DevTools for live-reload during local development.
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    // Spring MVC (Servlet stack) for default HTTP adapter.
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Log4j2 logging implementation (root build excludes Logback globally).
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    // Jackson YAML dataformat for Log4j2 YAML configuration file parsing.
    runtimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    // Actuator endpoints: health, metrics, info, loggers etc.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // JSR-303 validation (Hibernate Validator engine) for config/request validation.
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // ===== Testing Dependencies =====
    // Spring Boot Test starter (integration test context bootstrap, MockMvc, TestRestTemplate).
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Local development bootRun customization: JDWP remote debugging on port 5005 when the
// `-Pdebug` Gradle project property is provided.
tasks.bootRun {
    if (project.hasProperty("debug")) {
        jvmArgs = listOf(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
        )
    }
}
