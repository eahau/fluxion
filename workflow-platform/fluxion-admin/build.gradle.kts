// fluxion-admin - Admin management console executable Spring Boot application.
// Provides workflow management REST API, JPA persistence, OpenAPI-generated DTOs,
// Spring Security authentication, JWT token handling, and admin UI serving capabilities.
// Key plugins: Spring Boot (application), Kotlin + Spring/JPA plugins, OpenAPI Generator (kotlin-spring).
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.openapi.generator")
}

// Disable Spring Dependency Management auto-applied Maven exclusions to avoid conflicts
// with Spring Boot bootJar runtime classpath resolution (prevent false-positive exclusions).
the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().applyMavenExclusions(false)

// ===== OpenAPI Generator Configuration =====
// Generates Kotlin DTO model classes and Spring @RestController interface stubs from ../doc/openapi.yaml.
// Output sources are registered under build/generated/openapi and wired to compileKotlin source set.
val openApiInputFile = file("${rootProject.projectDir}/../doc/openapi.yaml")
val openApiOutputDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set(openApiInputFile.absolutePath)
    outputDir.set(openApiOutputDir.get().asFile.absolutePath)
    validateSpec.set(false)
    apiPackage.set("com.fluxion.admin.generated.api")
    modelPackage.set("com.fluxion.admin.generated.model")
    packageName.set("com.fluxion.admin.generated")
    configOptions.set(mapOf(
        "interfaceOnly" to "true",
        "skipDefaultInterface" to "false",
        "useTags" to "true",
        "dateLibrary" to "java8",
        "useSpringBoot3" to "true",
        "documentationProvider" to "none",
        "enumPropertyNaming" to "UPPERCASE",
        "serializableModel" to "true",
        "openApiNullable" to "false",
        "modelMutable" to "true"
    ))
    globalProperties.set(mapOf(
        "models" to "",
        "apis" to ""
    ))
}

// ===== OpenAPI Post-Processing Hook =====
// kotlin-spring generator only emits @JsonProperty(required=true) for required fields without
// injecting JSR-303 @NotNull. This task patches generated model classes to add @field:NotNull
// annotations on every required property after openApiGenerate completes.
tasks.named("openApiGenerate") {
    doLast {
        val modelDir = openApiOutputDir.get().asFile.resolve("src/main/kotlin")
            .resolve("com/fluxion/admin/generated/model")
        if (!modelDir.exists()) return@doLast
        modelDir.listFiles { f -> f.extension == "kt" }?.forEach { file ->
            var text = file.readText()
            text = text.replace(Regex("""\s*@field:NotNull\n"""), "\n")
            text = text.replace(
                Regex("""(\s*)(@get:JsonProperty\("[^"]+", required = true\))"""),
                "$1@field:NotNull\n$1$2"
            )
            file.writeText(text)
        }
    }
}

// Register generated Kotlin sources on the main source set and establish task ordering so
// openApiGenerate completes before compileKotlin / compileJava attempt to consume sources.
tasks.named("compileKotlin") {
    dependsOn("openApiGenerate")
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}

sourceSets {
    main {
        kotlin {
            srcDir(openApiOutputDir.map { it.dir("src/main/kotlin") })
        }
    }
}

