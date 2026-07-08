// fluxion-decorator:spring-boot - Spring Boot auto-configuration for decorator module.
// Registers all built-in decorator beans (MetricsDecorator, TraceDecorator, CacheDecorator,
// AsyncDecorator) into the ApplicationContext conditionally based on classpath presence.
// Key plugins: Kotlin JVM + Kotlin Spring plugin (@Configuration/@AutoConfiguration support).
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
}

dependencies {
    // Core decorator module: all SPI + implementation types transitively exposed via api.
    api(project(":fluxion-decorator"))

    // Spring Boot AutoConfigure module - provides @AutoConfiguration and @ConditionalOn* annotations.
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    // Micrometer API (compileOnly) - MetricsDecorator + MeterRegistry are only required at compile time;
    // concrete Micrometer impl is supplied by the consuming Spring Boot application.
    compileOnly("io.micrometer:micrometer-core")
    // OpenTelemetry API (compileOnly) - TraceDecorator + Tracer; concrete exporter supplied by app.
    compileOnly("io.opentelemetry:opentelemetry-api")
    // Unified logging extensions.
    implementation(project(":fluxion-log"))
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
