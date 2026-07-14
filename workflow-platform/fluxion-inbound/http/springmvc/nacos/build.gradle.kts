// fluxion-inbound:http:springmvc:nacos - Nacos-backed dynamic route configuration store.
// Implements RouteConfigStore SPI using Nacos Config SDK (non-Spring Cloud) for runtime
// HTTP route registration without requiring Nacos config to be wired through Spring Cloud.
plugins {
    kotlin("jvm")
}

dependencies {
    // HTTP adapter core: RouteConfigStore SPI interface + HttpRouteDefinition types.
    implementation(project(":fluxion-inbound:http:core"))

    // Nacos Config Client SDK (pure SDK, zero Spring Cloud dependency footprint).
    implementation("com.alibaba.nacos:nacos-client")

    // Jackson Kotlin module + Kotlin reflection for JSON deserialization of route definitions.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // SLF4J logging facade (Nacos client and route store diagnostic logs).
    implementation("org.slf4j:slf4j-api")
}
