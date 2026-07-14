// fluxion-inbound:http:springmvc:apollo - Apollo-backed dynamic route configuration store.
// Implements RouteConfigStore SPI using Ctrip Apollo Config Client SDK for runtime
// HTTP route registration via Apollo namespaces and configuration change listeners.
plugins {
    kotlin("jvm")
}

dependencies {
    // HTTP adapter core: RouteConfigStore SPI interface + HttpRouteDefinition types.
    implementation(project(":fluxion-inbound:http:core"))
    // Ctrip Apollo Client SDK - config center API + change subscription mechanism.
    implementation("com.ctrip.framework.apollo:apollo-client")

    // Jackson Kotlin module + Kotlin reflect for route definition JSON deserialization.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // SLF4J logging facade.
    implementation("org.slf4j:slf4j-api")
}
