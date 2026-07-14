// fluxion-outbound - External function transport SPI module.
// Defines ExternalFunctionTransport abstraction for remote function invocation across
// different protocol implementations (HTTP / gRPC / Dubbo) and ServiceLoader-based registry.
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    // Function core SPI (api for transitive exposure to transport impl submodules).
    api(project(":fluxion-function"))

    // Unified logging extensions.
    implementation(project(":fluxion-log"))

    // ===== Testing Dependencies =====
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
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
