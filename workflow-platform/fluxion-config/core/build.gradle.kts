// fluxion-config:core - Configuration center capability domain core SPI module (zero Spring).
// Defines ConfigCenterProvider, ConfigSubscriber, and configuration data model for pluggable
// config backends (Apollo / Nacos / HTTP). Implemented by sibling backend modules.
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
    // Core base types: JsonUtil, shared context abstractions.
    implementation(project(":fluxion-core"))
    // Adapter SPI: Unified subscriber / change listener contracts that config backends extend.
    api(project(":fluxion-core"))

    // Jackson Databind for JSON serialization of configuration payloads.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Unified logging extensions.
    implementation(project(":fluxion-log"))
}
