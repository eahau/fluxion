// fluxion-function:external:dubbo:spring-boot - Spring Boot auto-config for Dubbo external function transport.
// Auto-registers Dubbo ExternalFunctionTransport bean conditionally when GenericService class
// is present on the classpath, enabling zero-config Dubbo remote function invocation in Spring Boot apps.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
}

dependencies {
    // Dubbo external transport implementation (api - consumers receive transitive Dubbo types).
    api(project(":fluxion-function:external:dubbo"))
    // External function core SPI: ExternalFunctionTransport abstraction.
    implementation(project(":fluxion-function:external"))

    // Spring Boot AutoConfigure mechanism + @ConditionalOnClass bean guards.
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    // Unified logging.
    implementation(project(":fluxion-log"))

    // Dubbo core (compileOnly) for @ConditionalOnClass(GenericService::class) classpath detection;
    // excludes netty-all aggregator JAR as this auto-config never uses Netty APIs directly.
    compileOnly("org.apache.dubbo:dubbo") {
        exclude(group = "io.netty", module = "netty-all")
    }

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}
