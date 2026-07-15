// fluxion-config:http - HTTP bootstrap configuration center backend implementation.
// Implements ConfigCenterProvider SPI via simple HTTP endpoint polling (GET + ETag / last-modified),
// suitable for environments without dedicated config center infrastructure.
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
    implementation(project(":fluxion-config:core"))
    implementation(project(":fluxion-registry:core"))
    implementation(project(":fluxion-discovery:core"))

    // Jackson Databind for JSON configuration response parsing.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Unified logging extensions (lazy lambda wrappers over SLF4J).
    implementation(project(":fluxion-log"))
}
