// fluxion-schema:spring-boot - Spring Boot auto-configuration aggregate for schema subsystem.
// Wires up SchemaConfigSubscriber, SchemaChangeListener bridge (from adapter-spi), schema registry
// beans, and pulls in both core + JSON schema modules as api dependencies.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // Core schema abstraction + JSON schema validator are always auto-configured by default.
    api(project(":fluxion-schema:core"))
    api(project(":fluxion-schema:json"))

    // Adapter SPI: SchemaConfigSubscriber / SchemaChangeListener interfaces (compileOnly to avoid
    // forcing adapter-spi as required runtime when consumers don't use adapter features).
    compileOnly(project(":fluxion-config:core"))

    // Spring Boot AutoConfigure mechanism + metadata processor.
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    // Unified logging extensions (lazy lambda wrappers over SLF4J).
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
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
