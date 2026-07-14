// fluxion-inbound:rpc:dubbo - Apache Dubbo RPC protocol adapter implementation (zero Spring).
// Exports and consumes workflow functions via Dubbo GenericService generic invocation interface,
// enabling cross-language RPC without compiled service interface classes at build time.
plugins {
    kotlin("jvm")
}

dependencies {
    // Core base types.
    implementation(project(":fluxion-core"))
    implementation(project(":fluxion-inbound:spi"))
    // Apache Dubbo core framework - GenericService generic invocation interface.
    // Excludes netty-all aggregator JAR (28 sub-modules) since this module never imports Netty APIs.
    implementation("org.apache.dubbo:dubbo") {
        exclude(group = "io.netty", module = "netty-all")
    }

    // Jackson Kotlin + Kotlin reflection for JSON serialization of RPC payloads outside Hessian2.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // SLF4J logging.
    implementation("org.slf4j:slf4j-api")
}

