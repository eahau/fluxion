// fluxion-function:builtin - Built-in workflow function implementations.
// Provides database execution (JDBC DataSource backed), HTTP call, cache operations,
// JSON manipulation, workflow orchestration, and utility functions. Requires Spring context
// for DataSource injection and transaction support.
// Key plugins: Kotlin JVM + Kotlin Spring plugin + java-library.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
}

dependencies {
    // Core base types: JsonUtil, shared abstractions.
    api(project(":fluxion-core"))
    // Function abstraction SPI.
    implementation(project(":fluxion-function"))
    // Decorator SPI: decorator interception for built-in functions.
    implementation(project(":fluxion-decorator"))
    // DAG Engine: required for workflow orchestration built-in functions.
    implementation(project(":fluxion-engine"))
    // Adapter SPI: WorkflowFunctionMeta + FunctionMetaManager types (api - downstream workers use it).
    api(project(":fluxion-adapter-spi"))
    // Function Spring Boot meta: FunctionMetaManager/FunctionMetaRegistry SPI.
    api(project(":fluxion-function:spring-boot"))

    // Schema parsing capabilities (pure core + JSON, zero Spring required at runtime).
    implementation(project(":fluxion-schema:core"))
    implementation(project(":fluxion-schema:json"))

    // Spring Boot AutoConfigure: @AutoConfiguration / @ConditionalOnBean for function beans.
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    // Spring Transaction JDBC: SpringTransactionManager + JdbcTemplate for DbExecute functions.
    implementation("org.springframework:spring-tx")
    implementation("org.springframework:spring-jdbc")

    // OkHttp client for built-in HTTP function call implementation.
    implementation("com.squareup.okhttp3:okhttp")
    // Jayway JsonPath for JSON payload manipulation in built-in functions.
    implementation("com.jayway.jsonpath:json-path")
    // Jackson serialization + Kotlin reflection for dynamic JSON/DTO processing.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Caffeine local cache backing CacheStore default implementation.
    implementation("com.github.ben-manes.caffeine:caffeine")
    // Unified logging.
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}
