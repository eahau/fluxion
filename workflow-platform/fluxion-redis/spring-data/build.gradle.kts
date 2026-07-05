plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // workflow-redis-core（RedisClientAdapter SPI + RedisRawCommand）
    api(project(":fluxion-redis:core"))

    // Spring Data Redis（由最终应用提供具体 starter，如 spring-boot-starter-data-redis）
    compileOnly("org.springframework.data:spring-data-redis")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    // SLF4J
    implementation("org.slf4j:slf4j-api")
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}
