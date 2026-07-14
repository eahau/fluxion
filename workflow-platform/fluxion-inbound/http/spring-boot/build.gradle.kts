// fluxion-inbound:http:spring-boot - Spring Boot auto-configuration for Spring MVC HTTP adapter.
// Assembles all Spring MVC adapter components, Apollo/Nacos route stores (compileOnly), and
// registers handler mapping beans conditionally on application environment.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // HTTP adapter core abstractions.
    implementation(project(":fluxion-inbound:http:core"))
    // Spring MVC adapter implementation (handler registry + interceptor chain).
    implementation(project(":fluxion-inbound:http:springmvc"))
    // Inbound SPI: InboundRouter, UnifiedRequest, UnifiedResponse.
    implementation(project(":fluxion-inbound:spi"))

    // Apollo / Nacos route storage backends - compileOnly so final applications can pick which
    // storage backend they need without pulling both Apollo and Nacos SDKs unnecessarily.
    compileOnly(project(":fluxion-inbound:http:springmvc:apollo"))
    compileOnly(project(":fluxion-inbound:http:springmvc:nacos"))

    // Spring Boot AutoConfigure + Spring Web MVC starter (auto-registered handler mappings).
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Apollo / Nacos Client SDKs (compileOnly) - concrete implementations provided by end applications.
    compileOnly("com.ctrip.framework.apollo:apollo-client")
    compileOnly("com.alibaba.nacos:nacos-client")

    // Jackson Kotlin module + Kotlin reflection for dynamic DTO handling.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // SLF4J logging.
    implementation("org.slf4j:slf4j-api")

    // Spring Boot Test starter for integration testing.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
