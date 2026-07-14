// fluxion-outbound:dubbo - Apache Dubbo-based external function transport implementation.
// Implements ExternalFunctionTransport SPI using Dubbo GenericService for generic invocation
// of remote Dubbo services without requiring compiled service interface classes at build time.
plugins {
    kotlin("jvm")
}

dependencies {
    // External function SPI: ExternalFunctionTransport abstraction + ServiceLoader registry.
    implementation(project(":fluxion-outbound"))

    // Apache Dubbo core framework - GenericService, RPC invocation, registry abstraction.
    // Excludes netty-all aggregator JAR (28 sub-modules in one JAR) as this module never imports Netty directly.
    implementation("org.apache.dubbo:dubbo") {
        exclude(group = "io.netty", module = "netty-all")
    }

    // Jackson + Kotlin module for JSON auxiliary message encoding outside Dubbo native Hessian2.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Kotlin reflection for dynamic class mapping across generic service calls.
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Unified logging.
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.apache.dubbo:dubbo-qos")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}
