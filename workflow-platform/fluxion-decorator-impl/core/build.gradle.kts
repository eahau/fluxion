dependencies {
    // workflow-core（NodeDecorator SPI / DecoratorRegistry / WorkflowMetrics 接口）
    // api: spring-boot 装配层需要访问 core 中的 AsyncCallback / CacheStore / DecoratorRegistry 等类型
    api(project(":fluxion-core"))

    // Micrometer Core API — MetricsDecorator + MicrometerWorkflowMetrics
    api("io.micrometer:micrometer-core")

    // OpenTelemetry API — TraceDecorator
    api("io.opentelemetry:opentelemetry-api")

    // Spring Expression（仅作纯表达式库）— CacheDecorator SpEL key 表达式
    implementation("org.springframework:spring-expression")

    // SLF4J — AsyncDecorator 日志
    implementation("org.slf4j:slf4j-api")

    // JUnit 5 — 装饰器单元测试
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")

    // 测试 fixtures
    testImplementation(project(":fluxion-test"))
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}
