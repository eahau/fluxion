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

    api("org.springframework.cloud:spring-cloud-commons")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation(project(":fluxion-log"))
}