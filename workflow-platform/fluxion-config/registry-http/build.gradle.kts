// fluxion-config:registry-http - HTTP-based lightweight service registry implementation.
// Implements service discovery SPI through simple HTTP polling (no heavyweight registry
// infrastructure required). Suitable for bootstrap and single-machine deployments.
plugins {
    java
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    // Adapter SPI: base registry/discovery contracts this module implements.
    implementation(project(":fluxion-config:core"))

    // Jackson Databind for JSON serialization of registry payloads.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Unified logging extensions (lazy lambda wrappers over SLF4J).
    implementation(project(":fluxion-log"))
}
