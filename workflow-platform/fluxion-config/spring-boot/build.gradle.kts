// fluxion-config:spring-boot - Spring Boot auto-configuration for config center subsystem.
// Aggregates all config backend implementations (Apollo/Nacos/HTTP) as compileOnly, conditionally
// activates them via @ConditionalOnClass, bridges config events to adapter-spi subscribers.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java`
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    // Adapter SPI: ConfigSubscriber / SchemaConfigSubscriber bridge contracts.
    implementation(project(":fluxion-adapter-spi"))
    // Config core SPI: ConfigCenterProvider + base types.
    implementation(project(":fluxion-config:core"))
    // Core module: JsonUtil utility for declarative YAML/JSON config parsing in auto-config classes.
    implementation(project(":fluxion-core"))

    // Config center backend implementations (compileOnly) - end applications pick exactly one backend.
    compileOnly(project(":fluxion-config:apollo"))
    compileOnly(project(":fluxion-config:nacos"))
    compileOnly(project(":fluxion-config:http"))

    // Registry center backend implementations (compileOnly) - end applications select at runtime.
    compileOnly(project(":fluxion-config:registry-http"))

    // Config center client SDKs (compileOnly) - concrete SDKs are pulled by end-application dependencies.
    compileOnly("com.ctrip.framework.apollo:apollo-client")
    compileOnly("com.ctrip.framework.apollo:apollo-openapi")
    compileOnly("com.alibaba.nacos:nacos-client")

    // Spring framework modules (compileOnly): Spring Boot applications supply these via starters;
    // this module only needs interfaces to detect and bind beans.
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webmvc")

    // Jackson Databind for JSON/YAML configuration parsing.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Unified logging extensions (lazy lambda wrappers over SLF4J).
    implementation(project(":fluxion-log"))
}
