// fluxion-outbound:grpc:spring-boot - Spring Boot auto-config for gRPC external function transport.
// Auto-registers gRPC ExternalFunctionTransport bean (conditionally on AbstractStub class presence)
// so consuming Spring Boot applications automatically enable gRPC remote function calls.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
}

dependencies {
    // gRPC external transport implementation (api - consumers receive transitive gRPC types).
    api(project(":fluxion-outbound:grpc"))
    // External function core SPI: ExternalFunctionTransport abstraction.
    implementation(project(":fluxion-outbound"))

    // Spring Boot AutoConfigure mechanism + @ConditionalOnClass bean guards.
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    // Unified logging.
    implementation(project(":fluxion-log"))

    // gRPC Stub (compileOnly) for @ConditionalOnClass(AbstractStub::class) classpath detection guard;
    // concrete stub impl supplied at runtime by the core module's dependencies.
    compileOnly("io.grpc:grpc-stub")

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}
