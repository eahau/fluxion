// fluxion-starters:dubbo-spring-boot-starter - Convenience aggregator starter for Apache Dubbo.
// Pulls in the Dubbo inbound service exposure side (ReferenceConfig/ServiceConfig management)
// along with the Dubbo outbound external-function transport so workers can both serve and
// call Dubbo services without juggling multiple starter coordinates.
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Inbound Dubbo adapter implementation (server).
    api(project(":fluxion-inbound:rpc:dubbo"))
    // Shared RPC inbound auto-configuration (gRPC + Dubbo share this starter).
    api(project(":fluxion-inbound:rpc:spring-boot"))
    // Outbound Dubbo external-function transport auto-configuration.
    api(project(":fluxion-outbound:dubbo:spring-boot"))
}
