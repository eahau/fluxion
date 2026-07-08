// fluxion-adapter-rpc:grpc - gRPC RPC protocol adapter implementation (zero Spring).
// Exports and consumes workflow functions via gRPC protocol (stub + protobuf serialization).
// Generates Java sources from .proto service definitions using Google Protobuf Gradle plugin.
// Key plugins: Kotlin JVM + Google Protobuf plugin (protobuf codegen + gRPC codegen).
import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm")
    id("com.google.protobuf")
}

dependencies {
    // Core base types.
    implementation(project(":fluxion-core"))
    // Adapter SPI (UnifiedRequest / WorkflowRouter contracts for RPC protocol adapters).
    implementation(project(":fluxion-adapter-spi"))
    // Schema core abstractions - for schema-based payload validation in gRPC messages.
    implementation(project(":fluxion-schema:core"))
    // Built-in functions: referenced by gRPC service method implementations for cross-function calls.
    implementation(project(":fluxion-function:builtin"))

    // gRPC Java runtime: stub layer, protobuf serialization, Netty shaded transport.
    implementation("io.grpc:grpc-stub")
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-netty-shaded")

    // Protobuf Java Util for JSON/Protobuf interop conversion.
    implementation("com.google.protobuf:protobuf-java-util")
    // JSR-250 annotation APIs (javax.annotation) required for generated proto message annotations.
    implementation("javax.annotation:javax.annotation-api")
    // Kotlin reflection + Jackson Kotlin module for dynamic gRPC payload handling.
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // SLF4J logging.
    implementation("org.slf4j:slf4j-api")
}

// ===== Protobuf Code Generation Configuration =====
// Configures protoc + gRPC Java plugin to generate message classes, service stubs, and server/client
// impl skeletons from .proto files under src/main/proto.
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.3" }
    plugins {
        id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.64.0" }
    }
    generateProtoTasks {
        all().forEach { it.plugins { id("grpc") } }
    }
}
