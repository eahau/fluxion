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
    // Adapter SPI: base subscriber / change event contracts.
    implementation(project(":fluxion-adapter-spi"))
    // Config core SPI: ConfigCenterProvider + data model interfaces.
    implementation(project(":fluxion-config:core"))

    // Jackson Databind for JSON configuration response parsing.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Unified logging extensions (lazy lambda wrappers over SLF4J).
    implementation(project(":fluxion-log"))
}
