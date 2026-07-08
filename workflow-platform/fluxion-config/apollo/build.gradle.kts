// fluxion-config:apollo - Ctrip Apollo configuration center backend implementation.
// Implements ConfigCenterProvider SPI wrapping Apollo Client SDK + OpenAPI admin client,
// enabling configuration read + write for both runtime consumption and admin workflows.
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
    // Unified logging extensions.
    implementation(project(":fluxion-log"))

    // Apollo Client SDK - runtime config subscription + change notification mechanism.
    implementation("com.ctrip.framework.apollo:apollo-client")
    // Apollo OpenAPI client - admin API for publishing config changes from fluxion-admin console.
    implementation("com.ctrip.framework.apollo:apollo-openapi")
    // Jackson Databind for JSON configuration body serialization.
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
