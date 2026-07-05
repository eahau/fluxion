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
    implementation(project(":fluxion-adapter-spi"))
    implementation(project(":fluxion-config:core"))

    // Apollo Client SDK
    implementation("com.ctrip.framework.apollo:apollo-client")
    implementation("com.ctrip.framework.apollo:apollo-openapi")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.slf4j:slf4j-api")
}
