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
    implementation(project(":fluxion-config:core"))
    implementation(project(":fluxion-registry:core"))

    // Nacos Config Client SDK (non-Spring Cloud) - config subscription + listener support.
    implementation("com.alibaba.nacos:nacos-client")
    // Jackson Databind for JSON configuration body serialization.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Unified logging extensions (lazy lambda wrappers over SLF4J).
    implementation(project(":fluxion-log"))
}
