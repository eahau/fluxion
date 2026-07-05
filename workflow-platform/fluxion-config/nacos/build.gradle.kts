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

    // Nacos Config SDK（非 Spring Cloud Starter，保持轻量）
    implementation("com.alibaba.nacos:nacos-client")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.slf4j:slf4j-api")
}
