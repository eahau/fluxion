dependencies {
    // workflow-decorator-impl-core（所有装饰器实现，传递依赖 workflow-core）
    api(project(":fluxion-decorator-impl:core"))

    // Spring Boot AutoConfigure（@AutoConfiguration / @ConditionalOnBean）
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Micrometer Core API — MetricsDecorator + MeterRegistry
    compileOnly("io.micrometer:micrometer-core")

    // OpenTelemetry API — TraceDecorator + Tracer
    compileOnly("io.opentelemetry:opentelemetry-api")

    // SLF4J
    implementation("org.slf4j:slf4j-api")
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}