dependencies {
    // ===== Internal Fluxion Modules =====
    // ACL SPI - permission/role/auth interfaces for admin security layer.
    implementation(project(":fluxion-acl-spi"))
    // Core foundation types and base abstractions (api for transitive exposure).
    implementation(project(":fluxion-core"))
    // Spring Boot auto-configuration for core engine beans.
    implementation(project(":fluxion-core:spring-boot"))
    // Schema core abstractions (NodeInput/FunctionResult schema contracts).
    implementation(project(":fluxion-schema:core"))
    // Spring Boot auto-configuration for schema registry + subscribers.
    implementation(project(":fluxion-schema:spring-boot"))
    // Spring Boot auto-configuration for cross-cutting decorators (metrics/tracing/cache).
    implementation(project(":fluxion-decorator:spring-boot"))
    // Config core (DefinitionConfigSubscriber, etc.).
    implementation(project(":fluxion-config:core"))
    // Registry core (InstanceRegistry, InstanceInfo, PublishTarget).
    implementation(project(":fluxion-registry:core"))
    // Registry Spring Boot auto-configuration (SpringCloudInstanceRegistry).
    implementation(project(":fluxion-registry:spring-boot"))
    // Discovery core (InstanceDiscovery).
    implementation(project(":fluxion-discovery:core"))
    // Discovery Spring Boot auto-configuration (SpringCloudInstanceDiscovery).
    implementation(project(":fluxion-discovery:spring-boot"))
    // Inbound SPI (InboundRouter, UnifiedRequest, UnifiedResponse).
    implementation(project(":fluxion-inbound:spi"))
    // Runtime core - DAG executor and workflow orchestration primitives.
    implementation(project(":fluxion-runtime:core"))
    // HTTP adapter core abstractions (request processing, route registry SPI).
    implementation(project(":fluxion-inbound:http:core"))
    // Spring MVC HTTP adapter Spring Boot starter (full auto-configuration).
    implementation(project(":fluxion-inbound:http:springmvc:spring-boot"))
    // Built-in workflow functions + Spring Boot function registry auto-configuration.
    implementation(project(":fluxion-function:spring-boot"))
    // Built-in function implementations (DbExecuteWorkflowCompiler, DataSourceProvider).
    implementation(project(":fluxion-function:builtin"))
    // Spring DI bridge - ApplicationContext-backed FunctionInstanceProvider.
    implementation(project(":fluxion-di:spring"))
    // Redis capability domain Spring Boot auto-configuration + client adapter SPI.
    implementation(project(":fluxion-redis:spring-boot"))
    // Lettuce Redis client adapter implementation.
    implementation(project(":fluxion-redis:lettuce"))
    // Groovy script engine Spring Boot auto-configuration + dynamic evaluation.
    implementation(project(":fluxion-script:spring-boot"))
    // Config center core.
    implementation(project(":fluxion-config:core"))
    // Debug module - DebugService and DebugSnapshot for admin debugger UI.
    implementation(project(":fluxion-debug"))
    // Outbound module - OutboundTransportRegistry for external function management.
    implementation(project(":fluxion-outbound"))
    // Config center Spring Boot auto-configuration (binds Apollo/Nacos/HTTP backends).
    implementation(project(":fluxion-config:spring-boot"))
    // HTTP bootstrap config backend - activated when workflow.config.type=http.
    implementation(project(":fluxion-config:http"))
    // Nacos config backend - activated when workflow.config.type=nacos.
    implementation(project(":fluxion-config:nacos"))
    // Spring Cloud Nacos Discovery for service registration and discovery.
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery")
    // Nacos Client SDK on runtime classpath when running in Nacos configuration mode.
    runtimeOnly("com.alibaba.nacos:nacos-client")

    // ===== Spring Boot Starters (Application Foundation) =====
    // DevTools for live-reload during local development.
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    // Spring MVC (Servlet stack) web server.
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Log4j2 logging implementation (root build.gradle.kts excludes default Logback globally).
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    // Jackson YAML dataformat for Log4j2 YAML configuration file parsing.
    runtimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    // Spring Data JPA with Hibernate ORM - entity mapping excludes glassfish JAXB runtime (annotation-only mode).
    implementation("org.springframework.boot:spring-boot-starter-data-jpa") {
        exclude(group = "org.glassfish.jaxb", module = "jaxb-runtime")
    }
    // Spring Security - authentication filter chain + method-level authorization.
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Actuator - /actuator/health, /actuator/metrics, /actuator/prometheus endpoints.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // JSR-303 validation (@NotNull/@Valid/@Size Hibernate Validator engine).
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Spring Cache abstraction (Caffeine-backed local cache default impl).
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // ===== Observability / Distributed Tracing =====
    // Micrometer-to-OpenTelemetry tracing bridge (propagates trace IDs through workflow calls).
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    // OTLP HTTP span exporter - sends traces to Jaeger/OpenTelemetry Collector at localhost:4318.
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // ===== Compile-Only Annotation Libraries =====
    // JSR-305 nullability annotations (eliminate kapt strict-mode compiler warnings).
    compileOnly("com.google.code.findbugs:jsr305")

    // ===== Kotlin Coroutines =====
    // Core coroutine primitives (suspend function support, Dispatchers, async/launch).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // ===== Persistence & Database =====
    // Flyway - declarative database migration scripts executed at application startup.
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    // MySQL Connector/J - runtime JDBC driver for MySQL 8.x databases.
    runtimeOnly("com.mysql:mysql-connector-j")

    // ===== JWT Authentication =====
    // JJWT API (compile-safe interfaces) + runtime impl + Jackson serialization module.
    implementation("io.jsonwebtoken:jjwt-api")
    runtimeOnly("io.jsonwebtoken:jjwt-impl")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson")

    // ===== Encryption =====
    // Jasypt string encryption library for sensitive config encryption.
    implementation("org.jasypt:jasypt:1.9.3")

    // ===== Testing =====
    // Spring Boot Test starter (integration test context bootstrap, @SpringBootTest).
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Handle duplicate JAR entries during bootJar fat-JAR packaging. Multiple submodules share identical
// "core" artifact coordinates (e.g., fluxion-schema:core, fluxion-adapter-http:core) which would
// otherwise cause Gradle duplicate-strategy failures; EXCLUDE retains first-encountered JAR.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Local development bootRun customization. Enables JDWP remote debugging on port 5005 when the
// `-Pdebug` Gradle project property is provided, and disables the DevTools restart classloader
// to prevent web container / port-binding state loss across hot-reload fork cycles.
tasks.bootRun {
    val baseJvmArgs = mutableListOf<String>()
    baseJvmArgs += "-Dspring.devtools.restart.enabled=false"
    baseJvmArgs += "-Dspring.profiles.active=local,nacos"
    if (project.hasProperty("debug")) {
        baseJvmArgs += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
    }
    if (baseJvmArgs.isNotEmpty()) {
        jvmArgs = baseJvmArgs
    }
}
