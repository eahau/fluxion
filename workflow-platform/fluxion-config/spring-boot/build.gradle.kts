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
    implementation(project(":fluxion-config:core"))
    implementation(project(":fluxion-registry:core"))
    implementation(project(":fluxion-registry:spring-boot"))
    implementation(project(":fluxion-discovery:core"))
    implementation(project(":fluxion-discovery:spring-boot"))
    implementation(project(":fluxion-core"))

    compileOnly(project(":fluxion-config:apollo"))
    compileOnly(project(":fluxion-config:nacos"))
    compileOnly(project(":fluxion-config:http"))

    compileOnly("com.ctrip.framework.apollo:apollo-client")
    compileOnly("com.ctrip.framework.apollo:apollo-openapi")
    compileOnly("com.alibaba.nacos:nacos-client")

    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webmvc")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation(project(":fluxion-log"))
}
