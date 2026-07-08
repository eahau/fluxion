// fluxion-config:nacos - Alibaba Nacos configuration center backend implementation.
// Implements ConfigCenterProvider SPI using Nacos Config Client SDK (non-Spring Cloud version),
// keeping runtime footprint minimal without Spring Cloud Commons abstractions.
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

    // Nacos Config Client SDK (non-Spring Cloud) - config subscription + listener support.
    implementation("com.alibaba.nacos:nacos-client")
    // Jackson Databind for JSON configuration body serialization.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Unified logging extensions (lazy lambda wrappers over SLF4J).
    implementation(project(":fluxion-log"))
}
