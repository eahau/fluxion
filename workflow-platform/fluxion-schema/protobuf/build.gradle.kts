// fluxion-schema:protobuf - Protocol Buffers schema validator implementation.
// Implements SchemaValidator SPI using protobuf-java descriptor parsing.
// Supports both runtime schema validation against compiled .proto DescriptorProtos descriptors.
plugins {
    kotlin("jvm")
}

dependencies {
    // Core schema abstraction (api exposure).
    api(project(":fluxion-schema:core"))

    // protobuf-java: DescriptorProtos descriptor type definitions and schema parsing.
    implementation("com.google.protobuf:protobuf-java")
    implementation("com.google.protobuf:protobuf-java-util")
    // Unified logging extensions (lazy lambda wrappers over SLF4J).
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    // JSON schema module used for cross-format comparison tests.
    testImplementation(project(":fluxion-schema:json"))
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl")
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
