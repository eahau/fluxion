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
    implementation(project(":fluxion-core"))
    api(project(":fluxion-core"))

    implementation(project(":fluxion-registry:core"))
    implementation(project(":fluxion-discovery:core"))

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation(project(":fluxion-log"))
}
