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
    implementation(project(":fluxion-config:core"))
    implementation(project(":fluxion-registry:core"))
    implementation(project(":fluxion-log"))

    // Apollo Client SDK - runtime config subscription + change notification mechanism.
    implementation("com.ctrip.framework.apollo:apollo-client")
    // Apollo OpenAPI client - admin API for publishing config changes from fluxion-admin console.
    implementation("com.ctrip.framework.apollo:apollo-openapi")
    // Jackson Databind for JSON configuration body serialization.
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
