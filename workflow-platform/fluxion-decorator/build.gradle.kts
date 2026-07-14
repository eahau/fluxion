// fluxion-decorator - Cross-cutting concern decorator SPI and implementations.
// Provides NodeDecorator abstraction, DecoratorRegistry, and built-in decorators for
// metrics capture (Micrometer), distributed tracing (OpenTelemetry), caching (Spring SpEL keys),
// async dispatch, and async execution wrapping.
// Key plugins: Kotlin JVM + java-library (SPI types exposed as api to decorator impl modules).
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Core SPI abstractions: NodeDecorator, DecoratorRegistry, WorkflowMetrics, TaskInterceptor.
    api(project(":fluxion-core"))
    // Function SPI types: FunctionComponent reference for decorator execution context.
    api(project(":fluxion-function"))

    // Micrometer Core API - powers MetricsDecorator and MicrometerWorkflowMetrics impl.
    // implementation scope prevents leaking Micrometer API onto downstream compile classpath.
    implementation("io.micrometer:micrometer-core")
    // OpenTelemetry API - powers TraceDecorator for distributed span propagation across nodes.
    implementation("io.opentelemetry:opentelemetry-api")
    // Spring Expression Language - standalone SpEL parser for CacheDecorator key expressions (zero Spring context).
    implementation("org.springframework:spring-expression")
    // Unified logging extensions for AsyncDecorator diagnostic output.
    implementation(project(":fluxion-log"))
    implementation(project(":fluxion-redis:core"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation(project(":fluxion-test"))
}

// Explicit source set registration for build clarity.
sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
