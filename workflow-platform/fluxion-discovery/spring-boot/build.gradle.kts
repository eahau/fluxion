plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java`
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(project(":fluxion-discovery:core"))
    implementation(project(":fluxion-registry:core"))
    implementation(project(":fluxion-core"))

    compileOnly("org.springframework.cloud:spring-cloud-commons")
    compileOnly("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery")

    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webmvc")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation(project(":fluxion-log"))
}