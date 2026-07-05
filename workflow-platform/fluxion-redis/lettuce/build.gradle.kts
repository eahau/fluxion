dependencies {
    // workflow-redis-core（RedisClientAdapter SPI + RedisRawCommand）
    implementation(project(":fluxion-redis:core"))

    // Lettuce Core（Spring Boot BOM 管理版本，6.x）
    implementation("io.lettuce:lettuce-core")

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
