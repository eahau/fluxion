// fluxion-starters:http-spring-boot-starter - Convenience aggregator starter for HTTP.
// Pulls in both the inbound HTTP adapter auto-configuration (Spring MVC + WebFlux +
// dynamic route config storage) and the outbound HTTP external-function transport, so
// end users only need a single dependency declaration instead of combining two starters.
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Inbound HTTP adapter Spring Boot auto-configuration.
    api(project(":fluxion-inbound:http:spring-boot"))
    // Outbound HTTP external-function transport auto-configuration.
    api(project(":fluxion-outbound:http:spring-boot"))
}
