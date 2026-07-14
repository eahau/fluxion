// fluxion-outbound:http:spring-boot - Spring Boot auto-configuration for HTTP external function transport.
// Auto-registers HTTP ExternalFunctionTransport bean into ApplicationContext so consuming
// Spring Boot apps gain remote HTTP function invocation capability by simply adding this starter.
// Key plugins: Kotlin JVM + Kotlin Spring plugin + java-library (api exposes HTTP transport).
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
}

dependencies {
    // HTTP external transport implementation (api - consumers receive HTTP transport types transitively).
    api(project(":fluxion-outbound:http"))
    // External function core SPI: ExternalFunctionTransport abstraction lives here.
    implementation(project(":fluxion-outbound"))

    // Spring Boot AutoConfigure: auto-register transport bean with @ConditionalOnClass.
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    // Unified logging.
    implementation(project(":fluxion-log"))

    // HttpClientAdapter compileOnly for @ConditionalOnClass(HttpClientAdapter::class) guard;
    // concrete impl supplied at runtime by the core module's classpath.
    compileOnly(project(":fluxion-function:builtin"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}
