dependencies {
    // workflow-redis-core（RedisClientAdapter SPI + RedisRawCommand）
    implementation(project(":fluxion-redis:core"))

    // Redisson（根 build.gradle.kts 统一管理版本）
    implementation("org.redisson:redisson")

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
