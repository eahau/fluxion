// fluxion-starters:grpc-spring-boot-starter - Convenience aggregator starter for gRPC.
// Combines the gRPC inbound adapter (server-side code generated from proto, dynamic
// service registration) with the gRPC outbound external-function transport (managed
// channels + DynamicMessage routing). The shared rpc:spring-boot auto-configuration is
// pulled in transitively by the inbound implementation, but we declare it explicitly for
// readability and robustness against implementation-module dependency-graph changes.
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Inbound gRPC adapter implementation (server).
    api(project(":fluxion-inbound:rpc:grpc"))
    // Shared RPC inbound auto-configuration (gRPC + Dubbo share this starter).
    api(project(":fluxion-inbound:rpc:spring-boot"))
    // Outbound gRPC external-function transport auto-configuration.
    api(project(":fluxion-outbound:grpc:spring-boot"))
}
