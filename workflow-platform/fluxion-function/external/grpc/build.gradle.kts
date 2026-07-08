// fluxion-function:external:grpc - gRPC-based external function transport implementation.
// Implements ExternalFunctionTransport SPI using gRPC Java (stub + protobuf + netty-shaded).
// Generates Java sources from protobuf files via Google Protobuf Gradle plugin.
// Key plugins: Kotlin JVM + Google Protobuf plugin (protobuf codegen + gRPC plugin).
import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm")
    id("com.google.protobuf")
}

dependencies {
    // External function SPI: ExternalFunctionTransport abstraction + ServiceLoader registry.
    implementation(project(":fluxion-function:external"))

    // gRPC Java runtime (stub layer, protobuf serialization, Netty shaded transport, reflection services).
    implementation("io.grpc:grpc-stub")
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-netty-shaded")
    implementation("io.grpc:grpc-services")

    // Protobuf Java Util: JSON/Protobuf message conversion utilities.
    implementation("com.google.protobuf:protobuf-java-util")
    // Jackson + Kotlin module for JSON auxiliary message encoding outside protobuf.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Kotlin reflection for dynamic class mapping in generic transport layer.
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Unified logging.
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("javax.annotation:javax.annotation-api")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}

// ===== Protobuf Code Generation Configuration =====
// Configures protoc compiler + gRPC Java codegen plugin. Generates Java sources for .proto definitions
// placed under src/main/proto into build/generated/sources/proto/main/java.
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.3" }
    plugins {
        id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.64.0" }
    }
    generateProtoTasks {
        all().forEach { it.plugins { id("grpc") } }
    }
}

// ===== Re-enable JavaCompile After Root Convention Disables It =====
// Root subprojects convention disables JavaCompile tasks for pure-Kotlin modules (afterEvaluate hook).
// This module requires Java compilation because Protobuf + gRPC generate Java source files.
afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        enabled = true
    }
}
