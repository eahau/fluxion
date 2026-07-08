// fluxion-schema:json - JSON Schema validator implementation.
// Implements SchemaValidator SPI using networknt JSON Schema Validator library (draft-07, draft-2019-09).
// Exposes core module types via api for consumers that need both schema abstraction + JSON validation.
plugins {
    kotlin("jvm")
}

dependencies {
    // Core schema abstraction: Schema, SchemaValidator interfaces (api for transitive exposure).
    api(project(":fluxion-schema:core"))

    // Jackson serialization stack for JSON node tree traversal during schema document parsing.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // networknt JSON Schema Validator - actual validation engine.
    implementation("com.networknt:json-schema-validator")
    // Unified logging extensions (lazy lambda wrappers over SLF4J).
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
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
