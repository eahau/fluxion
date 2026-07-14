// fluxion-inbound:rpc:spring-boot - Spring Boot auto-configuration for RPC adapter suite.
// Wires up protocol-agnostic RPC auto-configuration and conditionally activates Dubbo/gRPC
// implementations based on classpath presence (compileOnly dependencies).
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // Core base types.
    implementation(project(":fluxion-core"))
    // Adapter SPI (InboundRouter, request/response abstractions).
    implementation(project(":fluxion-inbound:spi"))
    // Schema core (compileOnly; optional for generic RPC adapters that don't validate schemas).
    compileOnly(project(":fluxion-schema:core"))

    // RPC protocol implementations (compileOnly) - end applications choose which RPC tech to use.
    compileOnly(project(":fluxion-inbound:rpc:dubbo"))
    compileOnly(project(":fluxion-inbound:rpc:grpc"))

    // Dubbo core (compileOnly): required for @ConditionalOnClass(GenericService::class) classpath check;
    // excludes netty-all aggregator JAR as this module never directly references Netty APIs.
    compileOnly("org.apache.dubbo:dubbo") {
        exclude(group = "io.netty", module = "netty-all")
    }

    // net.devh gRPC Spring Boot Starter (compileOnly): excludes transport + auto-configure artifacts
    // to keep classpath minimal - only gRPC annotation classes (GrpcService etc.) are needed.
    compileOnly("net.devh:grpc-server-spring-boot-starter") {
        exclude(group = "io.grpc")
        exclude(group = "net.devh", module = "grpc-server-spring-boot-autoconfigure")
    }
    compileOnly("io.grpc:grpc-stub")
    compileOnly("io.grpc:grpc-protobuf")

    // Jackson Databind + Kotlin reflection for generic RPC message serialization.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Unified logging extensions (lazy lambda loggers).
    implementation(project(":fluxion-log"))
    // SLF4J logging.
    implementation("org.slf4j:slf4j-api")
}

