// fluxion-schema:avro - Apache Avro schema validator implementation.
// Implements SchemaValidator SPI using Apache Avro library for both binary Avro data validation
// and schema compatibility checking between producer/consumer schema versions.
plugins {
    kotlin("jvm")
}

dependencies {
    // Core schema abstraction (api).
    api(project(":fluxion-schema:core"))

    // Apache Avro library: Schema parsing, GenericData validation, binary encoder/decoder support.
    implementation("org.apache.avro:avro")
    // Jackson: JSON representation of Avro schemas and core JsonUtil reuse.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // Unified logging extensions (lazy lambda wrappers over SLF4J).
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
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
