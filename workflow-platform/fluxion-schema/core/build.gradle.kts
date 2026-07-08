// fluxion-schema:core - Schema abstraction layer core module.
// Defines Schema interface, SchemaValidator SPI, schema registry interfaces, JSON helpers,
// and shared Jackson JsonUtil serialization utilities. Supports pluggable backends (JSON Schema,
// Protobuf, Avro) implemented in sibling modules.
// Key plugins: Kotlin JVM (java-library applied by root subprojects conventions.
plugins {
    kotlin("jvm")
}

dependencies {
    // Jackson core + Kotlin module - JSON serialization stack for JSON handling in JsonUtil utilities.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // Unified logging extensions (lazy lambda wrappers over SLF4J).
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.assertj:assertj-core:3.25.3")
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
